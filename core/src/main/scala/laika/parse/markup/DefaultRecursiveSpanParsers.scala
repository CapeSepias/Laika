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

package laika.parse.markup

import laika.ast.{InvalidElement, Span}
import laika.parse.text.{DelimitedText, PrefixedParser}
import laika.parse.{Failure, Parser, Success}

/** Default implementation for parsing inline markup recursively.
  *
  * @author Jens Halm
  */
trait DefaultRecursiveSpanParsers extends RecursiveSpanParsers with DefaultEscapedTextParsers {


  /** All default span parsers registered for a host markup language.
    */
  protected def spanParsers: Seq[PrefixedParser[Span]]

  private lazy val defaultSpanParser: Parser[List[Span]] = 
    InlineParsers.spans(DelimitedText.Undelimited).embedAll(spanParsers)

  private def createRecursiveSpanParser (textParser: Parser[String], spanParser: => Parser[List[Span]]): Parser[List[Span]] = {
    lazy val spanParser0 = spanParser
    Parser { ctx =>
      textParser.parse(ctx) match {
        case Success(str, next) =>
          spanParser0.parse(str) match {
            case Success(spans, _) => Success(spans, next)
            case f: Failure => f
          }
        case f: Failure => f
      }
    }
  }


  def recursiveSpans (p: Parser[String]): Parser[List[Span]] = p match {
    case dt: DelimitedText[String] => InlineParsers.spans(dt).embedAll(spanParsers)
    case _                         => createRecursiveSpanParser(p, defaultSpanParser)
  }

  def recursiveSpans (p: Parser[String],
                      additionalParsers: => Map[Char, Parser[Span]] = Map.empty): Parser[List[Span]] = {
    lazy val embedded = spanParsers ++ PrefixedParser.fromLegacyMap(additionalParsers)
    p match {
      case dt: DelimitedText[String] => InlineParsers.spans(dt).embedAll(embedded)
      case _ => createRecursiveSpanParser(p, InlineParsers.spans(DelimitedText.Undelimited).embedAll(embedded))
    }
  } 
  
  def recursiveSpans: Parser[List[Span]] = defaultSpanParser

  def delimitedRecursiveSpans (textParser: DelimitedText[String],
                               additionalSpanParsers: => Map[Char, Parser[Span]]): Parser[List[Span]] =
    recursiveSpans(textParser, additionalSpanParsers)

  def delimitedRecursiveSpans (textParser: DelimitedText[String]): Parser[List[Span]] =
    recursiveSpans(textParser)

  def withRecursiveSpanParser [T] (p: Parser[T]): Parser[(String => List[Span], T)] = Parser { ctx =>
    p.parse(ctx) match {
      case Success(res, next) =>
        val recParser: String => List[Span] = { source: String =>
          defaultSpanParser.parse(source) match {
            case Success(spans, _)  => spans
            case f: Failure => List(InvalidElement(f.message, source).asSpan)
          }
        }
        Success((recParser, res), next)
      case f: Failure => f
    }
  }

}
