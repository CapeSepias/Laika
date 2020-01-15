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

import laika.ast.~
import laika.parse.code.{CodeCategory, CodeSpan, CodeSpanParsers}
import laika.parse.text.TextParsers._

/**
  * @author Jens Halm
  */
object CharLiteral {
  
  case class CharParser(delim: Char,
                        embedded: Seq[CodeSpanParsers] = Nil) extends EmbeddedCodeSpans {
    
    val category: CodeCategory = CodeCategory.CharLiteral

    def embed(childSpans: CodeSpanParsers*): CharParser = {
      copy(embedded = embedded ++ childSpans)
    }

    def build: CodeSpanParsers = CodeSpanParsers(delim) {

      val plainChar = lookBehind(1, anyBut('\'', '\n').take(1)).map(CodeSpan(_, category))
      val closingDelim = anyOf(delim).take(1).map(CodeSpan(_, category))
      
      any.take(1).flatMap { char =>
        (spanParserMap.getOrElse(char.head, plainChar) ~ closingDelim).map { 
          case span ~ closingDel => mergeCodeSpans(delim, toCodeSpans(span) :+ closingDel) 
        }
      }

    }

  }
  
  def standard: CharParser = CharParser('\'')
  
}
