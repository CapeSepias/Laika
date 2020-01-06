/*
 * Copyright 2012-2020 the original author or authors.
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

package laika.parse.code.common

import laika.parse.text.TextParsers._
import laika.parse.code.{CodeCategory, CodeSpan, CodeSpanParser}

/**
  * @author Jens Halm
  */
object Comment {
  
  def singleLine (start: String): CodeSpanParser = {
    require(start.nonEmpty)
    CodeSpanParser(start.head) {
      start.tail ~> restOfLine ^^ { text => CodeSpan(start+text+"\n", CodeCategory.Comment) }
    }
    // TODO - create restOfLine variant that keeps final \n
  }

  def multiLine (start: String, end: String): CodeSpanParser = {
    require(start.nonEmpty)
    CodeSpanParser(start.head) {
      start.tail ~> delimitedBy(end) ^^ { text => CodeSpan(start+text+end, CodeCategory.Comment) }
    }
  }
  
}
