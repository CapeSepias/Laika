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

import laika.ast.Path.Current
import laika.ast._
import laika.render.epub.StyleSupport.collectStyles

/** Renders the entire content of an EPUB HTML navigation file.
  *
  * @author Jens Halm
  */
class HtmlNavRenderer {


  /** Inserts the specified (pre-rendered) navPoints into the NCX document template
    * and returns the content of the entire NCX file.
    */
  def fileContent (title: String, styles: String, navItems: String, coverDoc: Option[String] = None, titleDoc: Option[String] = None): String =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<!DOCTYPE html>
       |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
       |  <head>
       |    <meta charset="utf-8" />
       |    <meta name="generator" content="laika" />
       |    <title>$title</title>
       |    $styles
       |  </head>
       |  <body>
       |    <nav epub:type="toc" id="toc">
       |      <h1 id="toc-title">$title</h1>
       |$navItems
       |    </nav>
       |    <nav epub:type="landmarks" id="landmarks">
       |      <h2>Guide</h2>
       |      <ol>
       |${coverDoc.fold("")(uri => navLink("Cover", uri, 100001, "", "epub:type=\"cover\" "))}
       |${titleDoc.fold("")(uri => navLink(title, uri, 100002, "", "epub:type=\"titlepage\" "))}
       |        <li>
       |          <a epub:type="toc" href="#toc">Table of Contents</a>
       |        </li>
       |    </nav>
       |  </body>
       |</html>
       |
    """.stripMargin.replaceAll("[\n]+", "\n")

  /** Renders a single navigation link.
    */
  def navLink (title: String, link: String, pos: Int, children: String, epubType: String = ""): String =
    s"""        <li id="toc-li-$pos">
       |          <a ${epubType}href="$link">$title</a>
       |$children
       |        </li>""".stripMargin

  /** Renders a single navigation header.
    */
  def navHeader (title: String, pos: Int, children: String): String =
    s"""        <li id="toc-li-$pos">
       |          <span>$title</span>
       |$children
       |        </li>""".stripMargin

  /** Generates navigation entries for the structure of the DocumentTree. Individual
    * entries can stem from tree or subtree titles, document titles or
    * document sections, depending on which recursion depth is configured.
    * The configuration key for setting the recursion depth is `epub.toc.depth`.
    *
    * @param root the document tree to generate navPoints for
    * @param depth the recursion depth through trees, documents and sections
    * @return the navPoint XML nodes for the specified document tree
    */
  def navItems (bookNav: Seq[BookNavigation]): String =
    if (bookNav.isEmpty) ""
    else bookNav.map {
      case BookSectionHeader(title, pos, children)        => navHeader(title, pos, navItems(children))
      case BookNavigationLink(title, link, pos, children) => navLink(title, link, pos, navItems(children))
    }.mkString("      <ol class=\"toc\">\n", "\n", "\n      </ol>")

  /** Renders the entire content of an EPUB HTML navigation file for
    * the specified document tree. The recursion depth will be applied
    * to the tree structure obtained by recursively processing titles of document
    * trees, documents and sections.
    * The configuration key for setting the recursion depth is `epub.toc.depth`.
    */
  def render (tree: DocumentTree, depth: Int): String = {
    val title = if (tree.title.isEmpty) "UNTITLED" else SpanSequence(tree.title).extractText
    val bookNav = BookNavigation.forTree(tree.copy(content = tree.content.filter(_.path.basename != "cover")), depth)
    val styles = collectStyles(tree).map { input =>
      s"""<link rel="stylesheet" type="text/css" href="content${input.path.toString}" />"""
    }.mkString("\n    ")
    val renderedNavPoints = navItems(bookNav)
    val coverDoc = tree.selectDocument(Current / "cover").map(doc => BookNavigation.fullPath(doc.path, forceXhtml = true))
    val titleDoc = tree.titleDocument.map(doc => BookNavigation.fullPath(doc.path, forceXhtml = true))

    fileContent(title, styles, renderedNavPoints, coverDoc, titleDoc)
  }


}