/*
 * Copyright 2013-2018 the original author or authors.
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

package laika.render.epub

import laika.ast.Path.Root
import laika.ast._

/** Represents a recursive book navigation structure.
  */
trait BookNavigation {
  def title: String
  def pos: Int
  def children: Seq[BookNavigation]
}

/** Represents a book navigation entry that only serves as a section header without linking to content.
  */
case class BookSectionHeader (title: String, pos: Int, children: Seq[BookNavigation]) extends BookNavigation

/** Represents a book navigation entry that links to content in the document tree.
  */
case class BookNavigationLink (title: String, link: String, pos: Int, children: Seq[BookNavigation]) extends BookNavigation

object BookNavigation {

  /** Provides the full path to the document relative to the EPUB container root
    * from the specified virtual path of the Laika document tree.
    */
  def fullPath (path: Path): String = {
    val parent = path.parent match {
      case Root => ""
      case _ => path.parent.toString
    }
    "text" + parent + "/" + path.basename + ".xhtml"
  }

  /** Extracts navigation structure from document trees, documents and section in the specified
    * tree.
    *
    * The configuration key for setting the recursion depth is `epub.toc.depth`.
    *
    * @param root the document tree to generate navPoints for
    * @param depth the recursion depth through trees, documents and sections
    * @return a recursive structure of `BookNavigation` instances
    */
  def forTree (tree: DocumentTree, depth: Int, pos: Iterator[Int] = Iterator.from(0)): Seq[BookNavigation] = {

    def hasContent (level: Int)(nav: Navigatable): Boolean = nav match {
      case _: Document => true
      case tree: DocumentTree => if (level > 0) tree.content.exists(hasContent(level - 1)) else false
    }

    def forSections (path: Path, sections: Seq[SectionInfo], levels: Int, pos: Iterator[Int]): Seq[BookNavigation] =
      if (levels == 0) Nil
      else for (section <- sections) yield {
        val title = section.title.extractText
        val parentPos = pos.next
        val children = forSections(path, section.content, levels - 1, pos)
        BookNavigationLink(title, fullPath(path) + "#" + section.id, parentPos, children)
      }

    if (depth == 0) Nil
    else for (nav <- tree.content if hasContent(depth - 1)(nav)) yield nav match {
      case doc: Document =>
        val title = if (doc.title.nonEmpty) SpanSequence(doc.title).extractText else doc.name
        val parentPos = pos.next
        val children = forSections(doc.path, doc.sections, depth - 1, pos)
        BookNavigationLink(title, fullPath(doc.path), parentPos, children)
      case subtree: DocumentTree =>
        val title = if (subtree.title.nonEmpty) SpanSequence(subtree.title).extractText else subtree.name
        val parentPos = pos.next
        val children = forTree(subtree, depth - 1, pos)
        val link = fullPath(subtree.content.collectFirst{ case d: Document => d }.get.path)
        if (depth == 1) BookNavigationLink(title, link, parentPos, children)
        else BookSectionHeader(title, parentPos, children)
    }
  }

}
