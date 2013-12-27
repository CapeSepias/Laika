/*
 * Copyright 2013 the original author or authors.
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
  
package laika.parse.markdown

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import laika.api.Parse
import laika.tree.helper.ModelBuilder

class APISpec extends FlatSpec 
                 with Matchers
                 with ModelBuilder {
  
  
  trait BlockDirectives {
    import laika.directive.Directives.Blocks
    import Blocks.Combinators._
    import Blocks.Converters._
    import laika.util.Builders._
    import laika.directive.Directives.Default
    
    val directives = List(
      Blocks.create("oneArg")(attribute(Default) map p),
      Blocks.create("twoArgs")((attribute(Default) ~ attribute("name")) { (arg1,arg2) => p(arg1+arg2) })
    )
  }
  
  trait SpanDirectives {
    import laika.directive.Directives.Spans
    import Spans.Combinators._
    import Spans.Converters._
    import laika.util.Builders._
    import laika.directive.Directives.Default
    
    val directives = List(
      Spans.create("oneArg")(attribute(Default) map txt),
      Spans.create("twoArgs")((attribute(Default) ~ attribute("name")) { (arg1,arg2) => txt(arg1+arg2) })
    )
  }
  
  "The API" should "support the registration of block directives" in {
    new BlockDirectives {
      val input = """@:oneArg arg.
        |
        |@:twoArgs arg1 name=arg2.""".stripMargin
      (Parse as (Markdown withBlockDirectives (directives:_*)) fromString input).content should be (root (p("arg"),p("arg1arg2")))
    }
  }
  
  it should "ignore the registration of block directives when run in strict mode" in {
    new BlockDirectives {
      val input = """@:oneArg arg.
        |
        |@:twoArgs arg1 name=arg2.""".stripMargin
      (Parse as (Markdown withBlockDirectives (directives:_*) strict) fromString input).content should be (root (p("@:oneArg arg."),p("@:twoArgs arg1 name=arg2.")))
    }
  }
  
  it should "support the registration of span directives" in {
    new SpanDirectives {
      val input = """one @:oneArg arg. two @:twoArgs arg1 name=arg2. three""".stripMargin
      (Parse as (Markdown withSpanDirectives (directives:_*)) fromString input).content should be (root (p("one arg two arg1arg2 three")))
    }
  }
  
  it should "ignore the registration of span directives when run in strict mode" in {
    new SpanDirectives {
      val input = """one @:oneArg arg. two @:twoArgs arg1 name=arg2. three"""
      (Parse as (Markdown withSpanDirectives (directives:_*) strict) fromString input).content should be (root (p("one @:oneArg arg. two @:twoArgs arg1 name=arg2. three")))
    }
  }
  

}