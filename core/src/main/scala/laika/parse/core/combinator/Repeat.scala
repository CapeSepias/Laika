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

package laika.parse.core.combinator

import laika.parse.core._

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

/** Parser implementation for repeatedly applying the specified base parser until it fails.
  *
  * By default the parser allows for any number of successful parser invocations,
  * including empty results. Use of the `min`, `max` and `take` methods allows
  * to apply additional constrains on the expected number of results.
  *
  * @author Jens Halm
  */
class Repeat[+T] (parser: Parser[T], min: Int = 0, max: Int = Int.MaxValue) extends Parser[List[T]] {

  /** Specifies a minimum number of successful invocations.
    * If the base parser fails before reaching this number
    * of results, this parser will fail.
    */
  def min (num: Int): Repeat[T] = new Repeat(parser, num, max)

  /** Specifies a maximum number of successful invocations.
    * When this number is reached, this parser will stop invoking
    * the base parser even if it would be able to produce more results.
    */
  def max (num: Int): Repeat[T] = new Repeat(parser, min, num)

  /** Specifies the exact number of successful invocations, a shortcut for
    * `.min(num).max(num)`.
    *
    * If the base parser fails before reaching the minimum number
    * of results, this parser will fail.
    * When the maximum number is reached, this parser will stop invoking
    * the base parser even if it would be able to produce more results.
    */
  def take (num: Int): Repeat[T] = new Repeat(parser, num, num)

  def parse (ctx: ParserContext): Parsed[List[T]] = {

    val elems = new ListBuffer[T]

    @tailrec
    def rec (ctx: ParserContext): Parsed[List[T]] =
      if (elems.length == max) Success(elems.toList, ctx)
      else parser.parse(ctx) match {
        case Success(x, next)                  => elems += x; rec(next)
        case _: Failure if elems.length >= min => Success(elems.toList, ctx)
        case f: Failure                        => f
      }

    rec(ctx)

  }


}
