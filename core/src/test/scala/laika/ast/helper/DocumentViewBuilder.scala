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

package laika.ast.helper

import laika.ast._
import laika.ast.DocumentType._
import laika.io.{BinaryInput, Input}
import laika.rst.ast.CustomizedTextRole

/* Provides a view of DocumentTree structures that allows for 
 * formatted AST rendering (for debugging purposes) and case
 * class based equality for test assertions, whereas the corresponding
 * original model classes do not come with any useful notion of equality.
 * This is because they have content like a Typesafe Config instance or
 * rewrite rules (partial functions) for which there is no straightforward
 * equality and which only have an effect in a rewrite operation.
 * These views reduce the model to the components which can easily get
 * compared. Therefore it is recommended to use them with models which
 * already have been rewritten (where the left out components of the
 * structure do not matter any more) or where any possible future effects
 * of rewriting can be safely ignored for testing purposes.
 */
object DocumentViewBuilder {

  
  trait View extends Element
  
  trait ViewContainer[Self <: ViewContainer[Self]] extends ElementContainer[View,Self] with View
  
  case class TreeView (path: Path, content: Seq[TreeContent]) extends ViewContainer[TreeView]
  
  trait TreeContent extends View
  
  case class Documents (docType: DocumentType, content: Seq[DocumentView]) extends TreeContent with ViewContainer[Documents]

  case class TemplateDocuments (content: Seq[TemplateView]) extends TreeContent with ViewContainer[TemplateDocuments]
  
  case class StyleSheets (content: Map[String, StyleDeclarationSet]) extends TreeContent
                          
  case class Inputs (docType: DocumentType, content: Seq[InputView]) extends TreeContent with ViewContainer[Inputs]
  
  case class Subtrees (content: Seq[TreeView]) extends TreeContent with ViewContainer[Subtrees]
  
  case class InputView (name: String) extends View

  case class DocumentView (path: Path, content: Seq[DocumentContent]) extends ViewContainer[DocumentView]

  case class TemplateView (path: Path, content: TemplateRoot) extends View
  
  trait DocumentContent extends View
  
  case class Fragments (content: Seq[Fragment]) extends DocumentContent with ViewContainer[Fragments]
  
  case class Fragment (name: String, content: Element) extends View
  
  case class Content (content: Seq[Block]) extends DocumentContent with BlockContainer[Content] {
    def withContent (newContent: Seq[Block]): Content = copy(content = newContent)
  }
  
  case class Title (content: Seq[Span]) extends DocumentContent with SpanContainer[Title] {
    def withContent (newContent: Seq[Span]): Title = copy(content = newContent)
  }
  
  case class Sections (content: Seq[SectionInfo]) extends DocumentContent with ElementContainer[SectionInfo, Sections]

  def viewOf (root: DocumentTreeRoot): TreeView = viewOf(root.tree, root.staticDocuments)
  
  def viewOf (tree: DocumentTree, staticDocuments: Seq[BinaryInput] = Nil): TreeView = {
    val content = (
      Documents(Markup, tree.content.collect{case doc: Document => doc} map viewOf) ::
      StyleSheets(tree.styles) ::
      TemplateDocuments(tree.templates map viewOf) ::
      Inputs(Static, staticDocuments map viewOf) ::
      Subtrees(tree.content.collect{case tree: DocumentTree => tree} map (t => viewOf(t))) :: 
      List[TreeContent]()) filterNot { case c: ViewContainer[_] => c.content.isEmpty; case StyleSheets(styles) => styles.isEmpty }
    TreeView(tree.path, content)
  }
  
  def viewOf (doc: Document): DocumentView = {
    def filterTitle (title: Seq[Span]) = title match {
      case Text("",_) :: Nil => Nil
      case other => other
    }
    val content = (
      Content(doc.content.rewriteBlocks{ case CustomizedTextRole(_,_,_) => Remove }.content) :: 
      Fragments(viewOf(doc.fragments)) :: 
      Title(filterTitle(doc.title)) ::
      Sections(doc.sections) ::
      List[DocumentContent]()) filterNot { case c: ElementContainer[_,_] => c.content.isEmpty }
    DocumentView(doc.path, content)
  }
  
  def viewOf (fragments: Map[String, Element]): Seq[Fragment] = 
    (fragments map { case (name, content) => Fragment(name, content) }).toSeq
  
  def viewOf (doc: TemplateDocument): TemplateView = TemplateView(doc.path, doc.content)
  
  def viewOf (input: Input): InputView = InputView(input.name)
  
  
}
