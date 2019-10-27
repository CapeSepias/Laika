/*
 * Copyright 2012-2019 the original author or authors.
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

package laika.parse.hocon

import laika.ast.{Path, ~}
import laika.config._
import laika.parse.text.Characters
import laika.parse.text.TextParsers._
import laika.parse.{Parser, ParserContext}

import scala.util.Try

/** The parser implementation for the HOCON format.
  * 
  * It currently supports the full spec as documented in
  * [[https://github.com/lightbend/config/blob/master/HOCON.md]]
  * except for file includes which might be added in a later release.
  * 
  * @author Jens Halm
  */
object HoconParsers {

  // TODO - promote to core parser
  implicit class String2ParserOps (val p: Parser[String ~ String]) extends AnyVal {
    def concat: Parser[String] = p.map { case a ~ b => a + b }
  }
  implicit class String3ParserOps (val p: Parser[String ~ String ~ String]) extends AnyVal {
    def concat: Parser[String] = p.map { case a ~ b ~ c => a + b + c }
  }
  implicit class PrependParserOps[T] (val p: Parser[T ~ Seq[T]]) extends AnyVal {
    def concat: Parser[Seq[T]] = p.map { case x ~ xs => x +: xs }
  }
  
  case class PathFragments(fragments: Seq[String]) {
    def join(other: PathFragments): PathFragments = {
      if (fragments.isEmpty || other.fragments.isEmpty) PathFragments(fragments ++ other.fragments)
      else PathFragments(fragments.init ++ Seq(fragments.last + other.fragments.head) ++ other.fragments.tail)
    }
  }
  object PathFragments {
    def unquoted(key: StringValue): PathFragments = apply(key.value.split("\\.", -1))
    def quoted(key: StringValue): PathFragments = apply(Seq(key.value))
    def whitespace(ws: String): PathFragments = apply(Seq(ws))
  }
  
  def lazily[T](parser: => Parser[T]): Parser[T] = new Parser[T] {
    lazy val p = parser
    override def parse (in: ParserContext) = p.parse(in)
  }
  
  /** Parses whitespace or newline characters. */
  val wsOrNl: Characters[String] = anyOf(' ','\t','\n')

  /** Parses a null value. */
  val nullValue: Parser[ConfigBuilderValue] = "null" ^^^ ResolvedBuilderValue(NullValue)
  /** Parses a literal true value. */
  val trueValue: Parser[ConfigBuilderValue] = "true" ^^^ ResolvedBuilderValue(BooleanValue(true))
  /** Parses a literal false value. */
  val falseValue: Parser[ConfigBuilderValue] = "false" ^^^ ResolvedBuilderValue(BooleanValue(false))

  /** Parses a literal number value into a Long or Double depending on whether a fraction part is present. */
  val numberValue: Parser[ConfigBuilderValue] = {
    
    val zero = anyIn('0').take(1)
    val digits = anyIn('0' to '9')
    val oneToNine = anyIn('1' to '9')
    val nonZero = (oneToNine.take(1) ~ digits).concat
    val negativeSign = opt('-').map(_.fold("")(_.toString))
    val sign = opt(char('-') | char('+')).map(_.fold("")(_.toString))
    
    val integer = (negativeSign ~ (zero | nonZero)).concat
    val fraction = opt((anyIn('.').take(1) ~ digits).concat).map(_.getOrElse(""))
    val exponent = opt((anyIn('E','e').take(1) ~ sign ~ digits).concat).map(_.getOrElse(""))

    (integer ~ (fraction ~ exponent).concat) ^^? {
      case int ~ ""         => 
        Try(int.toLong).toEither
          .left.map(_.getMessage)
          .map(v => ResolvedBuilderValue(LongValue(v)))
      case int ~ doublePart => 
        Try((int + doublePart).toDouble).toEither
          .left.map(_.getMessage)
          .map(v => ResolvedBuilderValue(DoubleValue(v)))
    }
  }

  /** Parses a string enclosed in quotes. */
  val quotedString: Parser[StringValue] = {
    val chars = anyBut('"','\\').min(1)
    val specialChar = anyIn('b','f','n','r','t').take(1).map {
      case "b" => "\b"
      case "f" => "\f"
      case "n" => "\n"
      case "r" => "\r"
      case "t" => "\t"
    }
    val literalChar = anyIn('"','\\','/').take(1)
    val unicode = anyIn('0' to '9', 'a' to 'f', 'A' to 'F').take(4).map(Integer.parseInt(_, 16).toChar.toString)
    val escape = '\\' ~> (literalChar | specialChar | unicode)
    
    val value = (chars | escape).rep.map(parts => StringValue(parts.mkString))
    '"' ~> value <~ '"'
  }

