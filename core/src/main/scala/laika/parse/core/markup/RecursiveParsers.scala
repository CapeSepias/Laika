/*
 * Copyright 2013-2017 the original author or authors.
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

package laika.parse.core.markup

import laika.parse.core.Parser
import laika.parse.core.text.DelimitedText
import laika.tree.Elements.{Block, Span}

/** Provides parsers for nested blocks, custom block parser implementations
  * can use these without knowing the available span types of the host
  * markup language.
  *
  * Includes the support for nested spans and escaped text.
  *
  * @author Jens Halm
  */
trait RecursiveParsers extends RecursiveSpanParsers {

  /** Lifts the specified text parser to parse the string result
    * as a sequence of blocks.
    *
    * This type of span parser is usually used in block parsers,
    * that support the nesting of other blocks.
    */
  def recursiveBlocks (p: Parser[String]): Parser[Seq[Block]]

  /** Adds a block parser function to the result of the specified parser.
    * The function can be used for any kind of custom block parsing of portions of the
    * result produced by the base parser.
    *
    * The parser function never fails, but instead inserts blocks of type `InvalidBlock`
    * into the result in case of errors.
    */
  def withRecursiveBlockParser [T] (p: Parser[T]): Parser[(String => Seq[Block], T)]

}

/** Provides parsers for nested spans, custom span parser implementations
  * can use these without knowing the available span types of the host
  * markup language.
  *
  * Includes the support for escaped text.
  */
trait RecursiveSpanParsers extends EscapedTextParsers {

  /** Lifts the specified text parser to parse the string result
    * as a sequence of spans.
    *
    * This type of span parser is usually used in block parsers,
    * that need to process inline markup after the text for the block
    * has been parsed from the input string.
    */
  def recursiveSpans (parser: Parser[String]): Parser[List[Span]]

  /** Lifts the specified text parser to parse the string result
    * as a sequence of spans. Adds the specified additional parsers
    * to the span parsers of the host markup language.
    *
    * This type of span parser is usually used in block parsers,
    * that need to process inline markup after the text for the block
    * has been parsed from the input string.
    */
  def recursiveSpans (parser: Parser[String],
                      additionalParsers: => Map[Char, Parser[Span]] = Map.empty): Parser[List[Span]]

  /** Parses the input into a sequence of spans based on the available span types
    * of the host markup language.
    *
    * This parser always parses to the end of the input, therefore is usually applied to
    * the string result of a previous parser invocation.
    */
  def recursiveSpans: Parser[List[Span]]

  /** Lifts the specified text parser to parse the string result
    * as a sequence of spans.
    *
    * This type of span parser is usually used in inline parsers,
    * that need to process inline markup for a span with
    * a delimiter while supporting nested spans.
    */
  def delimitedRecursiveSpans (textParser: DelimitedText[String]): Parser[List[Span]]

  /** Lifts the specified text parser to parse the string result
    * as a sequence of spans. Adds the specified additional parsers
    * to the span parsers of the host markup language.
    *
    * This type of span parser is usually used in inline parsers,
    * that need to process inline markup for a span with
    * a delimiter while supporting nested spans.
    */
  def delimitedRecursiveSpans (textParser: DelimitedText[String],
                               additionalSpanParsers: => Map[Char, Parser[Span]]): Parser[List[Span]]

  /** Adds a span parser function to the result of the specified parser.
    * The function can be used for any kind of custom span parsing of portions of the
    * result produced by the base parser.
    *
    * The parser function never fails, but instead inserts spans of type `InvalidSpan`
    * into the result in case of errors.
    */
  def withRecursiveSpanParser [T] (p: Parser[T]): Parser[(String => List[Span], T)]

}

/** Provides parsers for escaped text, custom span parser implementations
  * can use these without knowing the rules of the host markup language for
  * escaping text.
  */
trait EscapedTextParsers {

  /** Parses the character after the one that started the escape sequence (usually a backslash).
    */
  def escapedChar: Parser[String]

  /** Adds support for escape sequences to the specified text parser.
    */
  def escapedText(p: DelimitedText[String]): Parser[String]

  /** Parses a span of text until one of the specified characters is seen
    * (unless it is escaped),
    * while also processing escaped characters, but no other nested
    *  spans. The final character is not included in the result.
    *
    * @param char the character(s) that signals the end of the text span
    * @return a parser for a text span that supports escape sequences
    */
  def escapedUntil(char: Char*): Parser[String]

}
