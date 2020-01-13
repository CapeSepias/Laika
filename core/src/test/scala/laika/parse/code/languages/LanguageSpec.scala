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

package laika.parse.code.languages

import laika.api.MarkupParser
import laika.ast._
import laika.format.Markdown
import laika.markdown.github.GitHubFlavor
import laika.parse.code.CodeCategory._
import laika.parse.code.{CodeCategory, CodeSpan}
import org.scalatest.{Matchers, WordSpec}

/**
  * @author Jens Halm
  */
class LanguageSpec extends WordSpec with Matchers {

  
  "The syntax highlighter for code blocks" should {
    
    def parse (input: String): RootElement = 
      MarkupParser.of(Markdown).using(GitHubFlavor).build.parse(input).toOption.get.content
    
    def s (category: CodeCategory, text: String): CodeSpan = CodeSpan(text, category)
    def t (text: String): CodeSpan = CodeSpan(text)
    
    val space: CodeSpan = CodeSpan(" ")
    val comma: CodeSpan = CodeSpan(", ")
    val equals: CodeSpan = CodeSpan(" = ")
    
    "parse Scala code" in {
      
      val input =
        """# Code
          |
          |```scala
          |case class Foo (bar: Int, baz: String) {
          |
          |  val xx = "some \t value"
          |  
          |  lazy val `y-y` = +++line 1
          |    |line 2+++.stripMargin
          |  
          |  def bag = Seq(true, null, 's', 0xff)
          |  
          |  // just a short example
          |  
          |}
          |```
        """.stripMargin.replaceAllLiterally("+++", "\"\"\"")
      
      parse(input) shouldBe RootElement(Seq(
        Title(Seq(Text("Code")), Styles("title") + Id("code")),
        CodeBlock("scala", Seq(
          CodeSpan("case", Keyword),
          space,
          CodeSpan("class", Keyword),
          space,
          CodeSpan("Foo", TypeName),
          CodeSpan(" ("),
          CodeSpan("bar", Identifier),
          CodeSpan(": "),
          CodeSpan("Int", TypeName),
          comma,
          CodeSpan("baz", Identifier),
          CodeSpan(": "),
          CodeSpan("String", TypeName),
          CodeSpan(") {\n\n  "),
          CodeSpan("val", Keyword),
          space,
          CodeSpan("xx", Identifier),
          equals,
          CodeSpan("\"some ", StringLiteral),
          CodeSpan("\\t", EscapeSequence),
          CodeSpan(" value\"", StringLiteral),
          CodeSpan("\n  \n  "),
          CodeSpan("lazy", Keyword),
          space,
          CodeSpan("val", Keyword),
          space,
          CodeSpan("`y-y`", Identifier),
          equals,
          CodeSpan("\"\"\"line 1\n    |line 2\"\"\"", StringLiteral),
          CodeSpan("."),
          CodeSpan("stripMargin", Identifier),
          CodeSpan("\n  \n  "),
          CodeSpan("def", Keyword),
          space,
          CodeSpan("bag", Identifier),
          equals,
          CodeSpan("Seq", TypeName),
          CodeSpan("("),
          CodeSpan("true", BooleanLiteral),
          comma,
          CodeSpan("null", LiteralValue),
          comma,
          CodeSpan("'s'", CharLiteral),
          comma,
          CodeSpan("0xff", NumberLiteral),
          CodeSpan(")\n  \n  "),
          CodeSpan("// just a short example\n", CodeCategory.Comment),
          CodeSpan("  \n}")
        ))
      ))
      
    }
    
  }
  
  
}
