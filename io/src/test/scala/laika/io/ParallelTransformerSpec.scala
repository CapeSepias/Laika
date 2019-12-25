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
  
package laika.io

import java.io._

import cats.effect.IO
import laika.api.Transformer
import laika.api.builder.OperationConfig
import laika.ast.DocumentType.Ignored
import laika.ast.Path.Root
import laika.ast._
import laika.bundle.{BundleProvider, ExtensionBundle}
import laika.directive.Templates
import laika.format._
import laika.io.helper.OutputBuilder._
import laika.io.helper.{InputBuilder, OutputBuilder, RenderResult}
import laika.io.implicits._
import laika.io.model.{StringTreeOutput, TreeInput}
import laika.io.text.ParallelTransformer
import laika.parse.Parser
import laika.parse.text.TextParsers
import org.scalatest.Assertion

class ParallelTransformerSpec extends IOSpec with FileIO {

  
  private val transformer: ParallelTransformer[IO] = Transformer.from(Markdown).to(AST).io(blocker).parallel[IO].build
  private def transformerWithBundle (bundle: ExtensionBundle): ParallelTransformer[IO] = 
    Transformer.from(Markdown).to(AST).using(bundle).io(blocker).parallel[IO].build
  
  
  trait TreeTransformer extends InputBuilder {
    import laika.ast.{DocumentType, Path}

    def inputs: Seq[(Path, String)]

    def input (in: Seq[(Path, String)], docTypeMatcher: Path => DocumentType): TreeInput[IO] = build(in, docTypeMatcher)

    def transformTree: IO[RenderedTreeViewRoot] = transformWith()
    
    def transformWithConfig (config: String): IO[RenderedTreeViewRoot] = transformWithBundle(BundleProvider.forConfigString(config))
    def transformWithDocTypeMatcher (matcher: PartialFunction[Path, DocumentType]): IO[RenderedTreeViewRoot] = transformWithBundle(BundleProvider.forDocTypeMatcher(matcher))
    def transformWithTemplates (parser: Parser[TemplateRoot]): IO[RenderedTreeViewRoot] = transformWithBundle(BundleProvider.forTemplateParser(parser))
    def transformWithDirective (directive: Templates.Directive): IO[RenderedTreeViewRoot] = transformWithBundle(BundleProvider.forTemplateDirective(directive))
    
    private def transformWith (transformer: ParallelTransformer[IO] = transformer): IO[RenderedTreeViewRoot] =
      transformer
        .fromInput(IO.pure(input(inputs, transformer.config.docTypeMatcher)))
        .toOutput(StringTreeOutput)
        .transform
        .map(OutputBuilder.RenderedTreeView.toTreeView[IO])

    private def transformWithBundle (bundle: ExtensionBundle): IO[RenderedTreeViewRoot] =
      transformWith(Transformer.from(Markdown).to(AST).using(bundle).io(blocker).parallel[IO].build)
    
    def root (content: Seq[TreeContentView]): RenderedTreeView = RenderedTreeView(Root, content)
    
    object Contents {
      val name = "foo"
      val aa = "aa"
      val style = "13"
      val link = "[link](foo)"
      val directive = "${document.content} @:foo bar. bb"
      val templateConfigRef = "${document.content}{{config.value}}"
      val template1 = "${document.content}"
      val template2 = "(${document.content})"
      val conf = "value: abc"
    }
    
    val simpleResult: String = """RootElement - Blocks: 1
      |. Paragraph - Spans: 1
      |. . Text - 'foo'""".stripMargin
      
    def docs (values: (Path, String)*): DocumentViews = DocumentViews(values map { case (path, content) => RenderedDocumentView(path, content) })

    def sorted (tree: RenderedTreeView): RenderedTreeView = tree.copy(content = tree.content map sortedContent)
        
    def sortedContent (content: TreeContentView): TreeContentView = content match {
      case DocumentViews(cnt) => DocumentViews(cnt.sortBy(_.path.name))
      case SubtreeViews(cnt) => SubtreeViews(cnt.sortBy(_.path.name) map sorted)
    }
    
    def trees (values: (Path, Seq[TreeContentView])*) = SubtreeViews(values map { case (path, content) => RenderedTreeView(path, content) })

