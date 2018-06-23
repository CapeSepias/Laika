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

import com.typesafe.config.Config
import laika.api.ext.{MarkupParsers, ParserDefinition, ParserDefinitionBuilders}
import laika.directive.Directives.{Blocks, Spans}
import laika.directive.MarkupDirectiveParsers
import laika.parse.core.Parser
import laika.parse.core.markup.RootParserBase
import laika.parse.core.text.TextParsers.anyOf
import laika.parse.core.text.TextParsers.char
import laika.parse.markdown.html.HTMLParsers
import laika.rewrite.TreeUtil
import laika.tree.Elements._
import laika.tree.Paths.Path

/** The main parser for Markdown, combining the individual parsers for block and inline elements,
  * and adding functionality like directives or verbatim HTML depending on configuration.
  *
  * @author Jens Halm
  */
class RootParser (blockDirectives: Map[String, Blocks.Directive],
                  spanDirectives:  Map[String, Spans.Directive],
                  parserExtensions: ParserDefinitionBuilders,
                  verbatimHTML: Boolean,
                  isStrict: Boolean) extends RootParserBase {

  /** Parses a single escaped character, only recognizing the characters the Markdown syntax document
    *  specifies as escapable.
    *
    *  Note: escaping > is not mandated by the official syntax description, but by the official test suite.
    */
  override lazy val escapedChar: Parser[String] = anyOf('\\', '`', '*', '_', '{', '}', '[', ']', '(', ')', '#', '+', '-', '.', '!', '>') take 1

  private lazy val markupParserExtensions: MarkupParsers = parserExtensions.markupParsers(this)

  private val htmlParsers = if (verbatimHTML) Some(new HTMLParsers(this)) else None

  private val directiveParsers =
    if (!isStrict) Some(new MarkupDirectiveParsers(this, blockDirectives, spanDirectives)) else None

  private val inlineParsers = new InlineParsers(this)

  private val blockParsers = new BlockParsers(this)


  protected lazy val spanParsers: Map[Char,Parser[Span]] = {

    val mainParsers = inlineParsers.allSpanParsers
    val htmlSpans = htmlParsers.map(_.htmlSpanParsers).getOrElse(Map())
    val extSpans = markupParserExtensions.spanParserMap
    val directiveSpans = directiveParsers.map(_.spanParsers).getOrElse(Map())

    val withHTML = mergeSpanParsers(mainParsers, htmlSpans ++ extSpans)
    mergeSpanParsers(withHTML, directiveSpans)
  }

  private def toParser (definition: ParserDefinition[Block]): Parser[Block] =
    definition.startChar.fold(definition.parser){_ ~> definition.parser} // TODO - temporary until startChar is processed

  protected lazy val topLevelBlock: Parser[Block] = {

    val mainParsers = Seq(blockParsers.rootMarkdownBlock)

    val blockDirectives = directiveParsers.map(_.blockDirective).toSeq
    val htmlBlocks = htmlParsers.map(_.topLevelBlocks).toSeq.flatten
    val extBlocks = markupParserExtensions.blockParsers.map(toParser)

    mergeBlockParsers(blockDirectives ++ htmlBlocks ++ extBlocks ++ mainParsers)
  }

  protected lazy val nestedBlock: Parser[Block] = {

    val mainParsers = Seq(blockParsers.nestedMarkdownBlock)

    val blockDirectives = directiveParsers.map(_.blockDirective).toSeq
    val extBlocks = markupParserExtensions.blockParsers.filter(_.useInRecursion).map(toParser)

    mergeBlockParsers(blockDirectives ++ extBlocks ++ mainParsers)
  }

  protected lazy val nonRecursiveBlock: Parser[Block] = {
    val extBlocks = markupParserExtensions.blockParsers.filterNot(_.isRecursive).map(toParser)

    mergeBlockParsers(extBlocks :+ blockParsers.nonRecursiveMarkdownBlock)
  }


  // TODO - could be rewrite rule
  override def blockList (parser: => Parser[Block]): Parser[List[Block]] =
    if (isStrict) super.blockList(parser)
    else super.blockList(parser) ^^ {
      _ map { case h: Header =>
        h.copy(options = h.options + Id(TreeUtil.extractText(h.content).replaceAll("[\n ]+", " ").toLowerCase))
      case other => other
      }
    }

  override def config (path: Path): Parser[Either[InvalidBlock,Config]] =
    directiveParsers.map(_.config(path)).getOrElse(super.config(path))


}
