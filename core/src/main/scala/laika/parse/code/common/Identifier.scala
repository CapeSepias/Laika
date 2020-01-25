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

import laika.ast.CodeSpan
import laika.parse.Parser
import laika.parse.code.{CodeCategory, CodeSpanParsers}
import laika.parse.text.TextParsers._

/**
  * @author Jens Halm
  */
object Identifier {
  
  /* TODO - support for non-ASCII identifier characters requires changes in the low-level optimizer
     for the span parser. This ASCII-only support will probably already cover a large range of common use cases.
  */
  val idStartChars: Set[Char] = ('a' to 'z').toSet ++ ('A' to 'Z').toSet
  val idPartChars: Set[Char] = ('0' to '9').toSet
  
  
  val upperCaseTypeName: String => CodeCategory = s => 
    if (s.nonEmpty && s.head.isUpper) CodeCategory.TypeName else CodeCategory.Identifier 
    

  case class IdParser(idStartChars: Set[Char], 
                      idNonStartChars: Set[Char], 
                      category: String => CodeCategory = _ => CodeCategory.Identifier,
                      allowDigitBeforeStart: Boolean = false) {

    import NumberLiteral._
    
    def withCategoryChooser(f: String => CodeCategory): IdParser = {
      copy(category = f)
    }
    
    def withIdStartChars(chars: Char*): IdParser = copy(idStartChars = idStartChars ++ chars.toSet)
    
    def withIdPartChars(chars: Char*): IdParser = copy(idNonStartChars = idNonStartChars ++ chars.toSet)
    
    val idRestParser: Parser[String] = anyOf((idStartChars ++ idNonStartChars).toSeq:_*) // TODO - remove

    def build: CodeSpanParsers = {
      
      CodeSpanParsers(idStartChars) {
        
        val prevChar = lookBehind(2, anyWhile(c => (!Character.isDigit(c) || allowDigitBeforeStart) && !Character.isLetter(c)).take(1)) | lookBehind(1, atStart)

        val idStart = lookBehind(1, any.take(1))
        
        (prevChar ~> idStart ~ idRestParser).concat.map(id => Seq(CodeSpan(id, category(id))))
        
      }
    }
    
    def standaloneParser: Parser[CodeSpan] = {
      val idStart = anyOf(idStartChars.toSeq:_*).take(1)
      (idStart ~ idRestParser).concat.map(id => CodeSpan(id, category(id)))
    }

  }
  
  def standard: IdParser = IdParser(idStartChars, idPartChars)
  
}
