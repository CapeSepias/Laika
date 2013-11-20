/*
 * Copyright 2013 the original author or authors.
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

package laika.directive

import Directives._
import laika.tree.Elements._
import laika.util.Builders._
import scala.collection.mutable.ListBuffer
import laika.template.TemplateParsers
import laika.tree.Documents.DocumentContext
import laika.tree.Templates._

/** Parsers for all types of custom directives that can be used
 *  in templates or as inline or block elements in markup documents.
 *  
 *  @author Jens Halm
 */
trait DirectiveParsers extends laika.parse.InlineParsers { 
  
  
  /** Groups the result of the parser and the source string
   *  that it successfully parsed into a tupled result. 
   */
  def withSource[T] (p: Parser[T]): Parser[(T, String)] = Parser { in =>
    p(in) match {
      case Success(result, next) => Success((result, next.source.subSequence(in.offset, next.offset).toString), next)
      case ns: NoSuccess         => ns
    }
  }
  

  /** Represents one part of a directive (an attribute or a body element).
   */
  case class Part (key: Key, content: String)

  /** Represents the parsed but unprocessed content of a directive.
   */
  case class ParsedDirective (name: String, parts: List[Part])
  
  type PartMap = Map[Key, String]
  
  
  /** Parses horizontal whitespace or newline characters.
   */
  lazy val wsOrNl = anyOf(' ','\t', '\n')

  /** Parses a name declaration that start with a letter and 
   *  continues with letters, numbers or the symbols '-' or '_'.
   */
  lazy val nameDecl: Parser[String] = (anyIn('a' to 'z', 'A' to 'Z') take 1) ~ 
    anyIn('a' to 'z', 'A' to 'Z', '0' to '9', '-', '_') ^^ { case first ~ rest => first + rest }
  
  /** Parses a full directive declaration, containing all its attributes,
   *  but not the body elements.
   */
  lazy val declaration: Parser[(String, List[Part])] = {
    
    lazy val attrName: Parser[String] = nameDecl <~ wsOrNl ~ '=' ~ wsOrNl
  
    lazy val attrValue: Parser[String] = {
      def escapedUntil (char: Char) = escapedText(anyUntil(char) min 1)
      def escapedText (p: TextParser) = text(p, Map('\\' -> (any take 1))) // TODO - should be promoted to generic inline parser (after which this trait only needs to extend MarkupParsers)
      
      '"' ~> escapedUntil('"') | (anyBut(' ','\t','\n','.',':') min 1)  
    }
  
    lazy val defaultAttribute: Parser[Part] = not(attrName) ~> attrValue ^^ { Part(Attribute(Default), _) }
  
    lazy val attribute: Parser[Part] = attrName ~ attrValue ^^ { case name ~ value => Part(Attribute(name), value) }
 
    (":" ~> nameDecl <~ wsOrNl) ~ opt(defaultAttribute <~ wsOrNl) ~ ((wsOrNl ~> attribute)*) <~ ws ^^ 
    { case name ~ defAttr ~ attrs => (name, defAttr.toList ::: attrs) }
 }
      

  private lazy val bodyName: Parser[String] = '~' ~> nameDecl <~ ws ~ ':'
  
  private lazy val noBody = '.' ^^^ List[Part]()
  
  /** Parses one directive instance containing its name declaration,
   *  all attributes and all body elements.
   *  
   *  @param bodyContent the parser for the body content which is different for a block directive than for a span or template directive
   *  @param includeStartChar indicates whether the starting '@' has to be parsed by this parser
   */
  def directiveParser (bodyContent: Parser[String], includeStartChar: Boolean): Parser[ParsedDirective] = {
    
    val defaultBody: Parser[Part] = not(wsOrNl ~> bodyName) ~> bodyContent ^^ { Part(Body(Default),_) }
    
    val body: Parser[Part] = wsOrNl ~> bodyName ~ bodyContent ^^ { case name ~ content => Part(Body(name), content) }
    
    val bodies = ':' ~> (defaultBody | body) ~ (body*) ^^ { case first ~ rest => first :: rest }
    
    val decl = if (includeStartChar) "@" ~> declaration else declaration
    decl ~ (noBody | bodies) ^^ { case (name, attrs) ~ bodies => ParsedDirective(name, attrs ::: bodies) }
  }
  
  abstract class DirectiveContextBase (parts: PartMap, docContext: Option[DocumentContext] = None) {
    
    def part (key: Key): Option[String] = parts.get(key)
      
    val context = docContext
    
  }
  
