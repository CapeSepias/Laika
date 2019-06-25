/*
 * Copyright 2013-2016 the original author or authors.
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

package laika.rst.ext

import laika.ast._
import laika.bundle.{BlockParser, BlockParserBuilder}
import laika.parse.markup.BlockParsers._
import laika.parse.markup.RecursiveParsers
import laika.parse.text.TextParsers._
import laika.parse.{Failure, Parser, Success}
import laika.rst.BaseParsers._
import laika.rst.ast.{CustomizedTextRole, SubstitutionDefinition}
import laika.rst.bundle.RstExtension
import laika.rst.ext.Directives._
import laika.rst.ext.ExtensionParsers.Result
import laika.rst.ext.TextRoles._

import scala.collection.mutable.ListBuffer

/** Provides the parsers for all types of extensions (directives and text roles).
 *
 * @author Jens Halm
 */
class ExtensionParsers(recParsers: RecursiveParsers,
                       blockDirectives: Map[String, DirectivePart[Block]],
                       spanDirectives: Map[String, DirectivePart[Span]],
                       textRoles: Map[String, RoleDirectivePart[String => Span]],
                       defaultTextRole: String) {


  import recParsers._

  
  private val explicitStart = "." ~ (ws min 1)
  
  
  /** Parses all types of explicit block items.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#explicit-markup-blocks]].
   */
  lazy val explicitBlockItem: Parser[Block] = explicitStart ~> (substitutionDefinition | roleDirective | blockDirective)
  
  /** Parses a substitution definition.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#substitution-definitions]].
   */
  lazy val substitutionDefinition: Parser[Block] = {
    val text = not(ws take 1) ~> escapedText(delimitedBy('|','\n').keepDelimiter.nonEmpty)
    val prefix = '|' ~> text <~ not(lookBehind(1, ' ')) ~ '|'
    
    ((prefix <~ ws) ~ spanDirectiveParser) ^^ { 
      case name ~ InvalidDirective(msg, source, _) =>
        InvalidElement(msg, source.replaceFirst(".. ",s".. |$name| ")).asBlock
      case name ~ content => SubstitutionDefinition(name, content) 
    }
  }
  private lazy val spanDirectiveParser: Parser[Span] = directive(spanDirectives.get)
  
  private def replaceInvalidDirective (block: Block): Block = block match {
    case InvalidDirective(msg, source, _) => InvalidElement(msg, source).asBlock
    case other => other
  }
  
  /** Parses a block-level directive.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#directives]].
   */
  lazy val blockDirective: Parser[Block] = directive(blockDirectives.get) ^^ replaceInvalidDirective

  
  private def directive [E](provider: String => Option[DirectivePart[E]]): Parser[E] = {
    
    val nameParser = simpleRefName <~ "::" ~ ws
    
    def directiveParser [E] (directive: DirectivePart[E]): Parser[E] = {
      val parserBuilder = new DirectiveParserBuilder
      val result = directive(parserBuilder)
      parserBuilder.parser ^^^ {
        result.get
      }
    }
    
    nameParser >> { name => 
      directive(provider(name.toLowerCase).map(directiveParser).getOrElse(failure(s"unknown directive: $name")), s"$name::")
    }
  }
  
  private case class InvalidDirective (msg: String, source: String, options: Options = NoOpt) extends Block with Span {
    type Self = InvalidDirective
    def withOptions (options: Options): InvalidDirective = copy(options = options)
  }
  
  private def directive [E](p: Parser[E], name: String): Parser[E] = Parser { in =>
    p.parse(in) match {
      case s @ Success(_,_) => s
      case Failure(msg, next) => (indentedBlock() ^^ { block =>
        InvalidDirective(msg.message(next), s".. $name " + block).asInstanceOf[E]
      }).parse(in)
    }
  }

  /** Parses a role directive.
   * 
   *  See [[http://docutils.sourceforge.net/docs/ref/rst/directives.html#custom-interpreted-text-roles]].
   */
  lazy val roleDirective: Parser[Block] = {
    
    val nameParser = "role::" ~ ws ~> simpleRefName ~ opt('(' ~> simpleRefName <~ ')')
    
    def directiveParser (name: String)(role: RoleDirectivePart[String => Span]): Parser[Block] = {
      val delegate = new DirectiveParserBuilder
      val parserBuilder = new RoleDirectiveParserBuilder(delegate)
      val result = role(parserBuilder)
      delegate.parser ^^^ {
        CustomizedTextRole(name, result.get)
      }
    }
    
    nameParser >> { case name ~ baseName =>
      val base = baseName.getOrElse(defaultTextRole)
      val fullname = s"role::$name" + (baseName map ("("+_+")") getOrElse "")
      directive(textRoles.get(base.toLowerCase).map(directiveParser(name))
          .getOrElse(failure(s"unknown text role: $base")), fullname) ^^ replaceInvalidDirective
    }
    
  }
  
  private class DirectiveParserBuilder extends DirectiveParser {

    val skip: Parser[Any] = success(())
    var requiredArgs: Parser[Any] = skip
    var optionalArgs: Parser[Any] = skip
    var requiredArgWithWS: Parser[Any] = skip
    var optionalArgWithWS: Parser[Any] = skip
    var fields: Parser[Any] = skip
    var separator: Parser[Any] = skip
    var contentParser: Parser[Any] = skip
    
    def parser: Parser[Any] = requiredArgs ~ requiredArgWithWS ~ 
                              optionalArgs ~ optionalArgWithWS ~
                              fields ~ separator ~ contentParser
    
    def requiredArg (p: => Parser[String]): Parser[String] = p.withFailureMessage("missing required argument")                          
                              
    val arg: Parser[String] = requiredArg((anyBut(' ','\n') min 1) <~ ws)
    
    val argWithWS: Parser[String] = {
      val p = indentedBlock(linePredicate = not(":"), endsOnBlankLine = true) ^^? { block =>
        val text = block.trim
        if (text.nonEmpty) Right(text) else Left("missing required argument")
      }
      requiredArg(p)
    }
    
    val body: Parser[String] = lookBehind(1, '\n') ~> indentedBlock(firstLineIndented = true) | indentedBlock()
    
    // TODO - some duplicate logic with original fieldList parser
    lazy val directiveFieldList: Parser[Any] = {
      
      val name = ':' ~> escapedUntil(':') <~ (lookAhead(eol) | ' ')

      val item = (ws min 1) >> { firstIndent =>
          (name ~ indentedBlock(firstIndent.length + 1)) ^^
        { case name ~ block => 
            (name, block.trim)
        }}
      
      ((opt(wsEol) ~> (item +)) | success(Nil)) ^^? { fields =>
        
        val parsed = scala.collection.mutable.Map(fields:_*)
        val missing = scala.collection.mutable.Set.empty[String]
        val invalid = new ListBuffer[String]

        for ((name, f) <- requiredFields) {
          parsed.remove(name).map(res => f(res).swap map {invalid += name + ": " + _}).getOrElse(missing += name)
        }
        for ((name, f) <- optionalFields) {
          parsed.remove(name).map(res => f(res).swap map {invalid += name + ": " + _})
        }
        
        val errors = new ListBuffer[String]
        if (parsed.nonEmpty) errors += parsed.keys.mkString("unknown options: ",", ","")
        if (missing.nonEmpty) errors += missing.mkString("missing required options: ",", ","")
        if (invalid.nonEmpty) errors += invalid.mkString("invalid option values: ", ", ", "") 
        Either.cond(errors.isEmpty, (), errors mkString "; ")
      }
    }
    
    val contentSeparator: Parser[Any ~ String] = 
      ((lookBehind(1, '\n') | eol) ~ blankLine) | failure("blank line required to separate arguments and/or options from the body")
    
    val requiredFields: scala.collection.mutable.Map[String, String => Either[String,Unit]] = scala.collection.mutable.Map()
    val optionalFields: scala.collection.mutable.Map[String, String => Either[String,Unit]] = scala.collection.mutable.Map()
    
    class LazyResult[T] {
      var value: Option[T] = None
      def set (v: T): Unit = value = Some(v)
      def set (v: Option[T]): Unit = value = v
      def get: T = value get
    }
    
    def optionToEither [T](f: String => Either[String,T])(res: Option[String]): Either[String,Option[T]] =
      (res map f) map (_.map(res => Some(res))) getOrElse Right(None)
    
    def argument [T](convert: String => Either[String,T] = {s:String => Right(s)}, 
                     withWS: Boolean = false): Result[T] = {
      separator = contentSeparator
      val result = new LazyResult[T]
      if (withWS) requiredArgWithWS = argWithWS ^^? convert ^^ result.set
      else requiredArgs = requiredArgs ~ (arg ^^? convert ^^ result.set)
      new Result(result.get)
    }

    def spanArgument: Result[Seq[Span]] = {
      separator = contentSeparator
      val result = new LazyResult[Seq[Span]]
      requiredArgWithWS = recursiveSpans(argWithWS) ^^ result.set
      new Result(result.get)
    }
    
    def optArgument [T](convert: String => Either[String,T] = {s:String => Right(s)}, 
                        withWS: Boolean = false): Result[Option[T]] = {
      separator = contentSeparator
      val result = new LazyResult[T]
      if (withWS) optionalArgWithWS = (opt(argWithWS) ^^? optionToEither(convert) ^^ result.set)
      else optionalArgs = optionalArgs ~ (opt(arg) ^^? optionToEither(convert) ^^ result.set)
      new Result(result.value)
    }

    def optSpanArgument: Result[Option[Seq[Span]]] = {
      separator = contentSeparator
      val result = new LazyResult[Option[Seq[Span]]]
      requiredArgWithWS = opt(recursiveSpans(argWithWS)) ^^ result.set
      new Result(result.get)
    }
    
    def field [T](name: String, f: String => Either[String,T]): Result[T] = {
      separator = contentSeparator
      fields = directiveFieldList
      val result = new LazyResult[T]
      requiredFields += (name -> { s:String => f(s) map result.set })
      new Result(result.get)
    }
    
    def optField [T](name: String, f: String => Either[String,T]): Result[Option[T]] = {
      separator = if (separator ne contentSeparator) opt(contentSeparator) else contentSeparator
      fields = directiveFieldList
      val result = new LazyResult[T]
      optionalFields += (name -> { s:String => f(s) map result.set })
      new Result(result.value)
    }

    def blockContent: Result[Seq[Block]] = parseContentWithParser(recursiveBlocks)
    
    def spanContent: Result[Seq[Span]] = parseContentWithParser(recursiveSpans)

    def content [T](f: String => Either[String,T]): Result[T] = parseContentWith(f)
    
    private def parseContentWith [T](f: String => Either[String,T]): Result[T] = {
      val result = new LazyResult[T]
      contentParser = body ^^? f ^^ result.set
      new Result(result.get)
    }

    private def parseContentWithParser [T](f: Parser[String] => Parser[T]): Result[T] = {
      val result = new LazyResult[T]
      contentParser = f(body) ^^ result.set
      new Result(result.get)
    }
    
  }
  
  private class RoleDirectiveParserBuilder (delegate: DirectiveParser) extends RoleDirectiveParser {
    
    def field [T](name: String, f: String => Either[String,T]): Result[T] = delegate.field(name,f)
    
    def optField [T](name: String, f: String => Either[String,T]): Result[Option[T]] = delegate.optField(name,f)
    
    def blockContent: Result[Seq[Block]] = delegate.blockContent

    def spanContent: Result[Seq[Span]] = delegate.spanContent
    
    def content [T](f: String => Either[String,T]): Result[T] = delegate.content(f)
    
  }
    
  
}

/** Provides the parsers for all types of extensions (directives and text roles).
  *
  * @author Jens Halm
  */
object ExtensionParsers {

  /** A wrapper for a single result value.
    */
  class Result[+A] (a: => A) {
    def get: A = a
    def map [B](f: A => B): Result[B] = new Result(f(get))
  }

  /** Creates a new parser builder based on the specified extensions.
    */
  def allBlocks(blockDirectives: Seq[Directive[Block]],
                spanDirectives: Seq[Directive[Span]],
                textRoles: Seq[TextRole],
                defaultTextRole: String): BlockParserBuilder = BlockParser.forStartChar('.').recursive { recParsers =>
    new ExtensionParsers(recParsers,
      RstExtension.createAsMap(blockDirectives, recParsers),
      RstExtension.createAsMap(spanDirectives, recParsers),
      RstExtension.createAsMap(textRoles, recParsers),
      defaultTextRole).explicitBlockItem
  }
}
