/*
 * Copyright 2013-2016 the original author or authors.
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

package laika.directive

import com.typesafe.config.ConfigFactory
import laika.api.MarkupParser
import laika.ast.Path.Root
import laika.ast._
import laika.ast.helper.ModelBuilder
import laika.bundle.{BlockParser, BlockParserBuilder, ParserBundle}
import laika.format.Markdown
import laika.parse.Parser
import laika.parse.combinator.Parsers
import laika.parse.directive.BlockDirectiveParsers
import laika.parse.helper.{DefaultParserHelpers, ParseResultHelpers}
import laika.parse.markup.RootParserProvider
import laika.parse.text.TextParsers._
import laika.rewrite.TemplateRewriter
import org.scalatest.{FlatSpec, Matchers}


class BlockDirectiveAPISpec extends FlatSpec
                          with Matchers
                          with ModelBuilder {

  
  object DirectiveSetup {
    import Blocks.dsl._

    trait Empty {
      val directive = Blocks.create("dir")(Blocks.dsl.empty(p("foo")))
    }
    
    trait RequiredDefaultAttribute {
      val directive = Blocks.create("dir") { attribute(Default) map (p(_)) }
    }
    
    trait OptionalDefaultAttribute {
      val directive = Blocks.create("dir") { 
        attribute(Default, positiveInt).optional map (num => p(num.map(_.toString).getOrElse("<>"))) 
      }
    }
    
    trait RequiredNamedAttribute {
      val directive = Blocks.create("dir") { attribute("name") map (p(_)) }
    }
    
    trait OptionalNamedAttribute {
      val directive = Blocks.create("dir") { 
        attribute("name", positiveInt).optional map (num => p(num.map(_.toString).getOrElse("<>"))) 
      }
    }
    
    trait Body {
      val directive = Blocks.create("dir") { body map (BlockSequence(_)) }
    }

    trait SeparatedBody {
      sealed trait Child extends Product with Serializable
      case class Foo (content: Seq[Block]) extends Child
      case class Bar (content: Seq[Block], attr: String) extends Child
      val sep1 = Blocks.separator("foo") { body map Foo }
      val sep2 = Blocks.separator("bar") { (body ~ attribute(Default)) map { case blocks ~ attr => Bar(blocks, attr) } }
      val directive = Blocks.create("dir") { separatedBody[Child](Seq(sep1, sep2)) map { multipart =>
        val seps = multipart.children.flatMap {
          case Foo(content) => p("foo") +: content
          case Bar(content, attr) => p(attr) +: content
        } 
        BlockSequence(multipart.mainBody ++ seps)
      }}
    }
    
    trait FullDirectiveSpec {
      val directive = Blocks.create("dir") {
        (attribute(Default) ~ attribute("strAttr").optional ~ attribute("intAttr", positiveInt).optional ~
        body).map {
          case defAttr ~ strAttr ~ intAttr ~ defBody =>
            val sum = intAttr.getOrElse(0)
            val str = defAttr + ":" + strAttr.getOrElse("..") + ":" + sum
            BlockSequence(p(str) +: defBody)
        }
      }
    }
    
    trait DirectiveWithParserAccess {
      val directive = Blocks.create("dir") { 
        (body(string) ~ parser).map {
          case body ~ parser => BlockSequence(parser(body.drop(3)))
        }
      }
    }
    
    trait DirectiveWithContextAccess {
      val directive = Blocks.create("dir") { 
        (body(string) ~ cursor).map {
          case body ~ cursor => p(body + cursor.target.path)
        }
      }
    }
    
  }
  
  trait BlockParser extends ParseResultHelpers
                          with DefaultParserHelpers[RootElement] {

    def directive: Blocks.Directive

    lazy val directiveSupport: ParserBundle = DirectiveSupport.withDirectives(Seq(directive), Seq(), Seq()).parsers

    lazy val paragraphParser: BlockParserBuilder = BlockParser.withoutStartChar.recursive { recParser =>
      recParser.recursiveSpans(((Parsers.not(blankLine) ~> restOfLine) +) ^^ (_.mkString("\n"))) ^^ { Paragraph(_) }
    }

    lazy val defaultParser: Parser[RootElement] = RootParserProvider.forParsers(
      blockParsers = Seq(paragraphParser),
      markupExtensions = directiveSupport.markupExtensions
    ).rootElement ^^ { root =>
      TemplateRewriter.rewriteRules(DocumentCursor(
        Document(Root, root, config = ConfigFactory.parseString("ref: value"))
      )).rewriteBlock(root).asInstanceOf[RootElement]
    }

    def invalid (input: String, error: String): InvalidBlock = InvalidElement(error, input).asBlock

  }


  import DirectiveSetup._

  "The directive parser" should "parse an empty directive" in {
    new BlockParser with Empty {
      val input = """aa
        |
        |@:dir
        |
        |bb""".stripMargin
      Parsing (input) should produce (root(p("aa"), p("foo"), p("bb")))
    }
  }
  
  it should "parse a directive with one required default string attribute" in {
    new BlockParser with RequiredDefaultAttribute {
      val input = """aa
        |
        |@:dir { foo }
        |
        |bb""".stripMargin
      Parsing (input) should produce (root(p("aa"), p("foo"), p("bb")))
    }
  }

  it should "detect a directive with a missing required default attribute" in {
    new BlockParser with RequiredDefaultAttribute {
      val input = """aa
        |
        |@:dir
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': required default attribute is missing"
      Parsing (input) should produce (root(p("aa"), invalid("@:dir",msg), p("bb")))
    }
  }

  it should "parse a directive with an optional default int attribute" in {
    new BlockParser with OptionalDefaultAttribute {
      val input = """aa
        |
        |@:dir { 5 }
        |
        |bb""".stripMargin
      Parsing (input) should produce (root(p("aa"), p("5"), p("bb")))
    }
  }

  it should "detect a directive with an optional invalid default int attribute" in {
    new BlockParser with OptionalDefaultAttribute {
      val input = """aa
        |
        |@:dir { foo }
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': error converting default attribute: not an integer: foo"
      Parsing (input) should produce (root(p("aa"), invalid("@:dir { foo }",msg), p("bb")))
    }
  }

  it should "parse a directive with a missing optional default int attribute" in {
    new BlockParser with OptionalDefaultAttribute {
      val input = """aa
        |
        |@:dir
        |
        |bb""".stripMargin
      Parsing (input) should produce (root(p("aa"), p("<>"), p("bb")))
    }
  }

  it should "parse a directive with one required named string attribute" in {
    val input = """aa
        |
        |@:dir { name=foo }
        |
        |bb""".stripMargin
    new BlockParser with RequiredNamedAttribute {
      Parsing (input) should produce (root(p("aa"), p("foo"), p("bb")))
    }
  }

  it should "parse a directive with a named string attribute value in quotes" in {
    val input = """aa
        |
        |@:dir { name="foo bar" }
        |
        |bb""".stripMargin
    new BlockParser with RequiredNamedAttribute {
      Parsing (input) should produce (root(p("aa"), p("foo bar"), p("bb")))
    }
  }

  it should "detect a directive with a missing required named attribute" in {
    new BlockParser with RequiredNamedAttribute {
      val input = """aa
        |
        |@:dir
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': required attribute with name 'name' is missing"
      Parsing (input) should produce (root(p("aa"), invalid("@:dir",msg), p("bb")))
    }
  }

  it should "parse a directive with an optional named int attribute" in {
    new BlockParser with OptionalNamedAttribute {
      val input = """aa
        |
        |@:dir { name=5 }
        |
        |bb""".stripMargin
      Parsing (input) should produce (root(p("aa"), p("5"), p("bb")))
    }
  }

  it should "detect a directive with an optional invalid named int attribute" in {
    new BlockParser with OptionalNamedAttribute {
      val input = """aa
        |
        |@:dir { name=foo }
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': error converting attribute with name 'name': not an integer: foo"
      Parsing (input) should produce (root(p("aa"), invalid("@:dir { name=foo }",msg), p("bb")))
    }
  }

  it should "parse a directive with a missing optional named int attribute" in {
    new BlockParser with OptionalNamedAttribute {
      val input = """aa
        |
        |@:dir
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': required default attribute is missing"
      Parsing (input) should produce (root(p("aa"), p("<>"), p("bb")))
    }
  }

  it should "parse a directive with a body" in {
    new BlockParser with Body {
      val input = """aa
        |
        |@:dir
        |some
        |{{config.ref}}
        |text
        |@:@
        |
        |bb""".stripMargin
      val body = BlockSequence(List(p(txt("some\nvalue\ntext"))))
      Parsing (input) should produce (root(p("aa"), body, p("bb")))
    }
  }

  it should "parse a directive with a body and trim empty lines at the start and end" in {
    new BlockParser with Body {
      val input = """aa
        |
        |@:dir
        |
        |some
        |{{config.ref}}
        |text
        |  
        |@:@
        |
        |bb""".stripMargin
      val body = BlockSequence(List(p(txt("some\nvalue\ntext"))))
      Parsing (input) should produce (root(p("aa"), body, p("bb")))
    }
  }

  it should "detect a directive with a missing body" in {
    new BlockParser with Body {
      val input = """aa
        |
        |@:dir
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': required body is missing"
      Parsing (input) should produce (root(p("aa"), invalid("@:dir",msg), p("bb")))
    }
  }

  it should "parse a directive with a separated body" in {
    new BlockParser with SeparatedBody {
      val input = """aa
        |
        |@:dir
        |aaa
        |
        |@:foo
        |bbb
        |
        |@:bar { baz }
        |ccc
        |@:@
        |
        |bb""".stripMargin
      val body = BlockSequence(List(p("aaa"),p("foo"),p("bbb"),p("baz"),p("ccc")))
      Parsing (input) should produce (root(p("aa"), body, p("bb")))
    }
  }

  it should "detect a directive with an invalid separator" in {
    new BlockParser with SeparatedBody {
      val input = """aa
        |
        |@:dir
        |aaa
        |
        |@:foo
        |bbb
        |
        |@:bar
        |ccc
        |@:@
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': One or more errors processing separator directive 'bar': required default attribute is missing"
      val src = input.split("\n").toSeq.slice(2, 11).mkString("\n")
      println(MarkupParser
        .of(Markdown)
        .using(DirectiveSupport.withDirectives(Seq(directive), Seq(), Seq()))
        .build
        .parse(input)
        .right.get
        .content.content(1).asInstanceOf[InvalidBlock].message.content
      )
      
      Parsing (input) should produce (root(p("aa"), invalid(src,msg), p("bb")))
    }
  }

  it should "parse a full directive spec with all elements present" in {
    new FullDirectiveSpec with BlockParser {
      val input = """aa
        |
        |@:dir { foo strAttr=str intAttr=7 }
        |
        |1 {{config.ref}} 2
        |
        |@:@
        |
        |bb""".stripMargin
      val body = BlockSequence(List(
        p("foo:str:7"),
        p(txt("1 value 2"))
      ))
      Parsing (input) should produce (root(p("aa"), body, p("bb")))
    }
  }

  it should "parse a full directive spec with all elements present and attributes on multiple lines" in {
    new FullDirectiveSpec with BlockParser {
      val input = """aa
        |
        |@:dir { foo 
        |  strAttr=str 
        |  intAttr=7 
        |}
        |
        |1 {{config.ref}} 2
        |
        |@:@
        |
        |bb""".stripMargin
      val body = BlockSequence(List(
        p("foo:str:7"),
        p(txt("1 value 2"))
      ))
      Parsing (input) should produce (root(p("aa"), body, p("bb")))
    }
  }

  it should "parse a full directive spec with all optional elements missing" in {
    new FullDirectiveSpec with BlockParser {
      val input = """aa
        |
        |@:dir { foo }
        | 
        |1 {{config.ref}} 2
        |
        |@:@
        |
        |bb""".stripMargin
      val body = BlockSequence(List(
        p("foo:..:0"),
        p(txt("1 value 2"))
      ))
      Parsing (input) should produce (root(p("aa"), body, p("bb")))
    }
  }

  it should "detect a full directive spec with one required attribute and the body missing" in {
    new FullDirectiveSpec with BlockParser {
      val input = """aa
        |
        |@:dir { strAttr=str }
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'dir': required default attribute is missing, required body is missing"
      Parsing (input) should produce (root(p("aa"), invalid("@:dir { strAttr=str }",msg), p("bb")))
    }
  }

  it should "parse a directive with a body and parser access" in {
    new BlockParser with DirectiveWithParserAccess {
      val input = """aa
        |
        |@:dir
        |some {{config.ref}} text
        |@:@
        |
        |bb""".stripMargin
      val body = BlockSequence(List(p(txt("e value text"))))
      Parsing (input) should produce (root(p("aa"), body, p("bb")))
    }
  }

  it should "parse a directive with a required default body and cursor access" in {
    new BlockParser with DirectiveWithContextAccess {
      val input = """aa
        |
        |@:dir
        |text
        |@:@
        |
        |bb""".stripMargin
      Parsing (input) should produce (root(p("aa"), p("text/"), p("bb")))
    }
  }
  
  it should "detect a directive with an unknown name" in {
    new BlockParser with OptionalNamedAttribute {
      val input = """aa
        |
        |@:foo { name=foo }
        |
        |bb""".stripMargin
      val msg = "One or more errors processing directive 'foo': No block directive registered with name: foo"
      Parsing (input) should produce (root(p("aa"), invalid("@:foo { name=foo }",msg), p("bb")))
    }
  }
  
  
}
