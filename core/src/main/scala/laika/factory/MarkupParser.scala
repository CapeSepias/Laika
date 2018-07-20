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

package laika.factory

import laika.api.ext._
import laika.parse.core.Parser
import laika.parse.core.combinator.Parsers.opt
import laika.parse.core.text.TextParsers
import laika.parse.core.text.TextParsers.blankLines
import laika.tree.Elements.Block

/** Responsible for creating parser instances for a specific markup format.
 *  A parser is simply a function of type `Input => Document`.
 *  
 *  @author Jens Halm
 */
trait MarkupParser {
  
  /** The file suffixes recognized by this parser.
   *  When transforming entire directories only files with
   *  names ending in one of the specified suffixes will
   *  be considered.
   * 
   *  It is recommended not to support `txt`
   *  or similarly common suffixes as this might interfere
   *  with other installed formats.
   */
  def fileSuffixes: Set[String]

  /** All block parsers for the markup language this parser processes.
    */
  def blockParsers: Seq[BlockParserBuilder]

  /** All span parsers for the markup language this parser processes.
    */
  def spanParsers: Seq[SpanParserBuilder]

  /** Parses the character after the one that started the escape sequence (usually a backslash).
    *
    * The default implementation parses any character as is, this can be overridden in case
    * the host language has more specific rules for escape sequences.
    */
  def escapedChar: Parser[String] = TextParsers.any.take(1)

  /** The parser-specific extensions that need to be installed
   *  for each transformation that involves this parser.
   * 
   *  One scenario where a parser needs to provide a bundle
   *  is when it produces tree elements that are unknown
   *  to the built-in rewrite rules and renderers.
   */
  def extensions: Seq[ExtensionBundle]

  def createBlockListParser (parser: Parser[Block]): Parser[Seq[Block]] = (parser <~ opt(blankLines))*

}
