/*
 * Copyright 2015-2016 the original author or authors.
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

import java.io.ByteArrayOutputStream

import laika.api.Render
import laika.factory.RenderResultProcessor
import laika.io.Input
import laika.io.Output.BinaryOutput
import laika.io.OutputProvider.OutputConfig
import laika.tree.Documents.DocumentTree
import laika.tree.Paths.Root
import org.scalatest.{FlatSpec, Matchers}

class FOforPDFSpec extends FlatSpec with Matchers {
  
  
  case class FOTest (config: Option[PDFConfig]) extends RenderResultProcessor[FOWriter] {
    
    val factory = XSLFO
    
    private val foForPDF = new FOforPDF(config)
    
    def process (tree: DocumentTree, render: (DocumentTree, OutputConfig) => Unit, output: BinaryOutput): Unit = {
    
      val fo = foForPDF.renderFO(tree, render)
      val out = output.asStream
      out.write(fo.getBytes("UTF-8"))
      
    }
    
  }
  
  
  trait ResultModel {
    
    private lazy val defaultTemplate = Input.fromClasspath("/templates/default.template.fo", Root / "default.template.fo").asParserInput.source.toString

    def results (num: Int): String = (1 to num) map (result) reduce (_ + _)
    
    def idPrefix (num: Int): String = if (num > 4) "_tree2" else if (num > 2) "_tree1" else ""
    
    def result (num: Int): String = {
      s"""<fo:marker marker-class-name="chapter"><fo:block>Title $num &amp; More</fo:block></fo:marker>
        |<fo:block id="${idPrefix(num)}_doc${num}_title-$num" font-family="sans-serif" font-size="16pt" font-weight="bold" keep-with-next="always" space-after="7mm" space-before="12mm">Title $num &amp; More</fo:block>
        |<fo:block font-family="serif" font-size="10pt" space-after="3mm">Text $num</fo:block>""".stripMargin
    }

    def resultsWithDocTitle (num: Int): String = (1 to num) map (resultWithDocTitle) reduce (_ + _)
    
    def resultWithDocTitle (num: Int): String = {
      s"""<fo:block id="${idPrefix(num)}_doc${num}_">
        |  <fo:marker marker-class-name="chapter"><fo:block>Title $num &amp; More</fo:block></fo:marker>
        |  <fo:block id="${idPrefix(num)}_doc${num}_title-$num" font-family="sans-serif" font-size="16pt" font-weight="bold" keep-with-next="always" space-after="7mm" space-before="12mm">Title $num &amp; More</fo:block>
        |</fo:block>
        |<fo:block font-family="serif" font-size="10pt" space-after="3mm">Text $num</fo:block>""".stripMargin
    }
    
    def treeTitleResult (num: Int): String = {
      val idPrefix = if (num == 3) "_tree2" else if (num == 2) "_tree1" else ""
      s"""<fo:block id="${idPrefix}__title__" font-family="sans-serif" font-size="16pt" font-weight="bold" keep-with-next="always" space-after="7mm" space-before="12mm">Tree $num &amp; More</fo:block>"""
    }
    
    def treeLinkResult (num: Int): String = {
      val idPrefix = if (num == 3) "_tree2" else if (num == 2) "_tree1" else ""
      s"""<fo:block id="${idPrefix}__title__"/>"""
    }
    
    def tocTitle: String = """<fo:marker marker-class-name="chapter"><fo:block>Contents</fo:block></fo:marker>
      |<fo:block font-family="sans-serif" font-size="16pt" keep-with-next="always" space-after="7mm" space-before="12mm">Contents</fo:block>
      |"""stripMargin
    
    def tocDocResult (num: Int): String = {
      val margin = if (num > 2) """ margin-left="4mm"""" else ""
      val fontsize = if (num > 2) "11pt" else "12pt"
      val spaceBefore = if (num > 2) "2mm" else "5mm"
      s"""<fo:block font-family="serif" font-size="$fontsize"$margin space-after="0mm" space-before="$spaceBefore" text-align-last="justify"><fo:basic-link color="#3956ac" internal-destination="${idPrefix(num)}_doc${num}_">Title $num &amp; More<fo:leader leader-pattern="dots"></fo:leader><fo:page-number-citation ref-id="${idPrefix(num)}_doc${num}_" /></fo:basic-link></fo:block>""" + "\n"
    }
    
    def tocTreeResult (num: Int): String = 
      s"""<fo:block font-family="serif" font-size="12pt" space-after="0mm" space-before="5mm" text-align-last="justify"><fo:basic-link color="#3956ac" internal-destination="_tree${num}__title__">Tree ${num+1} &amp; More<fo:leader leader-pattern="dots"></fo:leader><fo:page-number-citation ref-id="_tree${num}__title__" /></fo:basic-link></fo:block>""" + "\n"
    
    def withDefaultTemplate(result: String, bookmarks: String = ""): String =
      defaultTemplate.replace("{{document.content}}", result).replace("{{document.fragments.bookmarks}}", bookmarks)    
      
    def bookmarkTreeResult(treeNum: Int, docNum: Int): String = s"""    <fo:bookmark internal-destination="_tree${treeNum}__title__">
      |      <fo:bookmark-title>Tree ${treeNum + 1} &amp; More</fo:bookmark-title>
      |      <fo:bookmark internal-destination="_tree${treeNum}_doc${docNum}_">
      |        <fo:bookmark-title>Title $docNum &amp; More</fo:bookmark-title>
      |      </fo:bookmark>
      |      <fo:bookmark internal-destination="_tree${treeNum}_doc${docNum + 1}_">
      |        <fo:bookmark-title>Title ${docNum + 1} &amp; More</fo:bookmark-title>
      |      </fo:bookmark>
      |    </fo:bookmark>
      |""".stripMargin  
      
    val bookmarkRootResult = """<fo:bookmark-tree>
      |    <fo:bookmark internal-destination="_doc1_">
      |      <fo:bookmark-title>Title 1 &amp; More</fo:bookmark-title>
      |    </fo:bookmark>
      |    <fo:bookmark internal-destination="_doc2_">
      |      <fo:bookmark-title>Title 2 &amp; More</fo:bookmark-title>
      |    </fo:bookmark>
      |""".stripMargin  
      
    val closeBookmarks = "</fo:bookmark-tree>"
  }
  
  
  trait Setup extends TreeModel with ResultModel {
    
    def config: Option[PDFConfig]
    
    def result: String = {
      val stream = new ByteArrayOutputStream
      Render as FOTest(config) from tree toStream stream      
      stream.toString
    }
    
  }
  
  
  "The FOforPDF utility" should "render a tree with all structure elements disabled" in new Setup {
    
    val config = Some(PDFConfig(insertTitles = false, bookmarkDepth = 0, tocDepth = 0))
    
    result should be (withDefaultTemplate(results(6)))
  }
  
  it should "render a tree with inserted titles for documents and trees" in new Setup {
    
    val config = Some(PDFConfig(insertTitles = true, bookmarkDepth = 0, tocDepth = 0))
    
    result should be (withDefaultTemplate(treeTitleResult(1) + resultWithDocTitle(1) + resultWithDocTitle(2)
        + treeTitleResult(2) + resultWithDocTitle(3) + resultWithDocTitle(4)
        + treeTitleResult(3) + resultWithDocTitle(5) + resultWithDocTitle(6)))
  }
  
  it should "render a tree with a table of content" in new Setup {
    
    val config = Some(PDFConfig(insertTitles = false, bookmarkDepth = 0, tocDepth = Int.MaxValue, tocTitle = Some("Contents")))
    
    result should be (withDefaultTemplate(treeLinkResult(1) + tocTitle + tocDocResult(1) + tocDocResult(2)
        + tocTreeResult(1) + tocDocResult(3) + tocDocResult(4)
        + tocTreeResult(2) + tocDocResult(5) + tocDocResult(6).dropRight(1) + resultWithDocTitle(1) + resultWithDocTitle(2)
        + treeLinkResult(2) + resultWithDocTitle(3) + resultWithDocTitle(4)
        + treeLinkResult(3) + resultWithDocTitle(5) + resultWithDocTitle(6)))
  }
  
  it should "render a tree with bookmarks" in new Setup {
    
    val config = Some(PDFConfig(insertTitles = false, bookmarkDepth = Int.MaxValue, tocDepth = 0))
    
    result should be (withDefaultTemplate(treeLinkResult(1) + resultWithDocTitle(1) + resultWithDocTitle(2)
        + treeLinkResult(2) + resultWithDocTitle(3) + resultWithDocTitle(4)
        + treeLinkResult(3) + resultWithDocTitle(5) + resultWithDocTitle(6), 
        bookmarkRootResult + bookmarkTreeResult(1,3) + bookmarkTreeResult(2,5).dropRight(1) + closeBookmarks))
  }
  
  it should "render a tree with all structure elements enabled" in new Setup {
    
    val config = Some(PDFConfig.default)
    
    result should be (withDefaultTemplate(
        treeTitleResult(1) + tocDocResult(1) + tocDocResult(2)
        + tocTreeResult(1) + tocDocResult(3) + tocDocResult(4)
        + tocTreeResult(2) + tocDocResult(5) + tocDocResult(6).dropRight(1) 
        + resultWithDocTitle(1) + resultWithDocTitle(2)
        + treeTitleResult(2) + resultWithDocTitle(3) + resultWithDocTitle(4)
        + treeTitleResult(3) + resultWithDocTitle(5) + resultWithDocTitle(6),
        bookmarkRootResult + bookmarkTreeResult(1,3) + bookmarkTreeResult(2,5).dropRight(1) + closeBookmarks
    ))
  }
  
  it should "render a tree with all structure elements disabled by a tree configuration file" in new Setup {
    
    val config = None
    
    override val usePDFFileConfig = true
    
    result should be (withDefaultTemplate(results(6)))
  }


}
