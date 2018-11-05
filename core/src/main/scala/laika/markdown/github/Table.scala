/*
 * Copyright 2013-2018 the original author or authors.
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

package laika.markdown.github

import laika.ast._
import laika.bundle.{BlockParser, BlockParserBuilder}
import laika.parse.Parser
import laika.parse.text.TextParsers._

/** Parser for the table extension of GitHub Flavored Markdown.
  *
  * For the spec see [[https://github.github.com/gfm/#table]]
  *
  * @author Jens Halm
  */
object Table {

  val parser: BlockParserBuilder = BlockParser.forStartChar('|').withSpans { spanParsers =>

    val cellText = anyBut('|','\n')
    val finalCellText = textLine

    def cell (textParser: Parser[String], cellType: CellType): Parser[Cell] =
      spanParsers.recursiveSpans(textParser.map(_.trim)) ^^ { spans =>
        Cell(cellType, Seq(Paragraph(spans)))
      }

    def row (cellType: CellType): Parser[Row] =
      opt('|') ~> (cell(cellText, cellType) <~ '|').rep ~ (cell(finalCellText, cellType).map(Some(_)) | restOfLine ^^^ None) ^? {
        case cells ~ optFinal if cells.nonEmpty || optFinal.nonEmpty => Row(cells ++ optFinal.toSeq)
      }

    val separator: Parser[Unit] = ws ~> anyOf('-').min(1).^ <~ ws

    val sepRow: Parser[Int] = opt('|') ~> (separator ~ '|').rep.map(_.size) ~ opt(separator).map(_.fold(0)(_ => 1)) <~ wsEol ^^ {
      case sep ~ finalSep => sep + finalSep
    }

    val header: Parser[Row] = row(HeadCell) ~ sepRow ^? {
      case row ~ sepRow if row.content.size == sepRow => row
    }

    header ~ row(BodyCell).rep ^^ { case headerRow ~ bodyRows =>
      laika.ast.Table(TableHead(Seq(headerRow)), TableBody(bodyRows))
    }
  }

}
