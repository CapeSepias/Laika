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

package laika.render.epub

import cats.effect.{ContextShift, IO}
import laika.api.Renderer
import laika.ast.Path.Root
import laika.ast._
import laika.ast.helper.ModelBuilder
import laika.format.EPUB
import laika.io.implicits._
import laika.io.model.{RenderedDocument, RenderedTree, RenderedTreeRoot, StringTreeOutput}
import laika.runtime.TestContexts.blocker
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext

/**
  * @author Jens Halm
  */
class XHTMLRendererSpec extends FlatSpec with Matchers with ModelBuilder {

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  
  trait DocBuilder {

    def markupDoc (num: Int, path: Path = Root) = Document(path / ("doc"+num), root(p("Doc"+num)))

  }

  trait StringRenderer {

    def collectDocuments (result: RenderedTree): Seq[RenderedDocument] =
      (result.content.collect { case doc: RenderedDocument => Seq(doc) } ++
        result.content.collect { case tree: RenderedTree => collectDocuments(tree) }).flatten
    
    def renderedDocs (tree: DocumentTree): Seq[RenderedDocument] = {
      val res = renderedDocs(DocumentTreeRoot(tree))
      collectDocuments(res.tree)
    } 

    def renderedDocs (root: DocumentTreeRoot): RenderedTreeRoot = {
      Renderer
        .of(EPUB.XHTML)
        .io(blocker)
        .parallel[IO]
        .build
        .from(root)
        .toOutput(IO.pure(StringTreeOutput))
        .render
        .unsafeRunSync()
    }

    def renderedXhtml (num: Int, style1: String, style2: String): String = s"""<?xml version="1.0" encoding="UTF-8"?>
     |<!DOCTYPE html>
     |<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
     |  <head>
     |    <meta charset="utf-8" />
     |    <meta name="generator" content="laika" />
     |    <title></title>
     |    <link rel="stylesheet" type="text/css" href="$style1" />
     |    <link rel="stylesheet" type="text/css" href="$style2" />
     |  </head>
     |  <body epub:type="bodymatter">
     |    <div class="content">
     |      <p>Doc$num</p>
     |    </div>
     |  </body>
     |</html>""".stripMargin

  }

  "The XHTML Renderer for EPUB" should "render a tree with 2 documents and 2 style sheets" in {
    new DocBuilder with StringRenderer {
      val input = DocumentTreeRoot(
        tree = DocumentTree(Root,
          content = List(
            markupDoc(1),
            DocumentTree(Root / "sub", content = List(markupDoc(2, Root / "sub")))
          )
        ),
        staticDocuments = Seq(Root / "sub" / "styles2.css", Root / "styles1.css")
      ) 

      val expected = Seq(
        RenderedDocument(Root / "doc1.epub.xhtml", Nil, Nil, renderedXhtml(1, "sub/styles2.css", "styles1.css")),
        RenderedDocument(Root / "sub" / "doc2.epub.xhtml", Nil, Nil, renderedXhtml(2, "styles2.css", "../styles1.css"))
      )

      collectDocuments(renderedDocs(input).tree) shouldBe expected
    }
  }
  
  it should "render a paragraph containing a citation link with an epub:type attribute" in {
    val elem = p(txt("some "), CitationLink("ref","label"), txt(" span"))
    Renderer.of(EPUB.XHTML).build.render(elem) should be ("""<p>some <a class="citation" href="#ref" epub:type="noteref">[label]</a> span</p>""")
  }

  it should "render a paragraph containing a footnote link with an epub:type attribute" in {
    val elem = p(txt("some "), FootnoteLink("id","label"), txt(" span"))
    Renderer.of(EPUB.XHTML).build.render(elem) should be ("""<p>some <a class="footnote" href="#id" epub:type="noteref">[label]</a> span</p>""")
  }

  it should "render a footnote with an epub:type attribute" in {
    val elem = Footnote("label", List(p("a"),p("b")), Id("id"))
    val html = """<aside id="id" class="footnote" epub:type="footnote">
     |  <p>a</p>
     |  <p>b</p>
     |</aside>""".stripMargin
    Renderer.of(EPUB.XHTML).build.render(elem) should be (html)
  }

  it should "render a citation with an epub:type attribute" in {
    val elem = Citation("ref", List(p("a"),p("b")), Id("ref"))
    val html = """<aside id="ref" class="citation" epub:type="footnote">
     |  <p>a</p>
     |  <p>b</p>
     |</aside>""".stripMargin
    Renderer.of(EPUB.XHTML).build.render(elem) should be (html)
  }


}
