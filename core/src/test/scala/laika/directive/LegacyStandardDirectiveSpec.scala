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

package laika.directive

import laika.api.MarkupParser
import laika.api.builder.OperationConfig
import laika.config.{Config, ConfigBuilder, ConfigError, ConfigParser, Origin}
import laika.ast._
import laika.ast.helper.ModelBuilder
import laika.format.Markdown
import laika.parse.ParserContext
import laika.parse.hocon.{IncludeResource, ObjectBuilderValue}
import laika.rewrite.TemplateRewriter
import org.scalatest.{FlatSpec, Matchers}


class LegacyStandardDirectiveSpec extends FlatSpec
                            with Matchers
                            with ModelBuilder {


  lazy val templateParser = StandardDirectives.processExtension(DirectiveSupport).parsers.templateParser.get
  lazy val markupParser = MarkupParser.of(Markdown).build

  def parse (input: String): Document = markupParser.parse(input).toOption.get

  def parseWithFragments (input: String): (Map[String,Element], RootElement) = {
    val doc = parse(input)
    (doc.fragments, doc.content)
  }
  
  def parseTemplate (input: String): TemplateRoot = templateParser.parse(ParserContext(input)).toOption.get
  
  def parseTemplateWithConfig (input: String, config: String): RootElement = {
    val tRoot = parseTemplate(input)
    val template = TemplateDocument(Path.Root, tRoot)
    val cursor = DocumentCursor(Document(Path.Root, root(), config = ConfigParser.parse(config).resolve().toOption.get))
    TemplateRewriter.applyTemplate(cursor, template, Map.empty[IncludeResource, Either[ConfigError, ObjectBuilderValue]]).toOption.get.content
  }
  

  "The fragment directive" should "parse a fragment with a single paragraph" in {
    val input = """aa
      |
      |@:fragment foo:
      |  Fragment Text
      |
      |bb""".stripMargin
    parseWithFragments(input) should be ((Map("foo" -> Paragraph(List(Text("Fragment Text")),Styles("foo"))),root(p("aa"),p("bb"))))
  }
  
  it should "parse a fragment with a two paragraphs" in {
    val input = """aa
      |
      |@:fragment foo:
      |  Line 1
      |
      |  Line 2
      |
      |bb""".stripMargin
    parseWithFragments(input) should be ((Map("foo" -> BlockSequence(List(p("Line 1"), p("Line 2")),Styles("foo"))),root(p("aa"),p("bb"))))
  }
  
  
  "The pageBreak directive" should "parse an empty directive" in {
    val input = """aa
      |
      |@:pageBreak.
      |
      |bb""".stripMargin
    parse(input).content should be (root(p("aa"),PageBreak(),p("bb")))
  }
  
  
  "The style directive" should "parse a single nested block" in {
    val input = """aa
      |
      |@:style foo: 11
      | 22
      |
      |bb""".stripMargin
    parse(input).content should be (root(p("aa"), Paragraph(List(Text("11\n22")),Styles("foo")), p("bb")))
  }
  
  it should "parse two nested blocks" in {
    val input = """aa
      |
      |@:style foo: 11
      | 22
      |
      | 33
      |
      |bb""".stripMargin
    parse(input).content should be (root(p("aa"), BlockSequence(List(p("11\n22"),p("33")),Styles("foo")), p("bb")))
  }
  
  it should "parse a single nested span" in {
    val input = """aa @:style foo: { 11 } bb"""
    parse(input).content should be (root(p(txt("aa "), Text(" 11 ", Styles("foo")), txt(" bb"))))
  }
  
  it should "parse two nested spans" in {
    val input = """aa @:style foo: { 11 *22* 33 } bb"""
    parse(input).content should be (root(p(txt("aa "), SpanSequence(List(txt(" 11 "),em("22"),txt(" 33 ")),Styles("foo")), txt(" bb"))))
  }
  
  
  "The format directive" should "parse a single nested block" in {
    val input = """aa
      |
      |@:format foo: 11
      | 22
      |
      |bb""".stripMargin
    parse(input).content should be (root(p("aa"), TargetFormat("foo", p("11\n22")), p("bb")))
  }
  
  it should "parse two nested blocks" in {
    val input = """aa
      |
      |@:format foo: 11
      | 22
      |
      | 33
      |
      |bb""".stripMargin
    parse(input).content should be (root(p("aa"), TargetFormat("foo", BlockSequence(List(p("11\n22"),p("33")))), p("bb")))
  }
  
  
  "The for directive" should "process the default body once if the referenced object is a map" in {
    val input = """aaa @:for "config.person": { {{_.name}} {{_.age}} } bbb"""
    val config = "person: { name: Mary, age: 35 }" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(List(
        tt(" "),tt("Mary"),tt(" "),tt("35"),tt(" ")
      )),
      tt(" bbb")
    )))  
  } 
  
  it should "process the default body multiple times if the referenced object is a list" in {
    val input = """aaa @:for "config.persons": { {{_.name}} {{_.age}} } bbb"""
    val config = "persons: [{ name: Mary, age: 35 },{ name: Lucy, age: 32 },{ name: Anna, age: 42 }]" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(List(
        TemplateSpanSequence(List(
          tt(" "),tt("Mary"),tt(" "),tt("35"),tt(" ")
        )),
        TemplateSpanSequence(List(
          tt(" "),tt("Lucy"),tt(" "),tt("32"),tt(" ")
        )),
        TemplateSpanSequence(List(
          tt(" "),tt("Anna"),tt(" "),tt("42"),tt(" ")
        ))
      )),
      tt(" bbb")
    )))  
  } 
  
  it should "not process the default body if the referenced object is an empty collection" in {
    val input = """aaa @:for "config.persons": { {{name}} {{age}} } bbb"""
    val config = "persons: []" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(Nil),
      tt(" bbb")
    )))  
  } 
  
  it should "process the default body once if the referenced object is a scalar value" in {
    val input = """aaa @:for "config.person": { text } bbb"""
    val config = "person: Mary" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(List(tt(" text "))),
      tt(" bbb")
    )))  
  } 
  
  "The if directive" should "process the default body once if the referenced object is the string 'true'" in {
    val input = """aaa @:if "config.monday": { text } bbb"""
    val config = "monday: true" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(List(tt(" text "))),
      tt(" bbb")
    )))  
  } 
  
  it should "process the default body once if the referenced object is the string 'on'" in {
    val input = """aaa @:if "config.monday": { text } bbb"""
    val config = "monday: on" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(List(tt(" text "))),
      tt(" bbb")
    )))  
  } 
  
  it should "not process the default body if the referenced object does not exist" in {
    val input = """aaa @:if "config.monday": { text } bbb"""
    val config = "tuesday: on" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(Nil),
      tt(" bbb")
    )))  
  } 
  
  it should "not process the default body if the referenced object is not a string recognized as true" in {
    val input = """aaa @:if "config.monday": { text } bbb"""
    val config = "monday: off" 
    parseTemplateWithConfig(input, config) should be (root(tRoot(
      tt("aaa "),
      TemplateSpanSequence(Nil),
      tt(" bbb")
    )))  
  } 
  
  
  trait TreeModel {

    import Path._
    
    val pathUnderTest = Root / "sub2" / "doc7"

    def hasTitleDocs: Boolean = false

    def header (level: Int, title: Int, style: String = "section") =
      Header(level,List(Text("Section "+title)),Id("title"+title) + Styles(style))
      
    val sectionsWithoutTitle = RootElement(
      header(1,1) ::
      header(2,2) ::
      header(1,3) ::
      header(2,4) ::
      Nil
    )

    def config(path: Path, title: String, scope: Origin.Scope): Config = ConfigBuilder
      .withOrigin(Origin(scope, path))
      .withValue("title", title)
      .build

    def titleDoc (path: Path): Option[Document] =
      if (!hasTitleDocs || path == Root) None
      else Some(Document(path / "title", sectionsWithoutTitle, config = config(path / "title", "TitleDoc", Origin.DocumentScope)))

    def docs (path: Path, nums: Int*): Seq[Document] = nums map {
      n => Document(path / ("doc"+n), sectionsWithoutTitle, config = config(path / ("doc"+n), "Doc "+n, Origin.DocumentScope))
    }

    def buildTree (template: TemplateDocument, markup: Document): DocumentTree = {
      DocumentTree(Root, docs(Root, 1,2) ++ List(
        DocumentTree(Root / "sub1", docs(Root / "sub1",3,4), titleDoc(Root / "sub1"), config = config(Root / "sub1", "Tree 1", Origin.TreeScope)),
        DocumentTree(Root / "sub2", docs(Root / "sub2",5,6) ++ List(markup), titleDoc(Root / "sub1"), config = config(Root / "sub2", "Tree 2", Origin.TreeScope))
      ), templates = List(template))
    }

    def parseAndRewrite (template: String, markup: String): RootElement = {
      val templateDoc = TemplateDocument(Root / "test.html", parseTemplate(template))
      val doc = Document(pathUnderTest, parse(markup).content, config =
        config(pathUnderTest, "Doc 7", Origin.DocumentScope).withValue("template","/test.html").build)
      val tree = buildTree(templateDoc, doc).rewrite(OperationConfig.default.rewriteRules)
      TemplateRewriter.applyTemplates(DocumentTreeRoot(tree), "html").toOption.get.tree.selectDocument(Current / "sub2" / "doc7").get.content
    }
    
    def markup = """# Headline 1
        |
        |# Headline 2""".stripMargin
  }
  
  trait TocModel {
    import Path._
    import laika.ast.TitledBlock
    
    val treeUnderTest = Root / "sub2"
    
    def title: Option[String] = None

    def hasTitleDocLinks: Boolean = false
    
    def sectionCrossLink (path: Path, section: Int, level: Int) = 
      Paragraph(Seq(CrossLink(List(txt("Section "+section)), "title"+section, PathInfo.fromPath(path, treeUnderTest))), Styles("toc","level"+level))
      
    def leafLink (path: Path, section: Int, level: Int) = 
      BulletListItem(List(sectionCrossLink(path, section, level)), StringBullet("*"))
      
    def sectionNode (path: Path, section: Int, level: Int) = 
      BulletListItem(List(sectionCrossLink(path, section, level), BulletList(List(leafLink(path, section+1, level+1)), StringBullet("*"))), StringBullet("*"))
      
    def docCrossLink (path: Path, doc: Int, level: Int) =
      Paragraph(Seq(CrossLink(List(txt("Doc "+doc)), "", PathInfo.fromPath(path, treeUnderTest))), Styles("toc","level"+level))
      
    def docList (path: Path, doc: Int, level: Int) = 
      BulletListItem(List(docCrossLink(path, doc, level), BulletList(List(
        sectionNode(path, 1, level+1),
        sectionNode(path, 3, level+1)
      ), StringBullet("*"))), StringBullet("*"))
      
    def docListFirstLevel (path: Path, doc: Int, level: Int) = 
      BulletListItem(List(docCrossLink(path, doc, level), BulletList(List(
        leafLink(path, 1, level+1),
        leafLink(path, 3, level+1)
      ), StringBullet("*"))), StringBullet("*"))
      
    def internalLink (section: Int, level: Int) = 
      BulletListItem(List(
        Paragraph(Seq(InternalLink(List(txt("Headline "+section)), "headline-"+section)), Styles("toc","level"+level))
      ), StringBullet("*"))
      
    def extraDoc (treeNum: Int, level: Int) = 
      if (treeNum == 1) Nil
      else List(BulletListItem(List(
        Paragraph(List(txt("Doc 7")), Styles("toc","level"+level,"active")), 
        BulletList(List(
          internalLink(1, level+1),
          internalLink(2, level+1)
        ), StringBullet("*"))), StringBullet("*")))

    def treeTitle (treeNum: Int) =
      if (!hasTitleDocLinks) Paragraph(List(Text("Tree "+treeNum)), Styles("toc","level1"))
      else Paragraph(Seq(CrossLink(List(txt("TitleDoc")), "", PathInfo.fromPath(Root / ("sub"+treeNum) / "title", treeUnderTest))), Styles("toc","level1"))
      
    def treeList (treeNum: Int, docStart: Int) = 
      BulletListItem(List(
        treeTitle(treeNum),
        BulletList(List(
          docList(Root / ("sub"+treeNum) / ("doc"+docStart),     docStart,   2),
          docList(Root / ("sub"+treeNum) / ("doc"+(docStart+1)), docStart+1, 2)
        ) ++ extraDoc(treeNum,2), StringBullet("*"))
      ), StringBullet("*"))
      
    def rootList = 
      BulletList(List(
        docList(Root / "doc1", 1, 1),
        docList(Root / "doc2", 2, 1),
        treeList(1, 3),
        treeList(2, 5)
      ), StringBullet("*"))
      
    def currentList =
      BulletList(List(
        docList(Root / "sub2" / "doc5", 5, 1),
        docList(Root / "sub2" / "doc6", 6, 1)
      ) ++ extraDoc(2,1), StringBullet("*"))
      
    def firstTree =
      BulletList(List(
        docList(Root / "sub1" / "doc3", 3, 1),
        docList(Root / "sub1" / "doc4", 4, 1)
      ), StringBullet("*"))
      
    def firstTreeFirstLevel =
      BulletList(List(
        docListFirstLevel(Root / "sub1" / "doc3", 3, 1),
        docListFirstLevel(Root / "sub1" / "doc4", 4, 1)
      ), StringBullet("*"))
     
    def currentDoc =
      BulletList(List(
        internalLink(1, 1),
        internalLink(2, 1)
      ), StringBullet("*"))
      
    def result (list: BulletList) = {
      val toc = title match {
        case Some(text) => TitledBlock(List(txt(text)), List(list), options=Styles("toc"))
        case None       => BlockSequence(List(list), Styles("toc"))
      }
      root(tRoot(
        tt("aaa "),
        tElem(toc),
        tt(" bbb "),
        eRoot(
          Section(Header(1, List(txt("Headline 1")), Id("headline-1") + Styles("section")), Nil),
          Section(Header(1, List(txt("Headline 2")), Id("headline-2") + Styles("section")), Nil)
        )
      ))
    }
    
    def markupTocResult = root(
      BlockSequence(List(currentDoc), Styles("toc")),
      Section(Header(1, List(txt("Headline 1")), Id("headline-1") + Styles("section")), Nil),
      Section(Header(1, List(txt("Headline 2")), Id("headline-2") + Styles("section")), Nil)
    )
  }

  "The template toc directive" should "produce a table of content starting from the root tree" in {
    new TreeModel with TocModel {
      
      val template = """aaa @:toc. bbb {{document.content}}"""
      
      parseAndRewrite(template, markup) should be (result(rootList))  
    }
  }

  it should "produce a table of content starting from the root tree containing title documents" in {
    new TreeModel with TocModel {

      override val hasTitleDocs = true
      override val hasTitleDocLinks = true

      val template = """aaa @:toc. bbb {{document.content}}"""

      parseAndRewrite(template, markup) should be (result(rootList))
    }
  }

  it should "produce a table of content starting from the root tree with a title" in {
    new TreeModel with TocModel {
      
      override val title = Some("Some Title")
      
      val template = """aaa @:toc title="Some Title". bbb {{document.content}}"""
      
      parseAndRewrite(template, markup) should be (result(rootList))  
    }
  } 
  
  it should "produce a table of content starting from the current tree" in {
    new TreeModel with TocModel {
      
      val template = """aaa @:toc root=#currentTree. bbb {{document.content}}"""
      
      parseAndRewrite(template, markup) should be (result(currentList))  
    }
  } 
  
  it should "produce a table of content starting from the root of the current document" in {
    new TreeModel with TocModel {
      
      val template = """aaa @:toc root=#currentDocument. bbb {{document.content}}"""
      
      parseAndRewrite(template, markup) should be (result(currentDoc))  
    }
  } 
  
  it should "produce a table of content starting from an explicit absolute path" in {
    new TreeModel with TocModel {
      
      val template = """aaa @:toc root=/sub1. bbb {{document.content}}"""
      
      parseAndRewrite(template, markup) should be (result(firstTree))  
    }
  } 
  
  it should "produce a table of content starting from an explicit relative path" in {
    new TreeModel with TocModel {
      
      val template = """aaa @:toc root="../sub1". bbb {{document.content}}"""
      
      parseAndRewrite(template, markup) should be (result(firstTree))  
    }
  } 
  
  it should "produce a table of content starting from an explicit relative path with depth 2" in {
    new TreeModel with TocModel {
      
      val template = """aaa @:toc root="../sub1" depth=2. bbb {{document.content}}"""
      
      parseAndRewrite(template, markup) should be (result(firstTreeFirstLevel))  
    }
  } 
  
  "The document toc directive"  should "produce a table of content starting from the root of the current document" in {
    new TreeModel with TocModel {
      
      override val markup = """@:toc.
        |
        |# Headline 1
        |
        |# Headline 2""".stripMargin
      
      val template = """{{document.content}}"""
      
      parseAndRewrite(template, markup) should be (markupTocResult)  
    }
  } 
  
  
}
