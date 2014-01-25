/*
 * Copyright 2014 the original author or authors.
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

import laika.tree.Elements._
import laika.tree.Templates._
import laika.io.Output
import laika.factory.RendererFactory
  
/** A renderer for XSL-FO output. May be directly passed to the `Render` or `Transform` APIs:
 * 
 *  {{{
 *  Render as XSLFO from document toFile "hello.html"
 *  
 *  Transform from Markdown to XSLFO fromFile "hello.md" toFile "hello.fo"
 *  }}}
 *  
 *  TODO - this is a new renderer and currently work in progress - do not use yet
 * 
 *  @author Jens Halm
 */
class XSLFO private (messageLevel: Option[MessageLevel], renderFormatted: Boolean) 
    extends RendererFactory[HTMLWriter] {
  
  val fileSuffix = "html"
 
  /** Specifies the minimum required level for a system message
   *  to get included into the output by this renderer.
   */
  def withMessageLevel (level: MessageLevel) = new XSLFO(Some(level), renderFormatted)
  
  /** Renders XSL-FO without any formatting (line breaks or indentation) around tags. 
   *  Useful when storing the output in a database for example. 
   */
  def unformatted = new XSLFO(messageLevel, false)
  
  /** The actual setup method for providing both the writer API for customized
   *  renderers as well as the actual default render function itself. The default render
   *  function always only renders a single element and then delegates to the composite
   *  renderer passed to this function as a parameter when rendering children. This way
   *  user customizations are possible on a per-element basis.
   *  
   *  @param output the output to write to
   *  @param render the composite render function to delegate to when elements need to render their children
   *  @return a tuple consisting of the writer API for customizing
   *  the renderer as well as the actual default render function itself
   */
  def newRenderer (output: Output, render: Element => Unit) = {
    val out = new HTMLWriter(output asFunction, render, formatted = renderFormatted)  
    (out, renderElement(out))
  }

  
  private def renderElement (out: HTMLWriter)(elem: Element): Unit = {
    
    def include (msg: SystemMessage) = {
      messageLevel flatMap {lev => if (lev <= msg.level) Some(lev) else None} isDefined
    }
    
    def noneIfDefault [T](actual: T, default: T) = if (actual == default) None else Some(actual.toString)
    
    def renderBlocks (blocks: Seq[Block], close: String) = blocks match {
      case ss @ SpanSequence(_,_) :: Nil => out << ss << close
      case Paragraph(content,opt) :: Nil => out << SpanSequence(content,opt) << close
      case other                         => out <<|> other <<| close
    }
    
    def renderTable (table: Table) = {
      val children = List(table.caption,table.columns,table.head,table.body) filterNot (_.content.isEmpty)
      
      out <<@ ("???", table.options) <<|> children <<| "</???>"
    }
    
    object WithFallback {
      def unapply (value: Element) = value match {
        case f: Fallback => Some(f.fallback)
        case _ => None
      }
    }
    
    def renderBlockContainer [T <: BlockContainer[T]](con: BlockContainer[T]) = {
  
      def toTable (label: String, content: Seq[Block], options: Options): Table = {
        val left = Cell(BodyCell, List(SpanSequence(List(Text(s"[$label]")))))
        val right = Cell(BodyCell, content)
        val row = Row(List(left,right))
        Table(TableHead(Nil), TableBody(List(row)), Caption(),
            Columns.options(Styles("label"),NoOpt), options)
      }
      
      def quotedBlockContent (content: Seq[Block], attr: Seq[Span]) = 
        if (attr.isEmpty) content
        else content :+ Paragraph(attr, Styles("attribution"))
        
      def figureContent (img: Span, caption: Seq[Span], legend: Seq[Block]): List[Block] =
        List(SpanSequence(List(img)), Paragraph(caption, Styles("caption")), BlockSequence(legend, Styles("legend")))
      
      con match {
        case RootElement(content)             => if (content.nonEmpty) out << content.head <<| content.tail       
        case EmbeddedRoot(content,indent,_)   => out.indented(indent) { if (content.nonEmpty) out << content.head <<| content.tail }       
        case Section(header, content,_)       => out <<         header <<|   content
        case TitledBlock(title, content, opt) => out <<@ ("???",opt) <<|> (Paragraph(title,Styles("title")) +: content) <<| "</???>"
        case QuotedBlock(content,attr,opt)    => out <<@ ("???",opt); renderBlocks(quotedBlockContent(content,attr), "</???>")
        case BulletListItem(content,_,opt)    => out <<@ ("???",opt);         renderBlocks(content, "</???>") 
        case EnumListItem(content,_,_,opt)    => out <<@ ("???",opt);         renderBlocks(content, "</???>") 
        case DefinitionListItem(term,defn,_)  => out << "<???>" << term << "</???>" <<| "<???>"; renderBlocks(defn, "</???>")
        case LineBlock(content,opt)           => out <<@ ("???",opt + Styles("line-block")) <<|> content <<| "</???>"
        case Figure(img,caption,legend,opt)   => out <<@ ("???",opt + Styles("figure")) <<|> figureContent(img,caption,legend) <<| "</???>"
        
        case Footnote(label,content,opt)   => renderTable(toTable(label,content,opt + Styles("footnote")))
        case Citation(label,content,opt)   => renderTable(toTable(label,content,opt + Styles("citation")))
        
        case WithFallback(fallback)         => out << fallback
        case c: Customizable                => c match {
          case BlockSequence(content, NoOpt) => if (content.nonEmpty) out << content.head <<| content.tail // this case could be standalone above, but triggers a compiler bug then
          case _ => out <<@ ("???",c.options) <<|> c.content <<| "</???>"
        }
        case unknown                        => out << "<???>" <<|> unknown.content <<| "</???>"
      }
    }
    
    def renderSpanContainer [T <: SpanContainer[T]](con: SpanContainer[T]) = {
      def escapeTitle (s: String) = s.replace("&","&amp;").replace("\"","&quot;").replace("'","$#39;")
      def codeStyles (language: String) = if (language.isEmpty) Styles("code") else Styles("code", language)
      def crossLinkRef (path: PathInfo, ref: String) = {
        val target = path.relative.name.lastIndexOf(".") match {
          case -1 => path.relative.toString
          case i  => (path.relative.parent / (path.relative.name.take(i) + ".html")).toString
        }
        if (ref.isEmpty) target else s"$target#$ref"
      }
      
      con match {
        
        case Paragraph(content,opt)         => out <<@ ("???",opt)       <<  content <<  "</???>"  
        case Emphasized(content,opt)        => out <<@ ("???",opt)      <<  content <<  "</???>" 
        case Strong(content,opt)            => out <<@ ("???",opt)  <<  content <<  "</???>" 
        case ParsedLiteralBlock(content,opt)=> out <<@ ("???",opt)  <<< content << "</???>"
        case CodeBlock(lang,content,opt)    => out <<@ ("???",opt+codeStyles(lang)) <<<  content << "</???>"
        case Code(lang,content,opt)         => out <<@ ("???",opt+codeStyles(lang)) <<  content << "</???>"
        case Line(content,opt)              => out <<@ ("???",opt + Styles("line")) << content <<  "</???>"
        case Header(level, content, opt)    => out <|; out <<@ ("???"+level.toString,opt) << content << "</???" << level.toString << ">"
  
        case ExternalLink(content, url, title, opt)     => out <<@ ("???", opt, "href"->url,       "title"->title.map(escapeTitle)) << content << "</???>"
        case InternalLink(content, ref, title, opt)     => out <<@ ("???", opt, "href"->("#"+ref), "title"->title.map(escapeTitle)) << content << "</???>"
        case CrossLink(content, ref, path, title, opt)  => out <<@ ("???", opt, "href"->(crossLinkRef(path,ref)), "title"->title.map(escapeTitle)) << content << "</???>"
        
        case WithFallback(fallback)         => out << fallback
        case c: Customizable                => c match {
          case SpanSequence(content, NoOpt) => out << content // this case could be standalone above, but triggers a compiler bug then
          case TemplateRoot(content, NoOpt) => out << content
          case TemplateSpanSequence(content, NoOpt) => out << content
          case _ => out <<@ ("???",c.options) << c.content << "</???>"
        }
        case unknown                        => out << "<???>" << unknown.content << "</???>"
      }
    }
    
    def renderListContainer [T <: ListContainer[T]](con: ListContainer[T]) = con match {
      case EnumList(content,format,start,opt) => 
          out <<@ ("???", opt, ("class", format.enumType.toString.toLowerCase), ("start", noneIfDefault(start,1))) <<|> content <<| "</???>"
      case BulletList(content,_,opt)   => out <<@ ("???",opt) <<|> content <<| "</???>"
      case DefinitionList(content,opt) => out <<@ ("???",opt) <<|> content <<| "</???>"
      
      case WithFallback(fallback)      => out << fallback
      case c: Customizable             => out <<@ ("???",c.options) <<|> c.content <<| "</???>"
      case unknown                     => out << "<???>" <<|> unknown.content <<| "</???>"
    }
    
    def renderTextContainer (con: TextContainer) = con match {
      case Text(content,opt)           => opt match {
        case NoOpt                     => out                   <<&  content
        case _                         => out <<@ ("???",opt)  <<&  content << "</???>"
      }
      case TemplateString(content,opt) => opt match {
        case NoOpt                     => out                   <<  content
        case _                         => out <<@ ("???",opt)  <<  content << "</???>"
      }
      case RawContent(formats, content, opt) => if (formats.contains("html")) { opt match {
        case NoOpt                     => out                   <<   content
        case _                         => out <<@ ("???",opt)  <<   content << "</???>"
      }} 
      case Literal(content,opt)        => out <<@ ("???",opt)  <<<& content << "</???>" 
      case LiteralBlock(content,opt)   => out <<@ ("???",opt)  <<<& content << "</???>"
      case Comment(content,opt)        => out << "<!-- "        <<   content << " -->"
      
      case WithFallback(fallback)      => out << fallback
      case c: Customizable             => out <<@ ("???",c.options) << c.content << "</???>"
      case unknown                     => out <<& unknown.content
    }
    
    def renderSimpleBlock (block: Block) = block match {
      case Rule(opt)                   => out <<@ ("???",opt) 
      case InternalLinkTarget(opt)     => out <<@ ("???",opt) << "</???>"
      
      case WithFallback(fallback)      => out << fallback
      case unknown                     => ()
    }
    
    def renderSimpleSpan (span: Span) = span match {
      case CitationLink(ref,label,opt) => out <<@ ("???",opt + Styles("citation"),"href"->("#"+ref)) << "[" << label << "]</???>" 
      case FootnoteLink(ref,label,opt) => out <<@ ("???",opt + Styles("footnote"),"href"->("#"+ref)) << "[" << label << "]</???>" 
      case Image(text,url,title,opt)   => out <<@ ("???",opt,"src"->url,"alt"->text,"title"->title)
      case LineBreak(opt)              => out << "<???>"
      case TemplateElement(elem,indent,_) => out.indented(indent) { out << elem }
      
      case WithFallback(fallback)      => out << fallback
      case unknown                     => ()
    }
    
    def renderTableElement (elem: TableElement) = elem match {
      case TableHead(rows,opt)         => out <<@ ("???",opt) <<|> rows <<| "</???>"
      case TableBody(rows,opt)         => out <<@ ("???",opt) <<|> rows <<| "</???>"    
      case Caption(content, opt)       => out <<@ ("???",opt) <<  content <<  "</???>" 
      case Columns(columns,opt)        => out <<@ ("???",opt) <<|> columns <<| "</???>"  
      case Column(opt)            => out <<@ ("???",opt) << "</???>"  
      case Row(cells,opt)         => out <<@ ("???",opt) <<|> cells <<| "</???>"
      case Cell(HeadCell, content, colspan, rowspan, opt) => out <<@ 
            ("???", opt, "colspan"->noneIfDefault(colspan,1), "rowspan"->noneIfDefault(rowspan,1)); renderBlocks(content, "</???>") 
      case Cell(BodyCell, content, colspan, rowspan, opt) => out <<@ 
            ("???", opt, "colspan"->noneIfDefault(colspan,1), "rowspan"->noneIfDefault(rowspan,1)); renderBlocks(content, "</???>") 
    }
    
    def renderUnresolvedReference (ref: Reference) = {
      out << InvalidSpan(SystemMessage(Error,s"unresolved reference: $ref"), Text(ref.source)) 
    }
    
    def renderInvalidElement (elem: Invalid[_ <: Element]) = elem match {
      case InvalidBlock(msg, fallback, opt) => if (include(msg)) out << List(Paragraph(List(msg),opt), fallback)
                                               else out << fallback
      case e                                => if (include(e.message)) out << e.message << " " << e.fallback
                                               else out << e.fallback 
    }
    
    def renderSystemMessage (message: SystemMessage) = {
      if (include(message)) 
        out <<@ ("???", message.options + Styles("system-message", message.level.toString.toLowerCase)) << message.content << "</???>"
    }
    
    
    elem match {
      case e: SystemMessage       => renderSystemMessage(e)
      case e: Table               => renderTable(e)
      case e: TableElement        => renderTableElement(e)
      case e: Reference           => renderUnresolvedReference(e)
      case e: Invalid[_]          => renderInvalidElement(e)
      case e: BlockContainer[_]   => renderBlockContainer(e)
      case e: SpanContainer[_]    => renderSpanContainer(e)
      case e: ListContainer[_]    => renderListContainer(e)
      case e: TextContainer       => renderTextContainer(e)
      case e: Block               => renderSimpleBlock(e)
      case e: Span                => renderSimpleSpan(e)

      case unknown                => ()  
    }  
  } 
}

/** The default instance of the XSL-FO renderer.
 */
object XSLFO extends XSLFO(None, true)
