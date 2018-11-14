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
import laika.markdown.BlockParsers._

/** Parser for the table extension of GitHub Flavored Markdown.
  *
  * For the spec see [[https://github.github.com/gfm/#table]]
  *
  * @author Jens Halm
  */
object Tables {

  val parser: BlockParserBuilder = BlockParser.forStartChar('|').withSpans { spanParsers =>

    def cell (textParser: Parser[String], cellType: CellType): Parser[Cell] =
      spanParsers.recursiveSpans(textParser.map(_.trim)) ^^ { spans =>
        Cell(cellType, Seq(Paragraph(spans)))
      }

    def row (cellType: CellType): Parser[Row] = {
      val cellText = spanParsers.escapedUntil('|','\n')
      val finalCellText = textLine

      val delimitedCells = (cell(cellText, cellType) <~ lookBehind(1,'|')).rep
      val optFinalCell = cell(finalCellText, cellType).map(Some(_)) | restOfLine ^^^ None

      val rowStart = insignificantSpaces ~ not(anyOf('*','+','-','>','_','#','[',' ','\t').take(1)) ~ opt('|')

      rowStart ~> delimitedCells ~ optFinalCell ^? {
        case cells ~ optFinal if cells.nonEmpty || optFinal.nonEmpty => Row(cells ++ optFinal.toSeq)
      }
    }

    val sepRow: Parser[Seq[Options]] = {

      val separator: Parser[Option[Char] ~ Option[Char]] =
        (ws ~> opt(':')) ~ (anyOf('-').min(1).^ ~> opt(':') <~ ws)

      val delimitedSeparators = (separator <~ '|').rep
      val optFinalSep = opt(separator)

      opt('|') ~> delimitedSeparators ~ optFinalSep <~ wsEol ^^ {
        case seps ~ finalSep => (seps ++ finalSep.toSeq).map {
          case Some(_) ~ Some(_) => Styles("align-center")
          case Some(_) ~ None    => Styles("align-left")
          case None ~ Some(_)    => Styles("align-right")
          case _                 => NoOpt
        }
      }
    }

    case class Header (row: Row, columnOptions: Seq[Options])

    val header: Parser[Header] = row(HeadCell) ~ sepRow ^? {
      case row ~ sepRow if row.content.size == sepRow.size => Header(row, sepRow)
    }

    def applyColumnOptions (rows: Seq[Row], columnOptions: Seq[Options]): Seq[Row] = {
      val count = columnOptions.size
      rows.map(row => row.copy(content =
        row.content
          .take(count)
          .padTo(count, Cell(BodyCell, Nil))
          .zip(columnOptions)
          .map {
            case (cell, opt) => cell.copy(options = opt)
          }
      ))
    }

    header ~ row(BodyCell).rep ^^ { case header ~ bodyRows =>
      laika.ast.Table(
        TableHead(applyColumnOptions(Seq(header.row), header.columnOptions)),
        TableBody(applyColumnOptions(bodyRows, header.columnOptions))
      )
    }
  }

}
