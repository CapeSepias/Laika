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

package laika.parse.rst

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers
import laika.parse.helper.DefaultParserHelpers
import laika.parse.helper.ParseResultHelpers
import laika.tree.Elements.Span
import laika.tree.helper.ModelBuilder
import laika.parse.rst.Elements._
import laika.tree.Elements._
import laika.parse.rst.TextRoles.TextRole
import laika.parse.rst.Directives.DirectivePart
     
class ListParsersSpec extends FlatSpec 
                        with ShouldMatchers 
                        with BlockParsers 
                        with InlineParsers
                        with ParseResultHelpers 
                        with DefaultParserHelpers[Document] 
                        with ModelBuilder {

  
  val defaultParser: Parser[Document] = document
  
  
  val blockDirectives: Map[String, DirectivePart[Block]] = Map.empty
  val spanDirectives: Map[String, DirectivePart[Span]] = Map.empty
  val textRoles: Map[String, TextRole] = Map.empty
  
  
  def fl (fields: Field*) = FieldList(fields.toList)
  
  def field (name: String, blocks: Block*) = Field(List(Text(name)), blocks.toList)
  
  
  def oli (name: String, value: Block*) = OptionListItem(List(ProgramOption(name, None)), value.toList)

  def oli (name: String, value: String) = OptionListItem(List(ProgramOption(name, None)), List(fc(value)))

  def oli (name: String, argDelim: String, arg: String, value: String) = 
    OptionListItem(List(ProgramOption(name, Some(OptionArgument(arg,argDelim)))), List(fc(value)))
  
  def optL (items: OptionListItem*) = OptionList(items.toList)
  
  
  
  "The unordered list parser" should "parse items that are not separated by blank lines" in {
    val input = """* aaa
      |* bbb
      |* ccc""".stripMargin
    Parsing (input) should produce (doc( ul( li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items that are separated by blank lines" in {
    val input = """* aaa
      |
      |* bbb
      |
      |* ccc""".stripMargin
    Parsing (input) should produce (doc( ul( li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items starting with a '+' the same way as those starting with a '*'" in {
    val input = """+ aaa
      |+ bbb
      |+ ccc""".stripMargin
    Parsing (input) should produce (doc( ul( li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items starting with a '-' the same way as those starting with a '*'" in {
    val input = """- aaa
      |- bbb
      |- ccc""".stripMargin
    Parsing (input) should produce (doc( ul( li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items containing multiple paragraphs in a single item" in {
    val input = """* aaa
      |   
      |  bbb
      |  bbb
      |
      |* ccc
      |
      |* ddd""".stripMargin
    Parsing (input) should produce (doc( ul( li( p("aaa"), p("bbb\nbbb")), li("ccc"), li("ddd"))))
  }
  
  it should "parse nested items indented by spaces" in {
    val input = """* aaa
                  |
                  |  * bbb
                  |
                  |    * ccc""".stripMargin
    val list3 = ul( li("ccc"))
    val list2 = ul( li( p("bbb"), list3))
    val list1 = ul( li( p("aaa"), list2))
    Parsing (input) should produce (doc(list1))
  }
  
  
  "The ordered list parser" should "parse items with arabic enumeration style" in {
    val input = """1. aaa
      |2. bbb
      |3. ccc""".stripMargin
    Parsing (input) should produce (doc( ol( li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items with lowercase alphabetic enumeration style" in {
    val input = """a. aaa
      |b. bbb
      |c. ccc""".stripMargin
    Parsing (input) should produce (doc( ol(LowerAlpha,"",".", 1, li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items with uppercase alphabetic enumeration style" in {
    val input = """A. aaa
      |B. bbb
      |C. ccc""".stripMargin
    Parsing (input) should produce (doc( ol(UpperAlpha,"",".", 1, li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items with lowercase Roman enumeration style" in {
    val input = """i. aaa
      |ii. bbb
      |iii. ccc""".stripMargin
    Parsing (input) should produce (doc( ol(LowerRoman,"",".", 1, li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items with uppercase Roman enumeration style" in {
    val input = """I. aaa
      |II. bbb
      |III. ccc""".stripMargin
    Parsing (input) should produce (doc( ol(UpperRoman,"",".", 1, li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "keep the right start value for arabic enumeration style" in {
    val input = """4. aaa
      |5. bbb""".stripMargin
    Parsing (input) should produce (doc( ol(Arabic,"",".", 4, li("aaa"), li("bbb"))))
  }
  
  it should "keep the right start value for lowercase alphabetic enumeration style" in {
    val input = """d. aaa
      |e. bbb""".stripMargin
    Parsing (input) should produce (doc( ol(LowerAlpha,"",".", 4, li("aaa"), li("bbb"))))
  }
  
  it should "keep the right start value for uppercase alphabetic enumeration style" in {
    val input = """D. aaa
      |E. bbb""".stripMargin
    Parsing (input) should produce (doc( ol(UpperAlpha,"",".", 4, li("aaa"), li("bbb"))))
  }
  
  it should "keep the right start value for lowercase Roman enumeration style" in {
    val input = """iv. aaa
      |v. bbb""".stripMargin
    Parsing (input) should produce (doc( ol(LowerRoman,"",".", 4, li("aaa"), li("bbb"))))
  }
  
  it should "keep the right start value for uppercase Roman enumeration style" in {
    val input = """IV. aaa
      |V. bbb""".stripMargin
    Parsing (input) should produce (doc( ol(UpperRoman,"",".", 4, li("aaa"), li("bbb"))))
  }
  
  it should "parse items suffixed by right-parenthesis" in {
    val input = """1) aaa
      |2) bbb
      |3) ccc""".stripMargin
    Parsing (input) should produce (doc( ol(Arabic,"",")", 1, li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items surrounded by parenthesis" in {
    val input = """(1) aaa
      |(2) bbb
      |(3) ccc""".stripMargin
    Parsing (input) should produce (doc( ol(Arabic,"(",")", 1, li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items that are separated by blank lines" in {
    val input = """1. aaa
      |
      |2. bbb
      |
      |3. ccc""".stripMargin
    Parsing (input) should produce (doc( ol( li("aaa"), li("bbb"), li("ccc"))))
  }
  
  it should "parse items containing multiple paragraphs in a single item" in {
    val input = """1. aaa
      |   
      |   bbb
      |   bbb
      |
      |2. ccc
      |
      |3. ddd""".stripMargin
    Parsing (input) should produce (doc( ol( li( p("aaa"), p("bbb\nbbb")), li("ccc"), li("ddd"))))
  }
  
  it should "parse nested items indented by spaces" in {
    val input = """1. aaa
                  |
                  |   1. bbb
                  |
                  |      1. ccc""".stripMargin
    val list3 = ol( li("ccc"))
    val list2 = ol( li( p("bbb"), list3))
    val list1 = ol( li( p("aaa"), list2))
    Parsing (input) should produce (doc(list1))
  }
  
  it should "parse items with different enumeration patterns into separate lists" in {
    val input = """1. aaa
      |
      |2. bbb
      |
      |1) ccc
      |
      |2) ddd""".stripMargin
    Parsing (input) should produce (doc( ol( li("aaa"), li("bbb")), ol(Arabic,"",")", 1, li("ccc"), li("ddd"))))
  }
  
  
  
  "The definition list parser" should "parse items that are not separated by blank lines" in {
    val input = """term 1
      | aaa
      |term 2
      | bbb""".stripMargin
    Parsing (input) should produce (doc( dl( di("term 1", fc("aaa")), di("term 2", fc("bbb")))))
  }
  
  it should "parse items that are separated by blank lines" in {
    val input = """term 1
      | aaa
      |
      |term 2
      | bbb""".stripMargin
    Parsing (input) should produce (doc( dl( di("term 1", fc("aaa")), di("term 2", fc("bbb")))))
  }
  
  it should "parse a term with a classifier" in {
    val input = """term 1
      | aaa
      |
      |term 2 : classifier
      | bbb""".stripMargin
    Parsing (input) should produce (doc( dl( di("term 1", fc("aaa")), di(List(txt("term 2 "), Classifier(List(txt("classifier")))), fc("bbb")))))
  }
  
  it should "parse items containing multiple paragraphs in a single item" in {
    val input = """term 1
      |  aaa
      |  aaa
      |
      |  bbb
      |
      |term 2
      |  ccc""".stripMargin
    Parsing (input) should produce (doc( dl( di("term 1", p("aaa\naaa"), p("bbb")), di("term 2", fc("ccc")))))
  }
  
  it should "support inline markup in the term" in {
    val input = """term *em*
      | aaa
      |
      |term 2
      | bbb""".stripMargin
    Parsing (input) should produce (doc( dl( di(List(txt("term "), em(txt("em"))), fc("aaa")), di("term 2", fc("bbb")))))
  }
  
  
  
  "The field list parser" should "parse a list with all bodies on the same line as the name" in {
    val input = """:name1: value1
      |:name2: value2
      |:name3: value3""".stripMargin
    Parsing (input) should produce (doc( fl( field("name1", fc("value1")), field("name2", fc("value2")), field("name3", fc("value3")))))
  }
  
  it should "parse a list with bodies spanning multiple lines" in {
    val input = """:name1: line1a
      |  line1b
      |:name2: line2a
      |  line2b""".stripMargin
    Parsing (input) should produce (doc( fl( field("name1", fc("line1a\nline1b")), field("name2", fc("line2a\nline2b")))))
  }
  
  it should "parse a list with bodies spanning multiple blocks" in {
    val input = """:name1: line1a
      |  line1b
      |
      |  line1c
      |  line1d
      |:name2: line2a
      |  line2b""".stripMargin
    Parsing (input) should produce (doc( fl( field("name1", p("line1a\nline1b"), p("line1c\nline1d")), field("name2", fc("line2a\nline2b")))))
  }
  
  
  "The option list parser" should "parse a list with short posix options" in {
    val input = """-a  Option1
      |-b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("-a", "Option1"), oli("-b", "Option2"))))
  }
  
  it should "parse a list with long posix options" in {
    val input = """--aaaa  Option1
      |--bbbb  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("--aaaa", "Option1"), oli("--bbbb", "Option2"))))
  }
  
  it should "parse a list with short GNU-style options" in {
    val input = """+a  Option1
      |+b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("+a", "Option1"), oli("+b", "Option2"))))
  }
  
  it should "parse a list with short DOS-style options" in {
    val input = """/a  Option1
      |/b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("/a", "Option1"), oli("/b", "Option2"))))
  }
  
  it should "parse an option argument separated by a space" in {
    val input = """-a FILE  Option1
      |-b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("-a", " ", "FILE", "Option1"), oli("-b", "Option2"))))
  }
  
  it should "parse an option argument separated by '='" in {
    val input = """-a=FILE  Option1
      |-b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("-a", "=", "FILE", "Option1"), oli("-b", "Option2"))))
  }
  
  it should "parse a description starting on the next line" in {
    val input = """-a
      |    Option1
      |-b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("-a", "Option1"), oli("-b", "Option2"))))
  }
  
  it should "parse a block of options with blank lines between them" in {
    val input = """-a  Option1
      |
      |-b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("-a", "Option1"), oli("-b", "Option2"))))
  }
  
  it should "parse a description containing multiple paragraphs" in {
    val input = """-a  Line1
      |                Line2
      |
      |                Line3
      |
      |-b  Option2""".stripMargin
    Parsing (input) should produce (doc( optL( oli("-a", p("Line1\nLine2"), p("Line3")), oli("-b", "Option2"))))
  }
  
  
  
  "The line block parser" should "parse a block with out continuation or indentation" in {
    val input = """|| Line1
      || Line2
      || Line3""".stripMargin
    Parsing (input) should produce (doc( lb( line("Line1"), line("Line2"), line("Line3"))))
  }
  
  it should "parse a block with a continuation line" in {
    val input = """|| Line1
      |  Line2
      || Line3
      || Line4""".stripMargin
    Parsing (input) should produce (doc( lb( line("Line1\nLine2"), line("Line3"), line("Line4"))))
  }
  
  it should "parse a nested structure (pointing right)" in {
    val input = """|| Line1
      ||   Line2
      ||     Line3
      ||   Line4
      || Line5""".stripMargin
    Parsing (input) should produce (doc( lb( line("Line1"), lb(line("Line2"), lb(line("Line3")), line("Line4")), line("Line5"))))
  }
  
  it should "parse a nested structure (pointing left)" in {
    val input = """||     Line1
      ||   Line2
      || Line3
      ||   Line4
      ||     Line5""".stripMargin
    Parsing (input) should produce (doc( lb( lb( lb(line("Line1")), line("Line2")), line("Line3"), lb(line("Line4"), lb(line("Line5"))))))
  }
  
  
  
}