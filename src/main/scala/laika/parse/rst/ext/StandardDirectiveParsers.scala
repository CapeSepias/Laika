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
  
package laika.parse.rst.ext

import laika.tree.Elements._
import laika.parse.rst.Directives.DirectivePart
import laika.parse.rst.TextRoles.RoleDirectivePart
import laika.parse.rst.BlockParsers
import laika.parse.rst.InlineParsers

/** Defines the custom argument and body parsers for the standard directives.
 *  Most of these delegate to the default block or inline parsers for `reStructuredText`,
 *  but often do only except one specific block type like `Table` or `QuotedBlock` whereas
 *  the default block parser usually accepts any of the blocks.  
 * 
 *  @author Jens Halm
 */
trait StandardDirectiveParsers extends BlockParsers with InlineParsers {

  
  def blockDirective (name: String): Option[DirectivePart[Block]] = None
  def spanDirective (name: String): Option[DirectivePart[Span]] = None
  def textRole (name: String): Option[RoleDirectivePart[String => Span]] = None
  
  /** Parses all standard inline markup supported by `reStructuredText`.
   *  
   *  @p the standard inline parsers including all registered directives for recursive use
   *  @input the input to parse
   *  @return `Right` in case of parser success and `Left` in case of failure, to adjust to the Directive API
   */
  def standardSpans (p: InlineParsers)(input: String): Either[String,Seq[Span]] = 
    try Right(p.parseInline(input.trim))
    catch { case e: Exception => Left(e.getMessage) }
  
  /** Parses a quoted block with nested blocks.
   *  
   *  @p the standard block parsers including all registered directives for recursive use
   *  @input the input to parse
   *  @return `Right` in case of parser success and `Left` in case of failure, to adjust to the Directive API
   */
  def quotedBlock (p: BlockParsers, style: String)(input: String) = {
    p.parseDirectivePart(p.blockList(p.nestedBlock), input).right.map { blocks =>
      blocks.lastOption match {
        case Some(p @ Paragraph(Text(text, opt) :: _, _)) if text startsWith "-- " => 
          val attr = Text(text.drop(3), opt + Styles("attribution")) +: p.content.tail
          QuotedBlock(blocks.init, attr, Styles(style))
        case _ => 
          QuotedBlock(blocks, Nil, Styles(style))
      }
    }
  }

  /** Parses one of the two table types supported by `reStructuredText`.
   *  
   *  @p the standard block parsers including all registered directives for recursive use
   *  @input the input to parse
   *  @return `Right` in case of parser success and `Left` in case of failure, to adjust to the Directive API
   */
  def table (p: BlockParsers)(input: String) = 
    p.parseDirectivePart(p.gridTable | p.simpleTable, input)
  
  /** Parses a caption (a single paragraph) and a legend (one or more blocks), both being optional.
   *  
   *  @p the standard block parsers including all registered directives for recursive use
   *  @input the input to parse
   *  @return `Right` in case of parser success and `Left` in case of failure, to adjust to the Directive API
   */
  def captionAndLegend (p: BlockParsers)(input: String) = {
    val parser = p.opt(p.paragraph) ~ p.opt(p.blankLines ~> p.blockList(p.nestedBlock)) ^^ {
      case p.~(Some(caption), Some(legend)) => (caption.content, legend)
      case p.~(Some(caption), None)         => (caption.content, Nil)
      case _                                => (Nil, Nil)
    } 
    p.parseDirectivePart(parser, input)
  }
  
  /** Parses a target which might be a simple reference, a phrase reference or an uri.
   *  
   *  @input the input to parse
   *  @return `Right` in case of parser success and `Left` in case of failure, to adjust to the Directive API
   */
  def target (input: String): Either[String,Span] = {
    val phraseLinkRef = {
      val refName = escapedText(anyBut('`','<')) ^^ ReferenceName
      "`" ~> refName <~ "`_" ~ ws ~ eof ^^ {
        refName => LinkReference(Nil, refName.normalized, "`" + refName.original + "`_") 
      }
    }
    val simpleLinkRef = {
      simpleRefName <~ "_" ~ ws ~ eof ^^ { 
        refName => LinkReference(Nil, refName, "" + refName + "_")
      }
    }
    val uri = any ^^ {
      uri => ExternalLink(Nil, uri)
    }
    parseDirectivePart(phraseLinkRef | simpleLinkRef | uri, input)
  }
  
  /** Parses unicode values in various notations intertwined with normal text.
   *  
   *  @input the input to parse
   *  @return `Right` in case of parser success and `Left` in case of failure, to adjust to the Directive API
   */
  def unicode (input: String): Either[String,String] = {
    val hexNum = anyIn('0' to '9', 'A' to 'F', 'a' to 'f') 
    val hex = ((("0x" | "x" | "\\x" | "U+" | "u" | "\\u") ~> hexNum) | ("&#x" ~> hexNum <~ ";")) ^^ { Integer.parseInt(_,16) }
    val dec = (anyIn('0' to '9') min 1) ^^ { Integer.parseInt(_) }
    val unicode = (hex | dec) ^^ { int => new String(Character.toChars(int)) }
    val text = anyBut(' ') min 1
    val parser = (((unicode | text) <~ ws)*) ^^ { _.mkString(" ") }
    parseDirectivePart(parser, input)
  }
  
}