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

import laika.ast._
import laika.parse._
import laika.parse.text.{DelimitedText, PrefixedParser}

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer
  
/** Provides base parsers that abstract aspects of inline parsing common to most lightweight markup languages.
 *  
 *  It contains helper parsers that abstract the typical logic required for parsing
 *  nested spans. In many cases a parser has to recognize the end of the span as well
 *  as potentially the start of a nested span. These two concerns are usually unrelated.
 *  
 *  This object offers helpers that simplify creating these types of parsers and also
 *  optimize performance of inline parsing. Due to the nature of lightweight text markup
 *  inline parsing would usually require trying a long list of choices on each input
 *  character, which is slow. These base parsers work based on mappings from the first
 *  character of an inline span to the corresponding full parser.
 *  
 *  @author Jens Halm
 */
object InlineParsers {
  

  /** Abstracts the internal process of building up the result of an inline parser.
   *  Since some inline parser produce a tree of nested spans whereas others may
   *  only produce a text result, they often require the same logic in how they
   *  deal with nested constructs.
   */
  private trait ResultBuilder[Elem, +To] {
    def fromString (str: String): Elem
    def += (item: Elem): Unit
    def result: To
  } 
  
  /** ResultBuilder that produces a list of spans.
   */
  private class SpanBuilder extends ResultBuilder[Span, List[Span]] {
    
    private val buffer = new ListBuffer[Span]

    private var last: Option[Span] = None // ListBuffer does not have constant-time update-last
    
    def fromString (str: String): Span = Text(str)
    
    def += (item: Span): Unit = (last, item) match {
      case (Some(Text(text1, NoOpt)), Text(text2, NoOpt)) =>
        last = Some(Text(text1 ++ text2))
      case (Some(Text(content, _)), Reverse(len, target, _, _)) if content.length >= len =>
        buffer += Text(content.dropRight(len))
        last = Some(target)
      case (Some(span), Reverse(_, _, fallback, _)) =>
        buffer += span
        last = Some(fallback)
      case (Some(span), newLast) =>
        buffer += span
        last = Some(newLast)
      case (None, Reverse(_, _, fallback, _)) =>
        last = Some(fallback)
      case (None, newLast) =>
        last = Some(newLast)
    }

    def result: List[Span] = last match {
      case Some(span) =>
        buffer += span
        buffer.toList
      case None =>
        Nil
    }
  }

  /** ResultBuilder that produces a String.
   */
  private class TextBuilder extends ResultBuilder[String, String] {
    
    private val builder = new scala.collection.mutable.StringBuilder
    
    def fromString (str: String): String = str
    def += (item: String): Unit = builder ++= item
    def result: String = builder.toString
  }

  /** Generic base parser that parses inline elements based on the specified
    * helper parsers. Usually not used directly by parser implementations,
    * this is the base parser the other inline parsers of this trait delegate to.
    *
    * @tparam Elem the element type produced by a single parser for a nested span
    * @tparam To the type of the result this parser produces
    * @param baseParser the parser for the text of the current span element
    * @param nested a mapping from the start character of a span to the corresponding parser for nested span elements
    * @param resultBuilder responsible for building the final result of this parser based on the results of the helper parsers
    * @return the resulting parser
    */
  class InlineParser [Elem,To] (baseParser: => DelimitedText[String],
                                nested: => Seq[PrefixedParser[Elem]],
                                resultBuilder: => ResultBuilder[Elem, To]) extends Parser[To] {
    
    private lazy val nestedMap = PrefixedParser.mapAndMerge(nested)
    private lazy val textParser = new DelimitedText(new InlineDelimiter(nestedMap.keySet, baseParser.delimiter))

    def parse (in: ParserContext): Parsed[To] = {

      lazy val builder = resultBuilder // need a fresh one on each invocation

      def addText (text: String): Unit = if (!text.isEmpty) builder += builder.fromString(text)

      def nestedSpanOrNextChar (parser: Parser[Elem], input: ParserContext) = {
        parser.parse(input) match {
          case Success(result, next) => builder += result; next
          case _ => builder += builder.fromString(input.char.toString); input.consume(1)
        }
      }

      @tailrec
      def parse (input: ParserContext) : Parsed[To] = {
        textParser.parse(input) match {
          case Failure(msg, _, maxOffset) =>
            Failure(msg, in, maxOffset)
          case Success(EndDelimiter(text), next) =>
            addText(text)
            Success(builder.result, next)
          case Success(NestedDelimiter(startChar, text), next) =>
            addText(text)
            val parser = nestedMap(startChar)
            val newIn = nestedSpanOrNextChar(parser, next)
            parse(newIn)
        }
      }

      parse(in)
    }

  }

  /** Parses a list of spans based on the specified helper parsers.
    *
    *  @param parser the parser for the text of the current span element
    *  @param spanParsers a mapping from the start character of a span to the corresponding parser for nested span elements
    *  @return the resulting parser
    */
  def spans (parser: => DelimitedText[String], spanParsers: => Map[Char, Parser[Span]]): Parser[List[Span]]
      = new InlineParser(parser, spanParsers.toSeq.map { case (c, p) => PrefixedParser(c)(p) }, new SpanBuilder)

  /** Parses text based on the specified helper parsers.
    *
    *  @param parser the parser for the text of the current element
    *  @param nested a mapping from the start character of a span to the corresponding parser for nested span elements
    *  @return the resulting parser
    */
  def text (parser: => DelimitedText[String], nested: => Map[Char, Parser[String]]): Parser[String]
      = new InlineParser(parser, nested.toSeq.map { case (c, p) => PrefixedParser(c)(p) }, new TextBuilder)


}