    implicit class TreeAssertions[A](private val self: IO[RenderedTreeViewRoot]) {
      def assertTree(tree: RenderedTreeView): Assertion = self.asserting(_.tree shouldBe tree)
    }
  }


  "The parallel transformer" should {

    "transform an empty tree" in new TreeTransformer {
      val inputs = Nil
      transformTree.assertTree(root(Nil))
    }

    "transform a tree with a single document" in new TreeTransformer {
      val inputs = Seq(
        Root / "name.md" -> Contents.name
      )
      transformTree.assertTree(root(List(docs((Root / "name.txt", simpleResult)))))
    }

    "transform a tree with a cover, title document and one content document" in new TreeTransformer {
      val inputs = Seq(
        Root / "name.md" -> Contents.name,
        Root / "title.md" -> Contents.name,
        Root / "cover.md" -> Contents.name
      )
      transformTree.assertEquals(RenderedTreeViewRoot(
        root(List(
          TitleDocument(RenderedDocumentView(Root / "title.txt", simpleResult)),
          docs((Root / "name.txt", simpleResult))
        )),
        Some(RenderedDocumentView(Root / "cover.txt", simpleResult))
      ))
    }

    "transform a tree with a template document populated by a config file in the directory" in new TreeTransformer {
      val inputs = Seq(
        Root / "default.template.txt" -> Contents.templateConfigRef,
        Root / "directory.conf" -> Contents.conf,
        Root / "main.md" -> Contents.aa
      )
      val result =
        """RootElement - Blocks: 1
          |. TemplateRoot - TemplateSpans: 2
          |. . EmbeddedRoot(0) - Blocks: 1
          |. . . Paragraph - Spans: 1
          |. . . . Text - 'aa'
          |. . TemplateString - 'abc'""".stripMargin
      transformTree.assertTree(root(List(docs((Root / "main.txt", result)))))
    }

    "transform a tree with a template document populated by a root config string" in new TreeTransformer {
      val inputs = Seq(
        Root / "default.template.txt" -> Contents.templateConfigRef,
        Root / "main.md" -> Contents.aa
      )
      val result =
        """RootElement - Blocks: 1
          |. TemplateRoot - TemplateSpans: 2
          |. . EmbeddedRoot(0) - Blocks: 1
          |. . . Paragraph - Spans: 1
          |. . . . Text - 'aa'
          |. . TemplateString - 'def'""".stripMargin
      transformWithConfig("value: def").assertTree(root(List(docs((Root / "main.txt", result)))))
    }

    "transform a tree with a custom template engine" in new TreeTransformer {
      val inputs = Seq(
        Root / "default.template.txt" -> Contents.template1,
        Root / "main1.md" -> Contents.aa,
        Root / "main2.md" -> Contents.aa
      )
      val parser: Parser[TemplateRoot] = OperationConfig.default.templateParser.get.map(root => root.copy(root.content :+ TemplateString("cc")))
      val result =
        """RootElement - Blocks: 1
          |. TemplateRoot - TemplateSpans: 2
          |. . EmbeddedRoot(0) - Blocks: 1
          |. . . Paragraph - Spans: 1
          |. . . . Text - 'aa'
          |. . TemplateString - 'cc'""".stripMargin
      transformWithTemplates(parser).assertTree(root(List(docs(
        (Root / "main1.txt", result),
        (Root / "main2.txt", result)
      ))))
    }

    "transform a tree with a custom style sheet engine" in new TreeTransformer {
      // the AST renderer does not use stylesheets, so we must use XSL-FO here
      def styleDecl (fontSize: String) =
        StyleDeclaration(StylePredicate.ElementType("Paragraph"), "font-size" -> s"${fontSize}pt")

      val parser: Parser[Set[StyleDeclaration]] = TextParsers.any ^^ { n => Set(styleDecl(n)) }

      val inputs = Seq(
        Root / "doc1.md" -> Contents.name,
        Root / "styles.fo.css" -> Contents.style
      )

      val result = RenderResult.fo.withDefaultTemplate("""<fo:block font-family="serif" font-size="13pt" space-after="3mm">foo</fo:block>""")
      val transformer = Transformer.from(Markdown).to(XSLFO).using(BundleProvider.forStyleSheetParser(parser)).io(blocker).parallel[IO].build
      val renderResult = transformer
        .fromInput(IO.pure(input(inputs, transformer.config.docTypeMatcher)))
        .toOutput(StringTreeOutput)
        .transform
        .map(OutputBuilder.RenderedTreeView.toTreeView[IO])
      renderResult.map(_.tree).assertEquals(root(List(docs(
        (Root / "doc1.fo", result)
      ))))
    }

    "transform a tree with a template directive" in new TreeTransformer {

      import Templates.dsl._

      val directive = Templates.create("foo") {
        defaultAttribute.as[String] map {
          TemplateString(_)
        }
      }

      val inputs = Seq(
        Root / "default.template.txt" -> Contents.directive,
        Root / "aa.md" -> Contents.aa
      )
      val result =
        """RootElement - Blocks: 1
          |. TemplateRoot - TemplateSpans: 4
          |. . EmbeddedRoot(0) - Blocks: 1
          |. . . Paragraph - Spans: 1
          |. . . . Text - 'aa'
          |. . TemplateString - ' '
          |. . TemplateString - 'bar'
          |. . TemplateString - ' bb'""".stripMargin
      transformWithDirective(directive).assertTree(root(List(docs(
        (Root / "aa.txt", result)
      ))))
    }

    "transform a tree with a static document" in new TreeTransformer {
      val inputs = Seq(
        Root / "omg.js" -> Contents.name
      )
      transformTree.assertEquals(RenderedTreeViewRoot(RenderedTreeView(Root, Nil), staticDocuments = Seq(Root / "omg.js")))
    }

    "transform a tree with all available file types" in new TreeTransformer {
      val inputs = Seq(
        Root / "doc1.md" -> Contents.link,
        Root / "doc2.md" -> Contents.link,
        Root / "default.template.txt" -> Contents.template1,
        Root / "dir1" / "default.template.txt" -> Contents.template2,
        Root / "dir1" / "doc3.md" -> Contents.name,
        Root / "dir1" / "doc4.md" -> Contents.name,
        Root / "dir2" / "omg.js" -> Contents.name,
        Root / "dir2" / "doc5.md" -> Contents.name,
        Root / "dir2" / "doc6.md" -> Contents.name,
      )

      val withTemplate1 =
        """RootElement - Blocks: 1
          |. Paragraph - Spans: 1
          |. . Text - 'foo'""".stripMargin
      val withTemplate2 =
        """RootElement - Blocks: 1
          |. TemplateRoot - TemplateSpans: 3
          |. . TemplateString - '('
          |. . EmbeddedRoot(0) - Blocks: 1
          |. . . Paragraph - Spans: 1
          |. . . . Text - 'foo'
          |. . TemplateString - ')'""".stripMargin
      val markdown =
        """RootElement - Blocks: 1
          |. Paragraph - Spans: 1
          |. . ExternalLink(foo,None) - Spans: 1
          |. . . Text - 'link'""".stripMargin
      transformTree.assertEquals(RenderedTreeViewRoot(root(List(
        docs(
          (Root / "doc1.txt", markdown),
          (Root / "doc2.txt", markdown)
        ),
        trees(
          (Root / "dir1", List(docs(
            (Root / "dir1" / "doc3.txt", withTemplate2),
            (Root / "dir1" / "doc4.txt", withTemplate2)
          ))),
          (Root / "dir2", List(docs(
            (Root / "dir2" / "doc5.txt", withTemplate1),
            (Root / "dir2" / "doc6.txt", withTemplate1),
          )))
        )
      )), staticDocuments = Seq(Root / "dir2" / "omg.js")))
    }

    trait TwoPhaseTransformer extends InputBuilder {

      val srcRoot: String =
        """Title
          |=====
          |
          |bbb""".stripMargin

      val srcSub: String =
        """Sub Title
          |=========
          |
          |ccc""".stripMargin

      val contents: Map[String, String] = Map(
        "docRoot" -> srcRoot,
        "docSub" -> srcSub
      )

      val inputs: Seq[(Path, String)] = Seq(
        Root / "docRoot.rst" -> srcRoot,
        Root / "dir" / "docSub.rst" -> srcSub
      )

      val dirs: String =
        """- docRoot.rst:docRoot
          |+ dir
          |  - docSub.rst:docSub""".stripMargin

      val expectedResult: String =
        """RootElement - Blocks: 2
          |. Title(Id(title) + Styles(title)) - Spans: 1
          |. . Text - 'Title'
          |. Paragraph - Spans: 1
          |. . Text - 'bbb'
          |RootElement - Blocks: 2
          |. Title(Id(sub-title) + Styles(title)) - Spans: 1
          |. . Text - 'Sub Title'
          |. Paragraph - Spans: 1
          |. . Text - 'ccc'
          |""".stripMargin

      def input (in: Seq[(Path, String)], docTypeMatcher: Path => DocumentType): TreeInput[IO] = build(in, docTypeMatcher)
    }

    "render a tree with a RenderResultProcessor writing to an output stream" in new TwoPhaseTransformer {
      val transformer = Transformer.from(ReStructuredText).to(TestRenderResultProcessor)
      def transformTo(out: IO[OutputStream]) = transformer
        .io(blocker)
        .parallel[IO]
        .build
        .fromInput(IO.pure(input(inputs, transformer.build.markupParser.config.docTypeMatcher)))
        .toStream(out)
        .transform

      withByteArrayTextOutput { out =>
        transformTo(IO.pure(out))
      }.assertEquals(expectedResult)
    }

    "render a tree with a RenderResultProcessor writing to a file" in new TwoPhaseTransformer {
      val transformer = Transformer.from(ReStructuredText).to(TestRenderResultProcessor)
      def transformTo(f: File) = transformer
        .io(blocker)
        .parallel[IO]
        .build
        .fromInput(IO.pure(input(inputs, transformer.build.markupParser.config.docTypeMatcher)))
        .toFile(f)
        .transform

      val res = for {
        f   <- IO(File.createTempFile("output", null))
        _   <- transformTo(f)
        res <- readFile(f)
      } yield res.toString

      res.assertEquals(expectedResult)
    }

    trait FileSystemTest {

      import cats.implicits._

      def resourcePath (path: String): String = getClass.getResource(path).getFile

      def renderedDynDoc (num: Int): String =
        """RootElement - Blocks: 1
          |. TemplateRoot - Spans: 1
          |. . TemplateString - 'Doc""".stripMargin + num + "'"

      def renderedDoc (num: Int): String =
        """RootElement - Blocks: 1
          |. Paragraph - Spans: 1
          |. . Text - 'Doc""".stripMargin + num + "'"

      def readFiles (base: String): IO[List[String]] = List(
        readFile(base + "/doc1.txt"),
        readFile(base + "/doc2.txt"),
        readFile(base + "/dir1/doc3.txt"),
        readFile(base + "/dir1/doc4.txt"),
        readFile(base + "/dir2/doc5.txt"),
        readFile(base + "/dir2/doc6.txt"),
      ).sequence

      def readFilesFiltered (base: String): IO[List[String]] = List(
        readFile(base + "/doc2.txt"),
        readFile(base + "/dir2/doc5.txt"),
        readFile(base + "/dir2/doc6.txt"),
      ).sequence

      def readFilesMerged (base: String): IO[List[String]] = List(
        readFile(base + "/doc1.txt"),
        readFile(base + "/doc2.txt"),
        readFile(base + "/dir1/doc3.txt"),
        readFile(base + "/dir1/doc4.txt"),
        readFile(base + "/dir2/doc5.txt"),
        readFile(base + "/dir2/doc6.txt"),
        readFile(base + "/dir1/doc7.txt"),
        readFile(base + "/dir3/doc8.txt"),
        readFile(base + "/doc9.txt"),
      ).sequence
    }

    "read from and write to directories" in new FileSystemTest {
      val sourceName = resourcePath("/trees/a/")
      val expectedFileContents = (1 to 6).map(renderedDoc).toList
      val res = for {
        targetDir <- newTempDirectory
        _         <- transformer.fromDirectory(sourceName).toDirectory(targetDir).transform
        results   <- readFiles(targetDir.getPath)
      } yield results
      res.assertEquals(expectedFileContents)
    }

    "transform a directory with a custom document type matcher" in new FileSystemTest {
      val sourceName = resourcePath("/trees/a/")
      val transformer = transformerWithBundle(BundleProvider.forDocTypeMatcher { 
        case Root / "doc1.md" => Ignored; case Root / "dir1" / _ => Ignored 
      })
      val expectedFileContents = List(2,5,6).map(renderedDoc)
      
      val res = for {
        targetDir  <- newTempDirectory
        _          <- transformer.fromDirectory(sourceName).toDirectory(targetDir).transform
        contents   <- readFilesFiltered(targetDir.getPath)
        fileExists <- exists(new File(targetDir + "/doc1.txt"))
        dirExists  <- exists(new File(targetDir + "/dir1"))
      } yield (contents, fileExists, dirExists)
      
      res.assertEquals((expectedFileContents, false, false))
    }

    "allow to specify custom exclude filter" in new FileSystemTest {
      val sourceName = resourcePath("/trees/a/")
      val fileFilter = { f: File => f.getName == "doc1.md" || f.getName == "dir1" }
      val expectedFileContents = List(2,5,6).map(renderedDoc)
      
      val res = for {
        targetDir  <- newTempDirectory
        _          <- transformer.fromDirectory(sourceName, fileFilter).toDirectory(targetDir).transform
        contents   <- readFilesFiltered(targetDir.getPath)
        fileExists <- exists(new File(targetDir + "/doc1.txt"))
        dirExists  <- exists(new File(targetDir + "/dir1"))
      } yield (contents, fileExists, dirExists)

      res.assertEquals((expectedFileContents, false, false))
    }

    "read from two root directories" in new FileSystemTest {
      val source1 = new File(resourcePath("/trees/a/"))
      val source2 = new File(resourcePath("/trees/b/"))
      val expectedFileContents = (1 to 9).map(renderedDoc).toList
      val res = for {
        targetDir <- newTempDirectory
        _         <- transformer.fromDirectories(Seq(source1, source2)).toDirectory(targetDir).transform
        results   <- readFilesMerged(targetDir.getPath)
      } yield results 
      res.assertEquals(expectedFileContents)
    }

    "allow to use the same directory as input and output" in new FileSystemTest {

      val result =
        """RootElement - Blocks: 1
          |. Paragraph - Spans: 1
          |. . Text - 'Hello'""".stripMargin
      
      val res = for {
        targetDir  <- newTempDirectory
        staticFile = new File(targetDir, "static.txt")
        inputFile  = new File(targetDir, "hello.md")
        _          <- writeFile(inputFile, "Hello")
        _          <- writeFile(staticFile, "Text")
        _          <- transformer.fromDirectory(targetDir).toDirectory(targetDir).transform
        hello      <- readFile(inputFile)
        static     <- readFile(staticFile)
        result     <- readFile(new File(targetDir, "hello.txt"))
      } yield (hello, static, result)

      res.assertEquals(("Hello", "Text", result))
    }

    "not copy files from the output directory if it's nested inside the input directory" in {
      new FileSystemTest {

        val result =
          """RootElement - Blocks: 1
            |. Paragraph - Spans: 1
            |. . Text - 'Hello'""".stripMargin

        val res = for {
          targetDir  <- newTempDirectory
          staticFile = new File(targetDir, "static.txt")
          inputFile  = new File(targetDir, "hello.md")
          subdir     = new File(targetDir, "sub")
          _          <- writeFile(inputFile, "Hello")
          _          <- writeFile(staticFile, "Text")
          _          <- mkDir(subdir)
          outputFile = new File(subdir, "hello.js")
          _          <- writeFile(outputFile, "Output")
          _          <- transformer.fromDirectory(targetDir).toDirectory(subdir).transform
          hello      <- readFile(inputFile)
          static     <- readFile(new File(subdir, "static.txt"))
          result     <- readFile(new File(subdir, "hello.txt"))
          subExists  <- exists(new File(subdir, "sub"))
        } yield (hello, static, result, subExists)

        res.assertEquals(("Hello", "Text", result, false))
      }
    }
  }

}
