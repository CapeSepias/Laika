/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package laika.parse.directive

import cats.implicits._
import cats.data.NonEmptySet
import laika.config.{Key, StringValue}
import laika.ast._
import laika.bundle.{BlockParser, BlockParserBuilder, SpanParser, SpanParserBuilder}
import laika.directive._
import laika.parse.{Failure, Message, Parser, ParserContext}
import laika.parse.hocon.{BuilderField, ConfigResolver, HoconParsers, InvalidBuilderValue, ObjectBuilderValue, ResolvedBuilderValue, SelfReference}
import laika.parse.markup.{EscapedTextParsers, RecursiveParsers, RecursiveSpanParsers}
import laika.parse.text.{CharGroup, PrefixedParser}
import laika.parse.api._
import laika.parse.implicits._

/** Parsers for all types of custom directives that can be used
 *  in templates or as inline or block elements in markup documents.
 *  
 *  @author Jens Halm
 */
object DirectiveParsers {
  
  case class DirectiveSpec(name: String, fence: String)
  
  type BodyParserBuilder = DirectiveSpec => Parser[Option[String]]
  

  /** Parses a legacy reference (version 0.1 to 0.11) enclosed between `{{` and `}}`.
    */
  def legacyReference[T] (f: String => T): PrefixedParser[T] = "{{" ~ ws ~> anyNot('}') <~ ws ~ "}}" ^^ f

  /** Parses a HOCON-style reference enclosed between `\${` and `}` that may be marked as optional (`\${?some.param}`).
    */
  def hoconReference[T] (f: (Key, Boolean) => T, e: InvalidElement => T): PrefixedParser[T] = 
    ("${" ~> opt("?") ~ HoconParsers.concatenatedKey(NonEmptySet.one('}')).withSource <~ "}").map {
      case opt ~ ((Right(key), _))  => f(key, opt.isEmpty)
      case _ ~ ((Left(invalid), src)) => e(InvalidElement(s"Invalid HOCON reference: '$src': ${invalid.failure.toString}", s"$${$src}"))
    }

  /** Represents one part of a directive (an attribute or a body element).
   */
  case class Part (key: AttributeKey, content: String)

  /** Represents the parsed but unprocessed content of a directive.
   */
  case class ParsedDirective (name: String, attributes: ObjectBuilderValue, body: Option[String])
  
  
  /** Parses horizontal whitespace or newline characters.
   */
  lazy val wsOrNl: Parser[String] = anyOf(' ','\t', '\n')

  /** Parses a name declaration that start with a letter and 
   *  continues with letters, numbers or the symbols '-' or '_'.
   */
  lazy val nameDecl: Parser[String] = (oneOf(CharGroup.alpha) ~ anyOf(CharGroup.alphaNum.add('-').add('_'))).source
  
  
  def legacyAttributeParser (escapedText: EscapedTextParsers): Parser[ObjectBuilderValue] = {
    lazy val attrName: Parser[String] = nameDecl <~ wsOrNl ~ "=" ~ wsOrNl

    lazy val attrValue: Parser[String] =
      "\"" ~> escapedText.escapedUntil('"') | (anyNot(' ','\t','\n','.',':') min 1)

    lazy val defaultAttribute: Parser[BuilderField] = not(attrName) ~> attrValue.map { v => 
      BuilderField(AttributeKey.Default.key, ResolvedBuilderValue(StringValue(v))) 
    }

    lazy val attribute: Parser[BuilderField] = attrName ~ attrValue ^^ { case name ~ value =>
      BuilderField(name, ResolvedBuilderValue(StringValue(value)))
    }

    (wsOrNl ~> opt(defaultAttribute <~ wsOrNl) ~ ((wsOrNl ~> attribute)*)) <~ ws ^^
      { case defAttr ~ attrs => ObjectBuilderValue(defAttr.toList ++ attrs) }

    ((wsOrNl ~> opt(defaultAttribute <~ wsOrNl).map(_.toList) ~ (wsOrNl ~> attribute).rep).concat <~ ws)
      .map(ObjectBuilderValue)
  }
  
