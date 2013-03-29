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

package laika.parse.rst

import laika.tree.Elements._
import laika.util.Builders._
import laika.parse.rst.Elements.SubstitutionDefinition
import Directives._
import TextRoles._
import scala.collection.mutable.ListBuffer

/**
 * @author Jens Halm
 */
trait ExplicitBlockParsers extends BlockBaseParsers { self: InlineParsers =>

  
  
  val explicitStart = (".." ~ (ws min 1))
  
  
  def explicitBlockItem: Parser[Block] = explicitStart ~>
    (footnote | citation | linkDefinition | substitutionDefinition | roleDirective | blockDirective | comment) 
    // TODO - there is a linkDef alternative not requiring the .. prefix
  
  
  def footnote = {
    val prefix = '[' ~> footnoteLabel <~ ']' ~ ws
    
    prefix ~ varIndentedBlock() ^^ {
      case label ~ block => Footnote(label, parseNestedBlocks(block))
    }
  }
  
  def citation = {
    val prefix = '[' ~> simpleRefName <~ ']' ~ ws
    
    prefix ~ varIndentedBlock() ^^ {
      case label ~ block => Citation(label, parseNestedBlocks(block))
    }
  }
  
  def linkDefinition = {
    val named = '_' ~> refName <~ ':' // TODO - backticks are optional here in most cases (unless ref name contains colons)
    val anonymous = "__:" ^^^ ""
      
    val internal = named ^^ InternalLinkTarget // TODO - might need special logic for cases where it points to other targets (e.g. external)
    
    val externalName = (named | anonymous)
    
    val notEmpty = not(blankLine) | guard(restOfLine ~ (ws min 1) ~ not(blankLine))
    
    val external = externalName ~ (notEmpty ~> varIndentedBlock()) ^^ {
      case name ~ block => LinkDefinition(name, block.lines map (_.trim) filterNot (_.isEmpty) mkString)
    }
    
    external | internal
    
    // TODO - add indirect targets once inline parsers are completed
  }
  
  def substitutionDefinition = {
    val text = not(ws take 1) ~> (anyBut('|','\n') min 1)  
    val prefix = '|' ~> text <~ not(lookBehind(1, ' ')) ~ '|'
    
    ((prefix <~ ws) ~ spanDirective) ^^ { case name ~ content => SubstitutionDefinition(name, content) }
  }
  
  def comment = {
    varIndentedBlock() ^^ { block =>
      Comment(block.lines map (_.trim) filterNot (_.isEmpty) mkString "\n")
    }
  }
  
  
  val blockDirectives: Map[String, DirectivePart[Seq[Block]]] // TODO - populate (maybe do this in reStructuredText entry point object)
  
  val spanDirectives: Map[String, DirectivePart[Seq[Span]]]
  
  val textRoles: Map[String, TextRole]
    

  def blockDirective: Parser[Block] = directive(blockDirectives) ^^ BlockSequence

  def spanDirective = directive(spanDirectives)
  
  protected def directive [E](directives: Map[String, DirectivePart[Seq[E]]]) = {
    
    val nameParser = simpleRefName <~ "::" ~ ws
    
    def directiveParser [E] (directive: DirectivePart[Seq[E]]) = {
      val parserBuilder = new DirectiveParserBuilder
      val result = directive(parserBuilder)
      parserBuilder.parser ^^^ {
        result.get
      }
    }
    
    nameParser >> { name =>
      directives.get(name).map(directiveParser).getOrElse(failure("unknown directive: " + name))
    }
    
  }

  case class CustomizedTextRole (name: String, apply: String => Seq[Span]) extends Block
  
  def roleDirective = {
    
    val nameParser = "role::" ~ ws ~> simpleRefName ~ opt('(' ~> simpleRefName <~ ')')
    
    def directiveParser (name: String)(role: TextRole) = {
      val delegate = new DirectiveParserBuilder
      val parserBuilder = new RoleDirectiveParserBuilder(delegate)
      val result = role.part(parserBuilder)
      delegate.parser ^^^ {
        CustomizedTextRole(name, result.get)
      }
    }
    
    nameParser >> { case name ~ baseName =>
      val base = baseName.getOrElse(defaultTextRole)
      textRoles.get(base).map(directiveParser(name)).getOrElse(failure("unknown text role: " + base))
    }
    
  }
  
  // TODO - deal with failures and exact behaviour for unknown directives and other types of error
  class DirectiveParserBuilder extends DirectiveParser {

    val skip = success(())
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
    
    val arg = (anyBut(' ','\n') min 1) <~ ws
    
