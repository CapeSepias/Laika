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

package laika.parse.markdown.html

import org.scalatest.FlatSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.matchers.ShouldMatchers

import laika.api.Render
import laika.parse.markdown.html.HTMLElements._
import laika.render.HTML
import laika.tree.Elements.Element
import laika.tree.helper.ModelBuilder
 

class VerbatimHTMLRendererSpec extends FlatSpec 
                               with ShouldMatchers
                               with ModelBuilder 
                               with HTMLModelBuilder {
  
    
  def render (elem: Element) = Render as HTML using VerbatimHTML from elem toString 
   
  
  "The Verbatim HTML renderer" should "render an HTML character reference unescaped" in {
    val elem = p(txt("some "), charRef("&amp;"), txt(" & text"))
    render (elem) should be ("<p>some &amp; &amp; text</p>") 
  }
  
  it should "render an HTML comment with content unescaped" in {
    val elem = p(txt("some "), comment(" yes < no "), txt(" & text"))
    render (elem) should be ("<p>some <!-- yes < no --> &amp; text</p>") 
  }
  
  it should "render an HTML end tag unescaped" in {
    val elem = p(txt("some "), endTag("orphan"), txt(" & text"))
    render (elem) should be ("<p>some </orphan> &amp; text</p>") 
  }
  
  it should "render an HTML start tag without attributes unescaped" in {
    val elem = p(txt("some "), startTag("hr"), txt(" & text"))
    render (elem) should be ("<p>some <hr> &amp; text</p>") 
  }
  
  it should "render an HTML start tag with one attribute unescaped" in {
    val elem = p(txt("some "), startTag("hr", ("foo",txt("bar"))), txt(" & text"))
    render (elem) should be ("""<p>some <hr foo="bar"> &amp; text</p>""") 
  }
  
  it should "render an HTML start tag with two attributes unescaped" in {
    val elem = p(txt("some "), startTag("hr", ("foo",txt("bar")), ("bar",txt("foo"))), txt(" & text"))
    render (elem) should be ("""<p>some <hr foo="bar" bar="foo"> &amp; text</p>""") 
  }
  
  it should "render an empty HTML tag without attributes unescaped" in {
    val elem = p(txt("some "), emptyTag("br"), txt(" & text"))
    render (elem) should be ("<p>some <br/> &amp; text</p>") 
  }
  
  it should "render an empty HTML tag with one attribute unescaped" in {
    val elem = p(txt("some "), emptyTag("br", ("foo",txt("bar"))), txt(" & text"))
    render (elem) should be ("""<p>some <br foo="bar"/> &amp; text</p>""") 
  }
  
  it should "render an HTML element without attributes unescaped" in {
    val elem = p(txt("some "), element(startTag("span"), txt("inner")), txt(" & text"))
    render (elem) should be ("<p>some <span>inner</span> &amp; text</p>") 
  }
  
  it should "render an HTML element with one attribute unescaped" in {
    val elem = p(txt("some "), element(startTag("span", ("foo",txt("bar"))), txt("inner")), txt(" & text"))
    render (elem) should be ("""<p>some <span foo="bar">inner</span> &amp; text</p>""") 
  }
  
  it should "render two nested HTML elements unescaped" in {
    val inner = element(startTag("span"), txt("inner"))
    val outer = element(startTag("span"), txt("aaa "), inner, txt(" bbb"))
    val elem = p(txt("some "), outer, txt(" & text"))
    render (elem) should be ("<p>some <span>aaa <span>inner</span> bbb</span> &amp; text</p>") 
  }
  
  it should "render an HTML attribute with the value in single quotes" in {
    val attr = HTMLAttribute("foo", List(txt("bar")), Some('\''))
    val tag = HTMLStartTag("x", List(attr))
    render (tag) should be ("<x foo='bar'>") 
  } 
  
  it should "render an HTML attribute with an unquoted value" in {
    val attr = HTMLAttribute("foo", List(txt("bar")), None)
    val tag = HTMLStartTag("x", List(attr))
    render (tag) should be ("<x foo=bar>") 
  } 
  
  it should "render an HTML attribute without value" in {
    val attr = HTMLAttribute("foo", Nil, None)
    val tag = HTMLStartTag("x", List(attr))
    render (tag) should be ("<x foo>") 
  } 
  
  it should "render an HTML block unescaped" in {
    val inner = element(startTag("span"), txt("inner"))
    val outer = element(startTag("span"), txt("aaa "), inner, txt(" bbb"))
    val elem = HTMLBlock(outer)
    render (elem) should be ("<span>aaa <span>inner</span> bbb</span>") 
  }
  
  
}