  /** Parses a full directive declaration, containing all its attributes,
    *  but not the body elements.
    */
  def declarationParser (escapedText: EscapedTextParsers, supportsCustomFence: Boolean = false): Parser[(String, ObjectBuilderValue, String)] = {
    import HoconParsers.{wsOrNl => hoconWS, _}
    
    val defaultFence = success("@:@")
    val fence = if (supportsCustomFence) (ws ~> anyNot(' ', '\n', '\t').take(3)) | defaultFence else defaultFence
    val defaultAttribute = {
      val delim = (ws ~ ("," | eol)) | lookAhead(hoconWS ~ "}")
      opt((hoconWS ~> stringBuilderValue(/*Set(',','}','\n')*/ NonEmptySet.one('\u0001')) <~ delim ~ hoconWS)
        .map(sv => BuilderField(AttributeKey.Default.key, sv)))
    }
    val closingAttributes = "}".as(Option.empty[ParserContext]) | success(()).withContext.map { case (_, ctx) => Some(ctx) }
    val attributeSection = (ws ~> lazily("{" ~> defaultAttribute ~ objectMembers ~ closingAttributes)).map {
      case defAttr ~ obj ~ optCtx =>
        val attrs = obj.copy(values = defAttr.toSeq ++ obj.values)
        optCtx match {
          case Some(ctx) if ConfigResolver.extractErrors(obj).isEmpty =>
            ObjectBuilderValue(Seq(BuilderField("failure", InvalidBuilderValue(SelfReference, Failure(Message.fixed("Missing closing brace for attribute section"), ctx)))))
          case _ => 
            attrs
        }
    }
    
    ("@:" ~> nameDecl ~ opt(attributeSection) ~ fence).map { 
      case name ~ attrs ~ fencePattern => 
        (name, attrs.getOrElse(ObjectBuilderValue(Nil)), fencePattern) 
    }
  }
    
  /** Parses a full directive declaration, containing all its attributes,
   *  but not the body elements. This method parses the deprecated syntax where attributes
   *  are not enclosed in braces.
   */
  def legacyDeclarationParser (escapedText: EscapedTextParsers): Parser[(String, ObjectBuilderValue)] =
    ("@:" ~> nameDecl ~ legacyAttributeParser(escapedText)).map { case name ~ attrs => (name, attrs) }

  /** Parses one directive instance containing its name declaration,
   *  all attributes and all body elements.
   *  
   *  @param bodyContent the parser for the body content which is different for a block directive than for a span or template directive
   *  @param legacyBody the parser for the deprecated directive syntax that is still supported                   
   *  @param escapedText the parser for escape sequences according to the rules of the host markup language
   *  @param supportsCustomFence indicates whether the directive declaration allows the addition of a custom closing fence                   
   */
  def directiveParser (bodyContent: BodyParserBuilder, legacyBody: Parser[String], escapedText: EscapedTextParsers, 
                       supportsCustomFence: Boolean = false): Parser[ParsedDirective] = {

    val legacyDirective: Parser[(String, ObjectBuilderValue) ~ Option[String]] = {
      val noBody: Parser[Option[String]] = ".".as(None)
      val body: Parser[Option[String]] = ":" ~> legacyBody.map(Some(_))
      legacyDeclarationParser(escapedText) ~ (noBody | body)
    }
    
    val newDirective: Parser[(String, ObjectBuilderValue) ~ Option[String]] = {
      declarationParser(escapedText, supportsCustomFence) >> { case (name, attrs, fence) =>
        val body: Parser[Option[String]] = bodyContent(DirectiveSpec(name, fence))
        
        body.map(content => new ~((name, attrs), content))
      } 
    }
    
    (legacyDirective | newDirective).map { case (name, attrs) ~ body => ParsedDirective(name, attrs, body) }
  }
  
