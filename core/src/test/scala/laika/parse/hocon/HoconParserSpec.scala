/*
 * Copyright 2012-2019 the original author or authors.
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

package laika.parse.hocon

import laika.parse.helper.{ParseResultHelpers, StringParserHelpers}
import laika.parse.hocon.HoconParsers._
import org.scalatest.{Matchers, WordSpec}

/**
  * @author Jens Halm
  */
class HoconParserSpec extends WordSpec with Matchers with ParseResultHelpers with StringParserHelpers {

  def f (key: String, value: String): BuilderField = BuilderField(key, StringValue(value))
  
  private val nestedObject = BuilderField("obj", ObjectBuilderValue(Seq(
    BuilderField("inner", StringValue("xx")),
    BuilderField("num", DoubleValue(9.5))
  )))
  
  private val arrayProperty = BuilderField("arr", ArrayBuilderValue(Seq(
    LongValue(1), LongValue(2), StringValue("bar")
  )))
  
  "The object parser" should {

    "parse an empty root object that is not enclosed in braces" in {
      Parsing (" ") using rootObject should produce (ObjectBuilderValue(Nil))
    }

    "parse a root object with two properties that is not enclosed in braces" in {
      Parsing (""" "a": "foo", "b": "bar" """.stripMargin) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"),f("b","bar"))))
    }

    "parse a root object with all property types that is not enclosed in braces" in {
      val input =
        """"str": "foo",
          |"int": 27,
          |"null": null,
          |"bool": true,
          |"arr": [ 1, 2, "bar" ],
          |"obj": { "inner": "xx", "num": 9.5 }""".stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(
        BuilderField("str", StringValue("foo")),
        BuilderField("int", LongValue(27)),
        BuilderField("null", NullValue),
        BuilderField("bool", BooleanValue(true)),
        arrayProperty,
        nestedObject
      )))
    }

    "parse a root object with two properties that use '=' instead of ':'" in {
      Parsing (""" "a" = "foo", "b" = "bar" """.stripMargin) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"),f("b","bar"))))
    }

    "parse an object property without separator" in {
      val input =
        """"a": "foo", 
          |"obj" { 
          |  "inner": "xx", 
          |  "num": 9.5 
          |} """.stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), nestedObject)))
    }

    "parse an object property with a trailing comma" in {
      val input =
        """"a": "foo", 
          |"obj" = { 
          |  "inner": "xx", 
          |  "num": 9.5,
          |} """.stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), nestedObject)))
    }

    "parse an array property with a trailing comma" in {
      val input =
        """"a": "foo", 
          |"arr": [ 1, 2, "bar", ]""".stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), arrayProperty)))
    }

    "parse an array property with elements separated by newline characters" in {
      val input =
        """"a": "foo", 
          |"arr": [ 
          |  1 
          |  2 
          |  "bar"
          |]""".stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), arrayProperty)))
    }

    "parse a root object with members separated by newline characters" in {
      Parsing (
        """"a": "foo"
          |"b": "bar" 
          |"c": "baz" """.stripMargin) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"),f("b","bar"),f("c","baz"))))
    }

    "parse a root object with members separated by two newline characters" in {
      Parsing (
        """"a": "foo"
          |
          |"b": "bar"
          | 
          |"c": "baz" """.stripMargin) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"),f("b","bar"),f("c","baz"))))
    }

    "parse a multiline string property" in {
      val input =
        """"a": "foo", 
          |"s": +++Line 1
          | Line 2
          | Line 3+++""".stripMargin.replaceAllLiterally("+++", "\"\"\"")
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), f("s", "Line 1\n Line 2\n Line 3"))))
    }

    "ignore escapes in a multiline string property" in {
      val input =
        """"a": "foo", 
          |"s": +++Word 1 \n Word 2+++""".stripMargin.replaceAllLiterally("+++", "\"\"\"")
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), f("s", "Word 1 \\n Word 2"))))
    }

    "parse an object with unquoted keys" in {
      val input =
        """a: "foo", 
          |arr: [ 1, 2, "bar" ]""".stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), arrayProperty)))
    }

    "parse an object with unquoted string values" in {
      val input =
        """"a": foo, 
          |"arr": [ 1, 2, bar ]""".stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a","foo"), arrayProperty)))
    }
    
  }
  
  "The concatenated value parser" should {
    
    "parse simple values containing booleans" in {
      val input = "a = true is false"
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(
        BuilderField("a", ConcatValue(BooleanValue(true), Seq(ConcatPart(" ", StringValue("is")), ConcatPart(" ", BooleanValue(false)))))
      )))
    }

    "parse simple values containing numbers" in {
      val input = "a = 9 is 7"
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(
        BuilderField("a", ConcatValue(LongValue(9), Seq(ConcatPart(" ", StringValue("is")), ConcatPart(" ", LongValue(7)))))
      )))
    }

    "parse object values on a single line" in {
      val input = """a = { "inner": "xx", "num": 9.5 } { "inner": "xx", "num": 9.5 }"""
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(
        BuilderField("a", ConcatValue(nestedObject.value, Seq(ConcatPart(" ", nestedObject.value))))
      )))
    }

    "parse object values spanning multiple lines" in {
      val input = """a = { 
                    |  "inner": "xx", 
                    |  "num": 9.5
                    |} { 
                    |  "inner": "xx", 
                    |  "num": 9.5
                    |}""".stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(
        BuilderField("a", ConcatValue(nestedObject.value, Seq(ConcatPart(" ", nestedObject.value))))
      )))
    }

    "parse array values on a single line" in {
      val input = """a = [ 1, 2, "bar", ] [ 1, 2, "bar", ]"""
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(
        BuilderField("a", ConcatValue(arrayProperty.value, Seq(ConcatPart(" ", arrayProperty.value))))
      )))
    }

    "parse array values spanning multiple lines" in {
      val input = """a = [ 
                    | 1
                    | 2 
                    | "bar"
                    |] [ 
                    | 1 
                    | 2
                    | "bar"
                    |]""".stripMargin
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(
        BuilderField("a", ConcatValue(arrayProperty.value, Seq(ConcatPart(" ", arrayProperty.value))))
      )))
    }

  }
  
  "The concatenated key parser" should {
    
    "parse a concatenated key consisting of unquoted strings" in {
      val input = """a b c = foo"""
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a b c","foo"))))
    }

    "parse a concatenated key consisting of unquoted and quoted strings" in {
      val input = """a "b" c = foo"""
      Parsing (input) using rootObject should produce (ObjectBuilderValue(Seq(f("a b c","foo"))))
    }
    
  }

}
