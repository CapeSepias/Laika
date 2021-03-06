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
import laika.config.{ArrayValue, Key, StringValue}
import laika.ast._
import laika.bundle.{BlockParser, BlockParserBuilder, SpanParser, SpanParserBuilder}
import laika.directive._
import laika.parse.{Failure, Message, Parser, ParserContext, Success}
import laika.parse.hocon.{ArrayBuilderValue, BuilderField, ConfigResolver, HoconParsers, InvalidBuilderValue, ObjectBuilderValue, ResolvedBuilderValue, SelfReference, ValidStringValue}
import laika.parse.markup.{EscapedTextParsers, RecursiveParsers, RecursiveSpanParsers}
import laika.parse.text.{CharGroup, PrefixedParser}
import laika.parse.builders._
import laika.parse.implicits._

/** Parsers for all types of custom directives that can be used
 *  in templates or as inline or block elements in markup documents.
 *  
 *  @author Jens Halm
 */
object DirectiveParsers {
  
  case class DirectiveSpec(name: String, fence: String)
  
  type BodyParserBuilder = DirectiveSpec => Parser[Option[String]]
  

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
  
  
  /** Parses a full directive declaration, containing all its attributes,
    * but not the body elements.
    */
  def declarationParser (escapedText: EscapedTextParsers, supportsCustomFence: Boolean = false): Parser[(String, ObjectBuilderValue, String)] = {
    import HoconParsers._
    
    val defaultFence = success("@:@")
    val fence = if (supportsCustomFence) (ws ~> anyNot(' ', '\n', '\t').take(3)) | defaultFence else defaultFence
    
    val quotedAttribute = (ws ~ "\"") ~> text(delimitedBy('"')).embed("\\" ~> oneChar) <~ ws
    val unquotedAttribute = text(delimitedBy(',', ')').keepDelimiter).embed("\\" ~> oneChar) <~ ws
    val positionalAttributes = opt(ws ~> "(" ~> (quotedAttribute | unquotedAttribute).rep(",")
      .map(values => BuilderField(AttributeKey.Positional.key, ArrayBuilderValue(values.map(sv => ValidStringValue(sv.trim))))) <~ ")")
    
    val closingAttributes = literal("}").as(Option.empty[ParserContext]) | 
                            success(()).withContext.map { case (_, ctx) => Some(ctx) }
    
    val hoconAttributes = opt(ws ~> lazily("{" ~> objectMembers ~ closingAttributes))
    
    val attributeSection = (positionalAttributes ~ hoconAttributes).map {
      case posAttrs ~ Some(obj ~ optCtx) =>
        val positional = posAttrs.toSeq
        val attrs = obj.copy(values = positional ++ obj.values)
        optCtx match {
          case Some(ctx) if ConfigResolver.extractErrors(obj).isEmpty =>
            val fail = Failure(Message.fixed("Missing closing brace for attribute section"), ctx)
            ObjectBuilderValue(Seq(BuilderField("failure", InvalidBuilderValue(SelfReference, fail))))
          case _ => 
            attrs
        }
      case posAttrs ~ None => ObjectBuilderValue(posAttrs.toSeq)
    }
    
    ("@:" ~> nameDecl ~ attributeSection ~ fence).map { 
      case name ~ attrs ~ fencePattern => (name, attrs, fencePattern) 
    }
  }
    
  /** Parses one directive instance containing its name declaration,
   *  all attributes and all body elements.
   *  
   *  @param bodyContent the parser for the body content which is different for a block directive than for a span or template directive
   *  @param escapedText the parser for escape sequences according to the rules of the host markup language
   *  @param supportsCustomFence indicates whether the directive declaration allows the addition of a custom closing fence                   
   */
  def directiveParser (bodyContent: BodyParserBuilder, escapedText: EscapedTextParsers, 
                       supportsCustomFence: Boolean = false): Parser[ParsedDirective] = 
    declarationParser(escapedText, supportsCustomFence) >> { case (name, attrs, fence) =>
      bodyContent(DirectiveSpec(name, fence)).map(content => ParsedDirective(name, attrs, content))
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

  def spanDirective (directives: Map[String, Spans.Directive]): SpanParserBuilder =
    SpanParser.recursive(rec => spanDirectiveParser(directives)(rec))

  def spanDirectiveParser(directives: Map[String, Spans.Directive])(recParsers: RecursiveSpanParsers): PrefixedParser[Span] = {

    import recParsers._
    
    val separators = directives.values.flatMap(_.separators).toSet
    val body: BodyParserBuilder = spec => 
      if (directives.get(spec.name).exists(_.hasBody)) recursiveSpans(delimitedBy(spec.fence)).source.map { src => 
        Some(src.dropRight(spec.fence.length)) 
      } | success(None)
      else success(None)
    
    PrefixedParser('@') {
      withRecursiveSpanParser(directiveParser(body, recParsers).withSource).map {
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
    val noBody = wsEol.as(None)
    val body: BodyParserBuilder = spec =>
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
        directiveParser(body, recParsers, supportsCustomFence = true).withSource
      )).map {
        case (recSpanParser, (recBlockParser, (result, source))) =>
          val trimmedSource = if (source.lastOption.contains('\n')) source.dropRight(1) else source
          if (separators.contains(result.name)) Blocks.SeparatorInstance(result, trimmedSource)
          else Blocks.DirectiveInstance(directives.get(result.name), result, recBlockParser, recSpanParser, trimmedSource)
      }
    }
  }

}