  protected def applyDirective [E <: Element] (builder: BuilderContext[E]) (
      parseResult: ParsedDirective, 
      directives: String => Option[builder.Directive], 
      createContext: (PartMap, Option[DocumentContext]) => builder.DirectiveContext,
      createPlaceholder: (DocumentContext => E) => E, 
      createInvalidElement: String => E,
      directiveTypeDesc: String
    ): E = {
    
    import laika.util.Builders.{~ => ~~}
    
    val directive = directives(parseResult.name).map(Directives.Success(_))
        .getOrElse(Directives.Failure("No "+directiveTypeDesc+" directive registered with name: "+parseResult.name))
    
    val partMap = {
      val dups = parseResult.parts groupBy (_.key) filterNot (_._2.tail.isEmpty) keySet;
      if (dups.isEmpty) Directives.Success(parseResult.parts map (p => (p.key, p.content)) toMap)
      else Directives.Failure(dups.map("Duplicate "+_.desc).toList)
    }
    
    def processResult (result: Directives.Result[E]) = result match {
      case Directives.Success(result)   => result
      case Directives.Failure(messages) => createInvalidElement("One or more errors processing directive: " + messages.mkString(", "))
    }
    
    processResult((directive ~ partMap) flatMap { case directive ~~ partMap =>
      def directiveWithContext (context: Option[DocumentContext]) = directive(createContext(partMap, context))
      if (directive.requiresContext) Directives.Success(createPlaceholder(c => processResult(directiveWithContext(Some(c)))))
      else directiveWithContext(None)
    }) 
  }
  
}

/** Provides the concrete sub traits for the three different directive types.
 */
object DirectiveParsers {
  

  /** Provides the parsers for directives in templates.
   */
  trait TemplateDirectives extends DirectiveParsers { self: TemplateParsers.Templates =>
    
    def getTemplateDirective (name: String): Option[Templates.Directive]
    
    case class DirectiveSpan (f: DocumentContext => Span, options: Options = NoOpt) extends SpanResolver with TemplateSpan {
      def resolve (context: DocumentContext) = f(context) match {
        case s: TemplateSpan => s
        case s: Span => TemplateElement(s)
      }
    }
    
    lazy val templateDirectiveParser: Parser[TemplateSpan] = {
      val bodyContent = wsOrNl ~ '{' ~> (withSource(spans(anyUntil('}'), spanParsers)) ^^ (_._2.dropRight(1)))
      withSource(directiveParser(bodyContent, false)) ^^ { case (result, source) =>
        
        def createContext (parts: PartMap, docContext: Option[DocumentContext]): Templates.DirectiveContext = {
          new DirectiveContextBase(parts, docContext) with Templates.DirectiveContext {
            val parser = new Templates.Parser {
              def apply (source: String) = parseTemplatePart(source)
            }
          }
        }
        def invalid (msg: String) = TemplateElement(InvalidSpan(SystemMessage(laika.tree.Elements.Error, msg), Literal("@"+source)))
        
        applyDirective(Templates)(result, getTemplateDirective, createContext, s => DirectiveSpan(s), invalid, "template")
      }
    }
    
  }
  
  
  /** Provides the parsers for directives in inline elements of markup documents.
   */
  trait SpanDirectives extends DirectiveParsers {
    
    def getSpanDirective (name: String): Option[Spans.Directive]
    
    case class DirectiveSpan (f: DocumentContext => Span, options: Options = NoOpt) extends SpanResolver {
      def resolve (context: DocumentContext) = f(context)
    }
    
    lazy val spanDirectiveParser: Parser[Span] = {
      val bodyContent = wsOrNl ~ '{' ~> (withSource(spans(anyUntil('}'), spanParsers)) ^^ (_._2.dropRight(1)))
      withSource(directiveParser(bodyContent, false)) ^^ { case (result, source) => // TODO - optimization - parsed spans might be cached for DirectiveContext (applies for the template parser, too)
        
        def createContext (parts: PartMap, docContext: Option[DocumentContext]): Spans.DirectiveContext = {
          new DirectiveContextBase(parts, docContext) with Spans.DirectiveContext {
            val parser = new Spans.Parser {
              def apply (source: String) = parseInline(source)
            }
          }
        }
        def invalid (msg: String) = InvalidSpan(SystemMessage(laika.tree.Elements.Error, msg), Literal("@"+source))
        
        applyDirective(Spans)(result, getSpanDirective, createContext, DirectiveSpan(_), invalid, "span")
      }
    }
    
  }
  
  
  /** Provides the parsers for directives in block elements of markup documents.
   */
  trait BlockDirectives extends DirectiveParsers with laika.parse.BlockParsers {
    
    def getBlockDirective (name: String): Option[Blocks.Directive]
    
    case class DirectiveBlock (f: DocumentContext => Block, options: Options = NoOpt) extends BlockResolver {
      def resolve (context: DocumentContext) = f(context)
    }
    
    lazy val blockDirectiveParser: Parser[Block] = {
      val bodyContent = indentedBlock() ^^? { block => 
        val trimmed = (block.lines mkString "\n").trim
        Either.cond(trimmed.nonEmpty, trimmed, "empty body")
      }
      withSource(directiveParser(bodyContent, true)) ^^ { case (result, source) =>
        
        def createContext (parts: PartMap, docContext: Option[DocumentContext]): Blocks.DirectiveContext = {
          new DirectiveContextBase(parts, docContext) with Blocks.DirectiveContext {
            val parser = new Blocks.Parser {
              def apply (source: String): Seq[Block] = parseNestedBlocks(source, 0) // TODO - pass nest level
              def parseInline (source: String): Seq[Span] = parseInline(source)
            }
          }
        }
        def invalid (msg: String) = InvalidBlock(SystemMessage(laika.tree.Elements.Error, msg), LiteralBlock(source))
        
        applyDirective(Blocks)(result, getBlockDirective, createContext, DirectiveBlock(_), invalid, "block")
      }
    }
    
  }
  
  
}