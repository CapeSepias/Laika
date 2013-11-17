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

package laika.directive

import Directives._
import laika.util.Builders._
import laika.tree.Elements._
import laika.tree.Templates._
import laika.tree.Documents._
import laika.tree.Templates.rewriteRules
import scala.collection.JavaConversions._

/** 
 *  @author Jens Halm
 */
trait StandardDirectives {

  
  lazy val templateFor = Templates.create("for") {
    import Templates.Combinators._
    import java.util.{Map => JMap, Collection => JCol}

    (attribute(Default) ~ body(Default) ~ body("empty").optional ~ context) {
      (path, content, fallback, context) => {
        
        def rewriteContent (value: Any) =
          TemplateSpanSequence(content) rewrite rewriteRules(context.withReferenceContext(value))
        
        def rewriteFallback = 
          fallback map (TemplateSpanSequence(_) rewrite rewriteRules(context)) getOrElse (TemplateSpanSequence(Nil))

        context.resolveReference(path) match {
          case Some(m: Map[_,_])  => rewriteContent(m) 
          case Some(m: JMap[_,_]) => rewriteContent(m) 
          case Some(it: Iterable[_]) if it.isEmpty => rewriteFallback
          case Some(it: JCol[_])     if it.isEmpty => rewriteFallback
          case Some(it: Iterable[_]) => {
            val spans = for (value <- it) yield rewriteContent(value)
            TemplateSpanSequence(spans.toSeq)
          }
          case Some(it: JCol[_]) => {
            val spans = for (value <- iterableAsScalaIterable(it)) yield rewriteContent(value)
            TemplateSpanSequence(spans.toSeq)
          }
          case Some("") | Some(false) => rewriteFallback
          case Some(value)            => rewriteContent(value)
          case None                   => TemplateSpanSequence(Nil)
        }
      }
    }
  }
  
  lazy val templateIf = Templates.create("if") {
    import Templates.Combinators._
    import java.util.{Map => JMap, Collection => JCol}

    (attribute(Default) ~ body(Default) ~ body("else").optional ~ context) {
      (path, content, fallback, context) => {
        
        def rewriteContent =
          TemplateSpanSequence(content) rewrite rewriteRules(context)
        
        def rewriteFallback = 
          fallback map (TemplateSpanSequence(_) rewrite rewriteRules(context)) getOrElse (TemplateSpanSequence(Nil))
        
        context.resolveReference(path) match {
          case Some(true) | Some("true") | Some("yes") | Some("on") | Some("enabled") => rewriteContent
          case _ => rewriteFallback
        }
      }
    }
  }
  
  def toc (depth: Option[Int], rootConfig: String, title: Option[String], context: DocumentContext) = {
    
    val format = StringBullet("*")
    val maxLevel = depth getOrElse Int.MaxValue

    def isCurrent (doc: Document) = doc.path == context.document.path
    
    def sectionToLink (section: SectionInfo, path: Path, level: Int) = {
      val options = Styles("toc","level"+level)
      val title = section.title.content
      
      if (path == context.document.path)
        Paragraph(List(InternalLink(title, section.id, options = options)))
      else
        Paragraph(List(CrossLink(title, section.id, PathInfo(path, path.relativeTo(context.parent.path)), options = Styles("toc","level"+level))))
    }
      
    def docToLink (document: Document, level: Int) = {
      val options = Styles("toc","level"+level)
      val title = document.title
      
      if (isCurrent(document))
        Paragraph(title, options = options + Styles("active"))
      else
        Paragraph(List(CrossLink(title, "", PathInfo(document.path, document.path.relativeTo(context.parent.path)), options = options)))
    }  
    
    def treeToText (tree: DocumentTree, level: Int) =
      Paragraph(tree.title, options = Styles("toc","level"+level))
    
    def sectionsToList (sections: Seq[SectionInfo], path: Path, level: Int): List[Block] =
      if (sections.isEmpty || level > maxLevel) Nil else {
        val items = for (section <- sections) yield 
            BulletListItem(sectionToLink(section, path, level) :: sectionsToList(section.content, path, level + 1), format)
        List(BulletList(items, format))
    }
    
    def navigatablesToList (navigatables: Seq[Navigatable], level: Int): List[Block] = {
      def toLink (section: SectionInfo) = 
        Paragraph(List(InternalLink(List(Text(section.title.text)), section.id, options = Styles("toc","level"+level))))
      
      if (navigatables.isEmpty || level > maxLevel) Nil else {
        val items = for (navigatable <- navigatables) yield navigatable match {
          case doc: Document => BulletListItem(docToLink(doc, level) :: sectionsToList(doc.sections, doc.path, level + 1), format)
          case tree: DocumentTree => BulletListItem(treeToText(tree, level) :: navigatablesToList(tree.navigatables, level + 1), format)
        }
          
        List(BulletList(items, format))
      }
    }
      
    val root = rootConfig match {
      case "#rootTree"        => context.root
      case "#currentTree"     => context.parent
      case "#currentDocument" => context.document
      case pathString => {
        val configPath = Path(pathString)
        val path = 
          (if (configPath.isAbsolute) configPath
          else (context.parent.path / configPath)).relativeTo(context.root.path) 
        context.root.selectDocument(path).getOrElse(context.root.selectSubtree(path).getOrElse(context.root))
      }
    }
    
    val list = root match {
      case doc: Document      => sectionsToList(doc.sections, doc.path, 1)
      case tree: DocumentTree => navigatablesToList(tree.navigatables, 1)
    }
    val titleSeq = List(Text(title.getOrElse("Contents")))
    TitledBlock(titleSeq, list, Styles("toc"))
  }
  
  lazy val templateToc = Templates.create("toc") {
    import Templates.Combinators._
    import Templates.Converters._
    
    (attribute("depth", positiveInt).optional ~ 
        attribute("root").optional ~ 
        attribute("title").optional ~ 
        context) {  
      (depth, rootConfig, title, context) =>
        TemplateElement(toc(depth, rootConfig.getOrElse("#rootTree"), title, context))
    }
  }
  
  lazy val blockToc = Blocks.create("toc") {
    import Blocks.Combinators._
    import Blocks.Converters._
    
    (attribute("depth", positiveInt).optional ~ 
        attribute("root").optional ~ 
        attribute("title").optional ~ 
        context) {  
      (depth, rootConfig, title, context) =>
        toc(depth, rootConfig.getOrElse("#currentDocument"), title, context)
    }
  }
  
  
  lazy val blockFragment = Blocks.create("fragment") {
    import Blocks.Combinators._
    
    (attribute(Default) ~ body(Default)) {
      (name, content) => DocumentFragment(name, BlockSequence(content))
    }
  }
  
  lazy val templateFragment = Templates.create("fragment") {
    import Templates.Combinators._
    
    (attribute(Default) ~ body(Default)) {
      (name, content) => TemplateElement(DocumentFragment(name, TemplateSpanSequence(content)))
    }
  }
  
  lazy val stdBlockDirectives = List(
    blockToc,
    blockFragment
  )
  
  lazy val stdTemplateDirectives = List(
    templateToc,
    templateFor,
    templateIf
  )
  
}