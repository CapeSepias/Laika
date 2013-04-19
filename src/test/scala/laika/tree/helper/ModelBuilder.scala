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

package laika.tree.helper

import laika.tree.Elements._
   
trait ModelBuilder {

  
  def spans (elements: Span*) = elements.toList
  
  def em (elements: Span*) = Emphasized(elements.toList)

  def em (text: String) = Emphasized(List(txt(text)))

  def str (elements: Span*) = Strong(elements.toList)
  
  def str (text: String) = Strong(List(txt(text)))
  
  def txt (content: String) = Text(content)
  
  def lit (content: String) = Literal(content)
   
  def link (content: Span*) = new LinkBuilder(content.toList)
  
  class LinkBuilder private[ModelBuilder] (content: List[Span], url0: String = "", title0: Option[String] = None) {
    
    def url (value: String) = new LinkBuilder(content, value, title0)
    
    def title (value: String) = new LinkBuilder(content, url0, Some(value))
    
    def toLink = Link(content, url0, title0)
    
  }
  
  def linkRef (content: Span*) = new LinkRefBuilder(content.toList)
  
  class LinkRefBuilder private[ModelBuilder] (content: List[Span], id: String = "", postFix: String = "]") {
    
    def id (value: String): LinkRefBuilder = new LinkRefBuilder(content, value, postFix)
    
    def postFix (value: String): LinkRefBuilder = new LinkRefBuilder(content, id, value)
    
    def toLink = LinkReference(content, id, "[", postFix)
     
  }
  
  def img (text: String, url: String, title: Option[String] = None) = Image(text, url, title)

  def imgRef (text: String, id: String, postFix: String = "]") = ImageReference(text, id, "![", postFix)
  
  def citRef (label: String) = CitationReference(label)
  
  def fnRef (label: FootnoteLabel) = FootnoteReference(label)
  
  
  def doc (blocks: Block*) = Document(blocks.toList)
  
  def p (spans: Span*) = Paragraph(spans.toList)
  
  def p (text: String) = Paragraph(List(Text(text)))
  
  def ss (spans: Span*) = SpanSequence(spans.toList)
  
  def ss (text: String) = SpanSequence(List(Text(text)))
  

  def bl (items: BulletListItem*) = BulletList(items.toList, StringBullet("*"))

  def bl (bullet: String, items: BulletListItem*) = BulletList(items.toList, StringBullet(bullet))
  
  def el (items: EnumListItem*) = EnumList(items.toList, EnumFormat())
  
  def el (format: EnumFormat, start: Int, items: ListItem*) = 
    EnumList(items.toList, format, start)

  def bli (text: String) = BulletListItem(List(ss(txt(text))), StringBullet("*"))

  def bli (bullet: String, text: String) = BulletListItem(List(ss(txt(text))), StringBullet(bullet))

  def eli (pos: Int, text: String) = EnumListItem(List(ss(txt(text))), EnumFormat(), pos)

  def eli (format: EnumFormat, pos: Int, text: String) = EnumListItem(List(ss(txt(text))), format, pos)
    
  def bli (blocks: Block*) = BulletListItem(blocks.toList, StringBullet("*"))

  def bli (bullet: String, blocks: Block*) = BulletListItem(blocks.toList, StringBullet(bullet))

  def eli (pos: Int, blocks: Block*) = EnumListItem(blocks.toList, EnumFormat(), pos)
  
  def dl (items: DefinitionListItem*) = DefinitionList(items.toList)
  
  def dli (term: String, blocks: Block*) = DefinitionListItem(List(Text(term)), blocks.toList)

  def dli (term: List[Span], blocks: Block*) = DefinitionListItem(term, blocks.toList)
  
  
  def table (rows: Row*) = Table(Nil, rows.toList)
  
  def row (cells: Cell*) = Row(cells.toList)
  
  def cell (content: String, colspan: Int, rowspan: Int) = Cell(BodyCell, List(ss(txt(content))), colspan, rowspan)
  
  def cell (content: String): Cell = cell(ss(txt(content)))
  
  def cell (content: Block*): Cell = Cell(BodyCell, content.toList)
  
  def strrow (cells: String*) = Row(cells map cell)
  
  
  def lb (items: LineBlockItem*) = LineBlock(items.toList)
  
  def line (text: String) = Line(List(Text(text)))

  def line (spans: Span*) = Line(spans.toList)
  
  
  def quote (items: Block*) = QuotedBlock(items.toList, Nil)
  
  def quote (text: String) = QuotedBlock(List(p(text)), Nil) 

  def quote (text: String, attribution: String) = QuotedBlock(List(ss(text)), List(txt(attribution))) 
  
  
  def litBlock (content: String) = LiteralBlock(content)
  
  
  def h (level: Int, content: Span*) = Header(level, content.toList)

  def h (level: Int, content: String) = Header(level, List(txt(content)))
  
  
  
  implicit def builderToLink (builder: LinkBuilder) = builder.toLink

  implicit def builderToLinkRef (builder: LinkRefBuilder) = builder.toLink
  
  
}