  val nestedBraces: PrefixedParser[Text] = ("{" ~> delimitedBy('}')).source.map(Text(_))

}

/** Provides the parser definitions for span directives in markup documents.
  */
object SpanDirectiveParsers {

  import DirectiveParsers._
  import laika.directive.Spans

  val contextRef: SpanParserBuilder =
    SpanParser.standalone(hoconReference(MarkupContextReference(_,_), _.asSpan))

  val legacyContextRef: SpanParserBuilder =
    SpanParser.standalone(legacyReference(key => MarkupContextReference(Key.parse(key), required = true)))

  def spanDirective (directives: Map[String, Spans.Directive]): SpanParserBuilder =
    SpanParser.recursive(rec => spanDirectiveParser(directives)(rec))

  def spanDirectiveParser(directives: Map[String, Spans.Directive])(recParsers: RecursiveSpanParsers): PrefixedParser[Span] = {

    import recParsers._
    
    val legacyBody = {
      val contextRef = legacyReference(key => MarkupContextReference(Key.parse(key), required = true))
      val spanParser = recursiveSpans(delimitedBy('}')).embed(contextRef).embed(nestedBraces)
      
      wsOrNl ~ "{" ~> spanParser.source.map(_.dropRight(1))
    }
    
    val separators = directives.values.flatMap(_.separators).toSet
    val newBody: BodyParserBuilder = spec => 
      if (directives.get(spec.name).exists(_.hasBody)) recursiveSpans(delimitedBy(spec.fence)).source.map { src => 
        Some(src.dropRight(spec.fence.length)) 
      } | success(None)
      else success(None)
    
    PrefixedParser('@') {
      withRecursiveSpanParser(directiveParser(newBody, legacyBody, recParsers).withSource).map {
        case (recParser, (result, source)) =>
          if (separators.contains(result.name)) Spans.SeparatorInstance(result, source)
          else Spans.DirectiveInstance(directives.get(result.name), result, recParser, source)
      }
    }
  }

}

/** Provides the parser definitions for block directives in markup documents.
  */
object BlockDirectiveParsers {

  import DirectiveParsers._
  import laika.directive.Blocks
  import laika.parse.markup.BlockParsers._

  def blockDirective (directives: Map[String, Blocks.Directive]): BlockParserBuilder =
    BlockParser.recursive(blockDirectiveParser(directives))

  def blockDirectiveParser (directives: Map[String, Blocks.Directive])(recParsers: RecursiveParsers): PrefixedParser[Block] = {

    import recParsers._

    val separators = directives.values.flatMap(_.separators).toSet
    val legacyBody = indentedBlock().evalMap { block =>
      val trimmed = block.trim
      Either.cond(trimmed.nonEmpty, trimmed, "empty body")
    }
    val noBody = wsEol.as(None)
    val newBody: BodyParserBuilder = spec =>
      if (directives.get(spec.name).exists(_.hasBody)) {
        val closingFenceP = spec.fence <~ wsEol
        wsEol ~> (not(closingFenceP | eof) ~> restOfLine).rep <~ closingFenceP ^^ { lines =>
          val trimmedLines = lines.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse
          Some(trimmedLines.mkString("\n"))
        }
      } | noBody
      else noBody

    PrefixedParser('@') {
      withRecursiveSpanParser(withRecursiveBlockParser(
        directiveParser(newBody, legacyBody, recParsers, supportsCustomFence = true).withSource
      )).map {
        case (recSpanParser, (recBlockParser, (result, source))) =>
          val trimmedSource = if (source.lastOption.contains('\n')) source.dropRight(1) else source
          if (separators.contains(result.name)) Blocks.SeparatorInstance(result, trimmedSource)
          else Blocks.DirectiveInstance(directives.get(result.name), result, recBlockParser, recSpanParser, trimmedSource)
      }
    }
  }

}
