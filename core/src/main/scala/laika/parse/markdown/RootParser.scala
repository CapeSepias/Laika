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

package laika.parse.markdown

import laika.api.ext.{MarkupParsers, ParserDefinition, ParserDefinitionBuilders, SpanParser}
import laika.parse.core.Parser
import laika.parse.core.markup.RootParserBase
import laika.parse.core.text.TextParsers.{anyOf, char}
import laika.rewrite.TreeUtil
import laika.tree.Elements._

/** The main parser for Markdown, combining the individual parsers for block and inline elements,
  * and adding functionality like directives or verbatim HTML depending on configuration.
  *
  * @author Jens Halm
  */
class RootParser (parserExtensions: ParserDefinitionBuilders, isStrict: Boolean = false) extends RootParserBase {

  /** Parses a single escaped character, only recognizing the characters the Markdown syntax document
    *  specifies as escapable.
    *
    *  Note: escaping > is not mandated by the official syntax description, but by the official test suite.
    */
  override lazy val escapedChar: Parser[String] = anyOf('\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '>') take 1


  private lazy val markupParserExtensions: MarkupParsers = parserExtensions.markupParsers(this)
  private val blockParsers = new BlockParsers(this)


  protected lazy val spanParsers: Map[Char,Parser[Span]] = {
    val mainSpans = Seq(
      InlineParsers.enclosedByAsterisk,
      InlineParsers.enclosedByUnderscore,
      InlineParsers.literalSpan,
      InlineParsers.image,
      InlineParsers.link,
      InlineParsers.simpleLink,
      InlineParsers.lineBreak,
      SpanParser.forStartChar('\\').standalone(escapedChar ^^ { Text(_) }).withLowPrecedence
    ).map(_.createParser(this))
    val extSpans = markupParserExtensions.spanParsers
    toSpanParserMap(mainSpans, extSpans)
  }

  private def toParser (definition: ParserDefinition[Block]): Parser[Block] =
    definition.startChar.fold(definition.parser){_ ~> definition.parser} // TODO - temporary until startChar is processed

  protected lazy val topLevelBlock: Parser[Block] = {
    val extBlocks = markupParserExtensions.blockParsers.map(toParser)
    mergeBlockParsers(extBlocks :+ blockParsers.rootMarkdownBlock)
  }

  protected lazy val nestedBlock: Parser[Block] = {
    val extBlocks = markupParserExtensions.blockParsers.filter(_.useInRecursion).map(toParser)
    mergeBlockParsers(extBlocks :+ blockParsers.nestedMarkdownBlock)
  }

  protected lazy val nonRecursiveBlock: Parser[Block] = {
    val extBlocks = markupParserExtensions.blockParsers.filterNot(_.isRecursive).map(toParser)
    mergeBlockParsers(extBlocks :+ blockParsers.nonRecursiveMarkdownBlock)
  }


  // TODO - could be rewrite rule - don't use in strict mode - remove strict flag from this class
  override def blockList (parser: => Parser[Block]): Parser[List[Block]] =
    if (isStrict) super.blockList(parser)
    else super.blockList(parser) ^^ {
      _ map { case h: Header =>
        h.copy(options = h.options + Id(TreeUtil.extractText(h.content).replaceAll("[\n ]+", " ").toLowerCase))
      case other => other
      }
    }

}
