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

package laika.parse.core

import java.util

import scala.collection.mutable.ArrayBuffer

/**
  * @author Jens Halm
  */
case class ParserContext (source: Source, offset: Int, nestLevel: Int) {

  val input: String = source.value

  /**  Indicates whether this contexts offset is behind
    *  the last character of the input string
    */
  def atEnd: Boolean = offset >= input.length

  def remaining: Int = input.length - offset

  def char: Char = charAt(0)

  def charAt (relativeOffset: Int): Char = {
    val i = offset + relativeOffset
    if (i < input.length) input.charAt(i) else throw new IndexOutOfBoundsException(i.toString)
  }

  def capture (numChars: Int): String =
    if (numChars == 0) ""
    else if (numChars < 0 || numChars + offset > input.length) throw new IndexOutOfBoundsException(numChars.toString)
    else input.substring(offset, offset + numChars)

  def consume (numChars: Int): ParserContext =
    if (numChars != 0) ParserContext(source, offset + numChars, nestLevel)
    else this

  def position: Position = new Position(source, offset)

  def reverse: ParserContext = ParserContext(source.reverse, remaining, nestLevel)

}

object ParserContext {

  def apply (input: String): ParserContext = ParserContext(Source(input), 0, 0)

  def apply (input: String, nestLevel: Int): ParserContext = ParserContext(Source(input), 0, nestLevel)

  def apply (input: java.io.Reader): ParserContext = apply(input, 8 * 1024)

  def apply (input: java.io.Reader, sizeHint: Int): ParserContext = {

    val arr = new Array[Char](sizeHint)
    val buffer = new StringBuilder
    var numCharsRead: Int = 0

    while ({numCharsRead = input.read(arr, 0, arr.length); numCharsRead != -1}) {
      buffer.appendAll(arr, 0, numCharsRead)
    }

    apply(buffer.toString)
  }

}

case class Source (value: String) {

  /** An index that contains all line starts, including first line, and eof. */
  lazy val lineStarts: Array[Int] = {
    val lineStarts = new ArrayBuffer[Int]
    lineStarts += 0
    var pos = 0
    val len = value.length
    while (pos < len) {
      if (value(pos) == '\n') lineStarts += pos + 1
      pos += 1
    }
    lineStarts += len
    lineStarts.toArray
  }

  lazy val reverse = Source(value.reverse)

}

/**  Represents an offset into a source string. Its main purpose
  *  is error reporting, e.g. printing a visual representation of the line
  *  containing the error.
  *
  *  @param s the source for this position
  *  @param offset the offset into the source string
  *
  *  @author Jens Halm
  */
case class Position(s: Source, offset: Int) {

  val source = s.value

  /** The line number referred to by this position, starting at 1. */
  lazy val line: Int = {
    val result = util.Arrays.binarySearch(s.lineStarts, offset)
    if (result == s.lineStarts.length - 1) result // EOF position is not on a new line
    else if (result < 0) Math.abs(result) - 1 // see javadoc for binarySearch
    else result + 1 // line is 1-based
  }

  /** The column number referred to by this position, starting at 1. */
  lazy val column: Int = offset - s.lineStarts(line - 1) + 1

  /** The contents of the line at the current offset (not including a newline). */
  lazy val lineContent: String = {
    val startIndex = s.lineStarts(line - 1)
    val endIndex = s.lineStarts(line)

    val result = source.substring(startIndex, endIndex)
    if (result.endsWith("\n")) result.dropRight(1) else result
  }

  /** The contents of the line at the current offset, decorated with
    * a caret indicating the column. Example:
    * {{{
    *   The content of the current line with a caret under the c.
    *       ^
    * }}}
    */
  def lineContentWithCaret = lineContent + "\n" + " " * (column-1) + "^"

  /** A string representation of this Position of the form `line.column`. */
  override lazy val toString = s"$line.$column"

}

