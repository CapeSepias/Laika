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
import laika.ast.Path.Root
import laika.ast._
import laika.ast.helper.ModelBuilder
import laika.parse.Parser
import laika.parse.directive.TemplateParsers
import laika.parse.helper.{DefaultParserHelpers, ParseResultHelpers}
import laika.rewrite.TemplateRewriter
import org.scalatest.{FlatSpec, Matchers}

class TemplateDirectiveAPISpec extends FlatSpec
                          with Matchers
                          with ModelBuilder {

  
  object DirectiveSetup {
    import Templates.dsl._

    trait Empty {
      val directive = Templates.create("dir")(Templates.dsl.empty(TemplateString("foo")))
    }
    
    trait RequiredDefaultAttribute {
      val directive = Templates.create("dir") { attribute(Default) map (TemplateString(_)) }
    }
    
    trait OptionalDefaultAttribute {
      val directive = Templates.create("dir") { 
        attribute(Default, positiveInt).optional map (num => TemplateString(num.map(_.toString).getOrElse("<>"))) 
      }
    }
    
    trait RequiredNamedAttribute {
      val directive = Templates.create("dir") { attribute("name") map (TemplateString(_)) }
    }
    
    trait OptionalNamedAttribute {
      val directive = Templates.create("dir") { 
        attribute("name", positiveInt).optional map (num => TemplateString(num.map(_.toString).getOrElse("<>"))) 
      }
    }
    
    trait RequiredDefaultBody {
      val directive = Templates.create("dir") { body map (TemplateSpanSequence(_)) }
    }
    
    trait FullDirectiveSpec {
      val directive = Templates.create("dir") {
        (attribute(Default) ~ attribute("strAttr").optional ~ attribute("intAttr", positiveInt).optional ~
        body).map {
          case defAttr ~ strAttr ~ intAttr ~ defBody => 
            val sum = intAttr.getOrElse(0)
            val str = defAttr + ":" + strAttr.getOrElse("..") + ":" + sum
            TemplateSpanSequence(TemplateString(str) +: defBody)
        }
      }
    }
    
    trait DirectiveWithParserAccess {
      val directive = Templates.create("dir") { 
        (body(string) ~ parser).map {
          case body ~ parser => TemplateSpanSequence(parser(body.drop(3)))
        }
      }
    }
    
    trait DirectiveWithContextAccess {
      val directive = Templates.create("dir") { 
        (body(string) ~ cursor).map {
          case body ~ cursor => TemplateString(body + cursor.target.path)
        }
      }
    }
    
  }
  
  trait TemplateParser extends ParseResultHelpers
                          with DefaultParserHelpers[TemplateRoot] {
    
    val directive: Templates.Directive

    val templateParsers = new TemplateParsers(Map(directive.name -> directive))
    
    val defaultParser: Parser[TemplateRoot] = templateParsers.templateSpans ^^ { spans =>
      val root = TemplateRoot(spans)
      TemplateRewriter.rewriteRules(DocumentCursor(
        Document(Root, RootElement(Seq(root)), config = ConfigFactory.parseString("ref: value"))
      )).rewriteBlock(root).asInstanceOf[TemplateRoot]
    }
    
    def invalid (input: String, error: String): InvalidSpan = InvalidElement(error, input).asSpan
        
    def tss (spans: TemplateSpan*) = TemplateSpanSequence(spans)

  }
  

  import DirectiveSetup._

  "The directive parser" should "parse an empty directive" in {
    new Empty with TemplateParser {
      Parsing ("aa @:dir bb") should produce (tRoot(tt("aa "), tt("foo"), tt(" bb")))
    }
  }
  
  it should "parse a directive with one required default string attribute" in {
    new RequiredDefaultAttribute with TemplateParser {
      Parsing ("aa @:dir { foo } bb") should produce (tRoot(tt("aa "), tt("foo"), tt(" bb")))
    }
  }
  
  it should "detect a directive with a missing required default attribute" in {
    new RequiredDefaultAttribute with TemplateParser {
      val msg = "One or more errors processing directive 'dir': required default attribute is missing"
      Parsing ("aa @:dir bb") should produce (tRoot(tt("aa "), tElem(invalid("@:dir",msg)), tt(" bb")))
    }
  }
  
  it should "parse a directive with an optional default int attribute" in {
    new OptionalDefaultAttribute with TemplateParser {
      Parsing ("aa @:dir { 5 } bb") should produce (tRoot(tt("aa "), tt("5"), tt(" bb")))
    }
  }
  
  it should "detect a directive with an optional invalid default int attribute" in {
    new OptionalDefaultAttribute with TemplateParser {
      val msg = "One or more errors processing directive 'dir': error converting default attribute: not an integer: foo"
      Parsing ("aa @:dir { foo } bb") should produce (tRoot(tt("aa "), tElem(invalid("@:dir { foo }",msg)), tt(" bb")))
    }
  }
  
  it should "parse a directive with a missing optional default int attribute" in {
    new OptionalDefaultAttribute with TemplateParser {
      Parsing ("aa @:dir bb") should produce (tRoot(tt("aa "), tt("<>"), tt(" bb")))
    }
  }
  
  it should "parse a directive with one required named string attribute" in {
    new RequiredNamedAttribute with TemplateParser {
      Parsing ("aa @:dir { name=foo } bb") should produce (tRoot(tt("aa "), tt("foo"), tt(" bb")))
    }
  }
  
  it should "parse a directive with a named string attribute value in quotes" in {
    new RequiredNamedAttribute with TemplateParser {
      Parsing ("""aa @:dir { name="foo bar" } bb""") should produce (tRoot(tt("aa "), tt("foo bar"), tt(" bb")))
    }
  }
  
  it should "detect a directive with a missing required named attribute" in {
    new RequiredNamedAttribute with TemplateParser {
      val msg = "One or more errors processing directive 'dir': required attribute with name 'name' is missing"
      Parsing ("aa @:dir bb") should produce (tRoot(tt("aa "), tElem(invalid("@:dir",msg)), tt(" bb")))
    }
  }
  
  it should "parse a directive with an optional named int attribute" in {
    new OptionalNamedAttribute with TemplateParser {
      Parsing ("aa @:dir { name=5 } bb") should produce (tRoot(tt("aa "), tt("5"), tt(" bb")))
    }
  }
  
  it should "detect a directive with an optional invalid named int attribute" in {
    new OptionalNamedAttribute with TemplateParser {
      val msg = "One or more errors processing directive 'dir': error converting attribute with name 'name': not an integer: foo"
      Parsing ("aa @:dir { name=foo } bb") should produce (tRoot(tt("aa "), tElem(invalid("@:dir { name=foo }",msg)), tt(" bb")))
    }
  }
  
  it should "parse a directive with a missing optional named int attribute" in {
    new OptionalNamedAttribute with TemplateParser {
      val msg = "One or more errors processing directive 'dir': required default attribute is missing"
      Parsing ("aa @:dir bb") should produce (tRoot(tt("aa "), tt("<>"), tt(" bb")))
    }
  }
  
  it should "parse a directive with a body" in {
    new RequiredDefaultBody with TemplateParser {
      val body = tss(tt(" some "), tt("value"), tt(" text "))
      Parsing ("aa @:dir some {{config.ref}} text @:@ bb") should produce (tRoot(tt("aa "), body, tt(" bb")))
    }
  }
  
  it should "support a directive with a nested pair of braces" in {
    new RequiredDefaultBody with TemplateParser {
      val body = tss(tt(" some {ref} text "))
      Parsing ("aa @:dir some {ref} text @:@ bb") should produce (tRoot(tt("aa "), body, tt(" bb")))
    }
  }
  
  it should "detect a directive with a missing body" in {
    new RequiredDefaultBody with TemplateParser {
      val msg = "One or more errors processing directive 'dir': required body is missing"
      Parsing ("aa @:dir bb") should produce (tRoot(tt("aa "), tElem(invalid("@:dir",msg)), tt(" bb")))
    }
  }
  
  it should "parse a full directive spec with all elements present" in {
    new FullDirectiveSpec with TemplateParser {
      val body = tss(
        tt("foo:str:7"), 
        tt(" 1 "), tt("value"), tt(" 2 ")
      )
      Parsing ("aa @:dir { foo strAttr=str intAttr=7 } 1 {{config.ref}} 2 @:@ bb") should produce (tRoot(tt("aa "), body, tt(" bb")))
    }
  }

  it should "parse a full directive spec with all elements present with attributes spanning two lines" in {
    new FullDirectiveSpec with TemplateParser {
      val body = tss(
        tt("foo:str:7"),
        tt(" 1 "), tt("value"), tt(" 2 ")
      )
      Parsing ("aa @:dir { foo strAttr=str\nintAttr=7 } 1 {{config.ref}} 2 @:@ bb") should produce (tRoot(tt("aa "), body, tt(" bb")))
    }
  }
  
  it should "parse a full directive spec with all optional elements missing" in {
    new FullDirectiveSpec with TemplateParser {
      val body = tss(
        tt("foo:..:0"), 
        tt(" 1 "), tt("value"), tt(" 2 ")
      )
      Parsing ("aa @:dir { foo } 1 {{config.ref}} 2 @:@ bb") should produce (tRoot(tt("aa "), body, tt(" bb")))
    }
  }
  
  it should "detect a full directive spec with all one required attribute and one required body missing" in {
    new FullDirectiveSpec with TemplateParser {
      val msg = "One or more errors processing directive 'dir': required default attribute is missing, required body is missing"
      Parsing ("aa @:dir { strAttr=str } bb") should produce (tRoot(tt("aa "), tElem(invalid("@:dir { strAttr=str }",msg)), tt(" bb")))
    }
  }
  
  it should "parse a directive with a required default body and parser access" in {
    new DirectiveWithParserAccess with TemplateParser {
      val body = tss(tt("me "), tt("value"), tt(" text "))
      Parsing ("aa @:dir some {{config.ref}} text @:@ bb") should produce (tRoot(tt("aa "), body, tt(" bb")))
    }
  }
  
  it should "parse a directive with a required default body and cursor access" in {
    new DirectiveWithContextAccess with TemplateParser {
      Parsing ("aa @:dir text @:@ bb") should produce (tRoot(tt("aa "), tt(" text /"), tt(" bb")))
    }
  }
  
  it should "detect a directive with an unknown name" in {
    new OptionalNamedAttribute with TemplateParser {
      val msg = "One or more errors processing directive 'foo': No template directive registered with name: foo"
      Parsing ("aa @:foo name=foo. bb") should produce (tRoot(tt("aa "), tElem(invalid("@:foo name=foo.",msg)), tt(" bb")))
    }
  }
  
  
}