    val argWithWS = {
      val argLineStart = not(blankLine | ':')
      val nextBlock = failure("args do not expand beyond blank lines")
      argLineStart ~> varIndentedBlock(1, argLineStart, nextBlock) ^^ { block =>
        block.lines mkString "\n"
      }
    }
    
    val body = varIndentedBlock()
    
    // TODO - some duplicate logic with original fieldList parser
    lazy val directiveFieldList: Parser[Any] = {
      
      val name = eol ~ ':' ~> anyBut(':') <~ ':' // TODO - must be escapedUntil(':') once InlineParsers are implemented
      
      val firstLine = restOfLine 
      
      val item = (ws min 1) >> { firstIndent =>
          (name ~ firstLine ~ opt(varIndentedBlock(firstIndent.length + 1))) ^^ 
        { case name ~ firstLine ~ Some(block) => 
            (name, (firstLine :: block.lines) mkString "\n")
          case name ~ firstLine ~ None => 
            (name, firstLine) }}
      
      (item *) ^^? { fields =>
        val parsed = scala.collection.mutable.Map(fields.toArray:_*)
        val missing = scala.collection.mutable.Set.empty[String]
        
        for ((name, f) <- requiredFields) {
          parsed.remove(name).map(f).getOrElse(missing += name)
        }
        for ((name, f) <- optionalFields) {
          parsed.remove(name).foreach(f)
        }
        
        val errors = new ListBuffer[String]
        if (parsed.nonEmpty) errors += parsed.mkString("unknown options: ",", ","")
        if (missing.nonEmpty) errors += parsed.mkString("missing required options: ",", ","")
        Either.cond(errors.isEmpty, (), errors mkString "; ")
      }
    }
    
    val contentSeparator = (lookBehind(1, '\n') | eol) ~ blankLine
    
    val requiredFields: Map[String, String => Unit] = Map.empty
    val optionalFields: Map[String, String => Unit] = Map.empty
    
    class LazyResult[T] {
      var value: Option[T] = None
      def set (v: T) = value = Some(v)
      def get: T = value getOrElse { throw new IllegalStateException("result not set yet") }
      def fromString (f: String => T)(s: String) = set(f(s))
    }
    
    def requiredArg [T](f: String => T): Result[T] = {
      separator = contentSeparator
      val result = new LazyResult[T]
      requiredArgs = requiredArgs ~ (arg ^^ result.fromString(f))
      new Result(result.get)
    }
    
    def requiredArgWithWS [T](f: String => T): Result[T] = {
      separator = contentSeparator
      val result = new LazyResult[T]
      requiredArgWithWS = (argWithWS ^^ result.fromString(f))
      new Result(result.get)
    }
    
    def optionalArg [T](f: String => T): Result[Option[T]] = {
      separator = contentSeparator
      val result = new LazyResult[T]
      optionalArgs = optionalArgs ~ (arg ^^ result.fromString(f))
      new Result(result.value)
    }
    
    def optionalArgWithWS [T](f: String => T): Result[T] = {
      separator = contentSeparator
      val result = new LazyResult[T]
      optionalArgWithWS = (argWithWS ^^ result.fromString(f))
      new Result(result.get)
    }
    
    def requiredField [T](name: String, f: String => T): Result[T] = {
      separator = contentSeparator
      fields = directiveFieldList
      val result = new LazyResult[T]
      requiredFields + (name -> result.fromString(f)_)
      new Result(result.get)
    }
    
    def optionalField [T](name: String, f: String => T): Result[Option[T]] = {
      separator = contentSeparator
      fields = directiveFieldList
      val result = new LazyResult[T]
      optionalFields + (name -> result.fromString(f)_)
      new Result(result.value)
    }
    
    def standardContent: Result[Seq[Block]] = parseContentWith {
      block => parseNestedBlocks(block)
    }
    
    def content [T](f: String => T): Result[T] = parseContentWith {
      block => f(block.lines mkString "\n")
    }
    
    private def parseContentWith [T](f: IndentedBlock => T): Result[T] = {
      val result = new LazyResult[T]
      contentParser = (body ^^ { block => result.set(f(block)) })
      new Result(result.get)
    }
    
  }
  
  class RoleDirectiveParserBuilder (delegate: DirectiveParser) extends RoleDirectiveParser {
    
    def requiredField [T](name: String, f: String => T): Result[T] = delegate.requiredField(name,f)
    
    def optionalField [T](name: String, f: String => T): Result[Option[T]] = delegate.optionalField(name,f)
    
    def standardContent: Result[Seq[Block]] = delegate.standardContent
    
    def content [T](f: String => T): Result[T] = delegate.content(f)
    
  }
    
  
}