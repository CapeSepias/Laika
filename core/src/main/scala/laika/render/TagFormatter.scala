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

package laika.render

import laika.ast._

import scala.collection.mutable.StringBuilder

/** Base type for writers that produce tag-based output formats like XML 
 *  or HTML.
 *  Extends the base `TextWriter` and adds methods for writing text
 *  with special characters as entities and for conveniently writing
 *  tags with attributes.
 * 
 *  @param renderChild the function to use for rendering child elements
 *  @param elementStack the stack of parent elements of this formatter in recursive rendering
 *  @param indentation the level of indentation for this formatter
 *                    
 *  @author Jens Halm
 */
abstract class TagFormatter[Rep <: BaseFormatter[Rep]] (renderChild: (Rep, Element) => String,
                                                        elementStack: Seq[Element],
                                                        indentation: Indentation) extends BaseFormatter[Rep](renderChild, elementStack, indentation) { this: Rep =>
  
  /** Renders the specified string on the same line, 
   *  with all special XML/HTML characters converted to entities.
   */
  def text (str: String): String = escaped(str)
  
  def comment (content: String): String = s"<!-- $content -->"

  def element (tagName: String, options: Options, content: Seq[Element], attrs: (String,Any)*): String =
    s"<$tagName${attributes(tagName,options,attrs)}>${children(content)}</$tagName>"

  def indentedElement (tagName: String, options: Options, content: Seq[Element], attrs: (String,Any)*): String =
    s"<$tagName${attributes(tagName,options,attrs)}>${indentedChildren(content)}$newLine</$tagName>"

  def rawElement (tagName: String, options: Options, content: String, attrs: (String,Any)*): String =
    s"<$tagName${attributes(tagName,options,attrs)}>$content</$tagName>"

  def textElement (tagName: String, options: Options, txt: String, attrs: (String,Any)*): String =
    s"<$tagName${attributes(tagName,options,attrs)}>${text(txt)}</$tagName>"
  
  def emptyElement (tagName: String, options: Options, attrs: (String,Any)*): String =
    s"<$tagName${attributes(tagName,options,attrs)}>"
  
  def emptyElement (tagName: String): String = s"<$tagName>"
  
  /** Produces the complete sequence of attributes to write for the specified tag.
   */
  def attributes (tag: String, options: Options, attrs: Seq[(String,Any)]): String

  /** Writes the specified attributes (passed as name-value tuples),
    * including a preceding space character.
    */
  def attributes (attrs: Seq[(String, String)]): String = attrs.map(t => attribute(t._1 , t._2)).mkString

  /** Writes the specified attribute including a preceding space character.
    */
  def attribute (name: String, value: String): String = s""" $name="$value""""
 
  /** Replaces all special XML/HTML characters
   *  with entities.
   */
  private def escaped (str: String): String = {
    var i = 0
    val end = str.length
    val result = new StringBuilder
    while (i < end) {
      str.charAt(i) match {
        case '<' => result append "&lt;"
        case '>' => result append "&gt;"
        case '"' => result append "&quot;"
        case '\''=> result append "&#39;"
        case '&' => result append "&amp;"
        case '\u00A0' => result append "&nbsp;"
        case '\n' => result append newLine 
        case c   => result append c
      }
      i += 1
    }
    result.toString
  }

  
}
