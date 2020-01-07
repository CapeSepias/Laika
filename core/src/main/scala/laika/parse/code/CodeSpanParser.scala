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

package laika.parse.code

import laika.ast.{NoOpt, Options, Span, TextContainer}
import laika.parse.Parser

/**
  * @author Jens Halm
  */
sealed trait CodeSpanParser {

  def startChar: Char
  
  def parser: Parser[CodeSpan]
  
}

object CodeSpanParser {
  
  def apply(category: CodeCategory, startChar: Char)(parser: Parser[String]): CodeSpanParser = {
    
    def create(s: Char, p: Parser[String]) = new CodeSpanParser {
      val startChar = s
      val parser = p.map(res => CodeSpan(s"$startChar$res", category))
    }
    
    create(startChar, parser)
  }
  
}

sealed trait CodeCategory

object CodeCategory {
  
  object Comment extends CodeCategory
  object Keyword extends CodeCategory
  object BooleanLiteral extends CodeCategory
  object NumberLiteral extends CodeCategory
  object LiteralValue extends CodeCategory
  object TypeName extends CodeCategory
  
}

case class CodeSpan (content: String, categories: Set[CodeCategory], options: Options = NoOpt) extends Span with TextContainer {
  type Self = CodeSpan
  def withOptions (options: Options): CodeSpan = copy(options = options)
}

object CodeSpan {
  
  def apply (content: String, category: CodeCategory): CodeSpan = apply(content, Set(category))

  def apply (content: String): CodeSpan = apply(content, Set(), NoOpt)
  
}
