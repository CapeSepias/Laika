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

package laika.parse.core.text

import laika.parse.core._
import laika.util.stats.Counter

/**
  * A parser that matches a literal string.
  *
  * @author Jens Halm
  */
case class Literal (expected: String) extends Parser[String] {
  Counter.Literal.NewInstance.inc()
  val msgProvider = MessageProvider { context =>
    val toCapture = Math.min(context.remaining, expected.length)
    val found = context.capture(toCapture)
    s"`$expected' expected but `$found` found"
  }

  def apply (in: ParserContext) = {
    Counter.Literal.Invoke.inc()
    val source = in.input
    val start = in.offset
    var i = 0
    var j = start
    while (i < expected.length && j < source.length && expected.charAt(i) == source.charAt(j)) {
      i += 1
      j += 1
    }
    if (i == expected.length) {Counter.Literal.Read.inc(i); Success(expected, in.consume(i))}
    else Failure(msgProvider, in)
  }

}