  /** Parses a string enclosed in triple quotes. */
  val multilineString: Parser[StringValue] = {
    "\"\"\"" ~> delimitedBy("\"\"\"").map(StringValue)
  }

  /** Parses an unquoted string that is not allowed to contain any of the reserved characters listed in the HOCON spec. */
  val unquotedString: Parser[StringValue] = {
    val unquotedChar = anyBut('$', '"', '{', '}', '[', ']', ':', '=', ',', '+', '#', '`', '^', '?', '!', '@', '*', '&', '\\', ' ','\t','\n').min(1)
    unquotedChar.map(StringValue)
  }

  /** Parses any of the 3 string types (quoted, unquoted, triple-quoted). */
  val stringBuilderValue: Parser[ConfigBuilderValue] =
    (multilineString | quotedString | unquotedString).map(ResolvedBuilderValue)
  
  lazy val concatenatedValue: Parser[ConfigBuilderValue] = {
    lazy val parts = (ws ~ (not(comment) ~> anyValue)).map { case s ~ v => ConcatPart(s,v) }.rep
    lazily {
      (anyValue ~ parts).map {
        case first ~ Nil => first
        case first ~ rest => ConcatValue(first, rest)
      }
    }
  }

  /** Parses a key based on the HOCON rules where a '.' in a quoted string is not interpreted as a path separator. */
  val concatenatedKey: Parser[Path] = {
    val string = quotedString.map(PathFragments.quoted) | unquotedString.map(PathFragments.unquoted)
    val parts = (ws.map(PathFragments.whitespace) ~ string).map { case s ~ fr => s.join(fr) }
    (string ~ parts.rep).map {
      case first ~ rest => Path((first +: rest).reduce(_ join _).fragments.toList)
    }
  }

  /** Parses a substitution variable. */
  val substitutionValue: Parser[SubstitutionValue] = {
    ("${" ~> opt('?') ~ concatenatedKey <~ '}').map {
      case opt ~ key => SubstitutionValue(key, opt.isDefined)
    } 
  }

  /** Parses a comment. */
  val comment: Parser[String] = ("//" | "#") ~> restOfLine

  /** Parses the rest of the current line if it contains either just whitespace or a comment. */
  val wsOrComment: Parser[Any] = wsOrNl ~ (comment | wsOrNl.min(1)).rep
  
  private val separator: Parser[Any] = (char(',') | eol | comment) ~ wsOrComment
  
  private val trailingComma: Parser[Any] = opt(',' ~ wsOrComment)

  /** Parses an array value recursively. */
  lazy val arrayValue: Parser[ConfigBuilderValue] = {
    lazy val value = wsOrNl ~> concatenatedValue <~ ws
    lazy val values = wsOrComment ~> opt(value ~ (separator ~> value).rep).map(_.fold(Seq.empty[ConfigBuilderValue]){ case v ~ vs => v +: vs }) <~ wsOrComment
    lazily(('[' ~> values <~ trailingComma <~ ']').map(ArrayBuilderValue))
  }

  /** Parses the members of an object without the enclosing braces. */
  private[laika] lazy val objectMembers: Parser[ObjectBuilderValue] = {
    lazy val key = wsOrNl ~> concatenatedKey <~ ws
    lazy val value = wsOrNl ~> concatenatedValue <~ ws
    lazy val withSeparator = ((anyOf(':','=').take(1) | "+=") ~ value).map {
      case "+=" ~ element => ConcatValue(SelfReference, Seq(ConcatPart("", ArrayBuilderValue(Seq(element))))) 
      case _ ~ v => v 
    }
    lazy val withoutSeparator = wsOrNl ~> objectValue <~ wsOrNl
    lazy val member = (key ~ (withSeparator | withoutSeparator)).map { case k ~ v => BuilderField(k, v) }
    lazy val members = opt(member ~ (separator ~> member).rep).map(_.fold(Seq.empty[BuilderField]) { case m ~ ms => m +: ms })
    (wsOrComment ~> members <~ wsOrComment <~ trailingComma).map(ObjectBuilderValue)
  }

  /** Parses an object value enclosed in braces. */
  lazy val objectValue: Parser[ObjectBuilderValue] = lazily('{' ~> objectMembers <~ '}')

  /** Parses a root configuration object where the enclosing braces may be omitted. */
  lazy val rootObject: Parser[ObjectBuilderValue] = {
    val withBraces = wsOrComment ~> objectValue <~ wsOrComment
    val withoutBraces = wsOrComment ~> objectMembers <~ wsOrComment
    (withBraces | withoutBraces) <~ eof
  }

  /** Parses any kind of value supported by the HOCON format. */
  lazy val anyValue: Parser[ConfigBuilderValue] = 
    objectValue | 
      arrayValue | 
      numberValue | 
      trueValue | 
      falseValue | 
      nullValue |
      substitutionValue |
      stringBuilderValue
  
}
