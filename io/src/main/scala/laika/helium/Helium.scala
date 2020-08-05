/*
 * Copyright 2012-2020 the original author or authors.
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

package laika.helium

import cats.data.Kleisli
import cats.effect.Sync
import laika.ast.LengthUnit.{cm, mm, pt, px}
import laika.ast.Path.Root
import laika.ast._
import laika.bundle.{BundleOrigin, ExtensionBundle, Precedence}
import laika.config.{Config, ConfigBuilder, ConfigEncoder, LaikaKeys}
import laika.factory.Format
import laika.format.{EPUB, HTML, XSLFO}
import laika.helium.generate._
import laika.io.config.SiteConfig
import laika.io.model.{InputTree, ParsedTree}
import laika.io.theme.Theme
import laika.rewrite.DefaultTemplatePath
import laika.rewrite.nav.{ChoiceConfig, ChoiceGroupsConfig, CoverImage, TitleDocumentConfig}

/**
  * @author Jens Halm
  */
case class Helium (fontResources: Seq[FontDefinition],
                   themeFonts: ThemeFonts,
                   fontSizes: FontSizes,
                   colors: ColorSet,
                   landingPage: Option[LandingPage],
                   webLayout: WebLayout,
                   pdfLayout: PDFLayout) {
  
  /*
  def withFontFamilies (body: String, header: String, code: String) = withFontFamilies(EPUB, PDF, HTML)(...)
  def withFontFamilies (format: RenderFormat[_], formats: RenderFormat[_]*)(body: String, header: String, code: String)
  */
  
  def build[F[_]: Sync]: Theme[F] = {
    
    val themeInputs = InputTree[F]
      .addTemplate(TemplateDocument(DefaultTemplatePath.forHTML, new HTMLTemplate(this).root))
      .addTemplate(TemplateDocument(DefaultTemplatePath.forEPUB, EPUBTemplate.default))
      .addTemplate(TemplateDocument(DefaultTemplatePath.forFO, new FOTemplate(this).root))
      .addStyles(new FOStyles(this).styles.styles , FOStyles.defaultPath, Precedence.Low)
      .addClasspathResource("laika/helium/css/container.css", Root / "css" / "container.css")
      .addClasspathResource("laika/helium/css/content.css", Root / "css" / "content.css")
      .addClasspathResource("laika/helium/css/nav.css", Root / "css" / "nav.css")
      .addClasspathResource("laika/helium/css/code.css", Root / "css" / "code.css")
      .addClasspathResource("laika/helium/css/toc.css", Root / "css" / "toc.css")
      .addString(CSSVarGenerator.generate(this), Root / "css" / "vars.css")
      .build
    
    def estimateLines (blocks: Seq[Block]): Int = blocks.collect {
      case sp: SpanContainer => sp.extractText.length
      case bc: BlockContainer => estimateLines(bc.content) // TODO - handle lists and tables
    }.sum
    
    val rewriteRule: RewriteRules = RewriteRules.forBlocks {
      case cb: CodeBlock if cb.extractText.count(_ == '\n') <= pdfLayout.keepTogetherDecoratedLines =>
        Replace(cb.mergeOptions(Style.keepTogether))
      case bs: BlockSequence if bs.options.styles.contains("callout") && estimateLines(bs.content) <= pdfLayout.keepTogetherDecoratedLines =>
        Replace(bs.mergeOptions(Style.keepTogether))
    }
    
    implicit val releaseEncoder: ConfigEncoder[ReleaseInfo] = ConfigEncoder[ReleaseInfo] { releaseInfo =>
      ConfigEncoder.ObjectBuilder.empty
        .withValue("title", releaseInfo.title)
        .withValue("version", releaseInfo.version)
        .build
    }

    implicit val linkEncoder: ConfigEncoder[LandingPageLink] = ConfigEncoder[LandingPageLink] { link =>
      ConfigEncoder.ObjectBuilder.empty
        .withValue("text", link.text)
        .withValue("version", link match { case e: ExternalLink => e.target; case i: InternalLink => i.target.toString })
        .build
    }
    
    implicit val teaserEncoder: ConfigEncoder[Teaser] = ConfigEncoder[Teaser] { teaser =>
      ConfigEncoder.ObjectBuilder.empty
        .withValue("title", teaser.title)
        .withValue("description", teaser.description)
        .build
    }
    
    implicit val landingPageEncoder: ConfigEncoder[LandingPage] = ConfigEncoder[LandingPage] { landingPage =>
      ConfigEncoder.ObjectBuilder.empty
        .withValue("logo", landingPage.logo)
        .withValue("title", landingPage.title)
        .withValue("subtitle", landingPage.subtitle)
        .withValue("latestReleases", landingPage.latestReleases)
        .withValue("license", landingPage.license)
        .withValue("documentationLinks", landingPage.documentationLinks)
        .withValue("projectLinks", landingPage.projectLinks)
        .withValue("teasers", landingPage.teasers) // TODO - change to teaserRows
        .build
    }
    
    val landingPageConfig: Config = landingPage.fold(ConfigBuilder.empty.build) { pageConfig =>
      ConfigBuilder.empty
        .withValue("helium.landingPage", pageConfig)
        .build
    }
    
    val bundle: ExtensionBundle = new ExtensionBundle {
      override val origin: BundleOrigin = BundleOrigin.Theme
      val description = "Helium Theme Rewrite Rules"
      override val rewriteRules: Seq[DocumentCursor => RewriteRules] = Seq(_ => rewriteRule)
      override val renderOverrides = Seq(HTML.Overrides(HeliumRenderOverrides.create(webLayout.anchorPlacement)))
      override val baseConfig: Config = landingPageConfig
    }
    
    def addToc (format: Format): Kleisli[F, ParsedTree[F], ParsedTree[F]] = Kleisli { tree =>
      val toc = format match {
        case HTML => webLayout.tableOfContent
        case EPUB.XHTML => webLayout.tableOfContent // TODO - create EPUBLayout
        case XSLFO => pdfLayout.tableOfContent
        case _ => None
      }
      val result = toc.fold(tree) { tocConf =>
        if (tocConf.depth < 1) tree
        else {
          val navContext = NavigationBuilderContext(
            refPath = Root, 
            itemStyles = Set("toc"), 
            maxLevels = tocConf.depth,
            currentLevel = 0)
          val navItem = tree.root.tree.asNavigationItem(navContext)
          val navList = NavigationList(navItem.content, Styles("toc"))
          val title = Title(tocConf.title)
          val root = RootElement(title, navList)
          val doc = Document(Root / "table-of-contents", root)
          val oldTree = tree.root.tree
          val newTree = tree.copy(root = tree.root.copy(tree = oldTree.copy(content = doc +: oldTree.content)))
          newTree
        } 
      }
      Sync[F].pure(result)
    }
    
    def addLandingPage: Kleisli[F, ParsedTree[F], ParsedTree[F]] = landingPage.fold(Kleisli.ask[F, ParsedTree[F]]) { _ => 
      Kleisli { tree =>
        val landingPageContent = tree.root.tree.content.collectFirst { 
          case d: Document if d.path.withoutSuffix.name == "landing-page" => d.content 
        }.getOrElse(RootElement.empty)
        val titleDocument = tree.root.titleDocument.fold(
          Document(Root / TitleDocumentConfig.inputName(tree.root.config), landingPageContent)
        ) { titleDoc =>
          titleDoc.copy(content = RootElement(titleDoc.content.content ++ landingPageContent.content))
        }
        val titleDocWithTemplate = 
          if (titleDocument.config.hasKey(LaikaKeys.template)) titleDocument
          else titleDocument.copy(config = titleDocument.config.withValue(LaikaKeys.template, "landing-template.html").build)
        Sync[F].pure(tree.copy(root = tree.root.copy(tree = tree.root.tree.copy(titleDocument = Some(titleDocWithTemplate)))))
      }
    }

    def addDownloadPage: Kleisli[F, ParsedTree[F], ParsedTree[F]] = webLayout.downloadPage
      .filter(p => p.includeEPUB || p.includePDF)
      .fold(Kleisli.ask[F, ParsedTree[F]]) { pageConfig =>

        val refPath: Path = Root / "downloads"
        
        def downloadAST (link: Path, title: String, coverImage: Option[Path]): TitledBlock = TitledBlock(Seq(
          Text(title)
        ), coverImage.map(img => Paragraph(Image(title, InternalTarget.fromPath(img, refPath)))).toSeq ++ Seq(
          Paragraph(SpanLink(Seq(Text("Download")), InternalTarget.fromPath(link, refPath)))
        ))
        
        Kleisli { tree =>

          val classifiedCoverImages = tree.root.config.get[Seq[CoverImage]](LaikaKeys.coverImages).getOrElse(Nil)
          val defaultCoverImage = tree.root.config.getOpt[Path](LaikaKeys.coverImage).toOption.flatten
            .orElse(classifiedCoverImages.find(_.classifier.isEmpty).map(_.path))
          val classifiedCoverMap = classifiedCoverImages
            .collect { case CoverImage(path, Some(classifier)) => (classifier, path) }
            .toMap
          
          val artifactBaseName = tree.root.config.get[String](LaikaKeys.artifactBaseName).getOrElse("download")
          val downloadPath = SiteConfig.downloadPath(tree.root.config)
          
          val combinations: Seq[Seq[ChoiceConfig]] = ChoiceGroupsConfig
            .createChoiceCombinationsConfig(tree.root.config)
          val downloads: Seq[Block] = combinations.map { combination =>
            val baseTitle = combination.map(_.label).mkString(" - ")
            val classifier = combination.map(_.name).mkString("-")
            val coverImage = classifiedCoverMap.get(classifier).orElse(defaultCoverImage)
            val epubLink = downloadPath / s"$artifactBaseName-$classifier.epub"
            val pdfLink = downloadPath / s"$artifactBaseName-$classifier.pdf"
            BlockSequence(
              downloadAST(epubLink, baseTitle + " (EPUB)", coverImage),
              downloadAST(pdfLink, baseTitle + " (PDF)", coverImage)
            ).withOptions(Styles("downloads"))
          }
          val blocks = Title(pageConfig.title) +: pageConfig.description.map(Paragraph(_)).toSeq ++: downloads
          val doc = Document(Root / "downloads", RootElement(blocks))
          Sync[F].pure(tree.copy(
            root = tree.root.copy(
              tree = tree.root.tree.copy(
                content = doc +: tree.root.tree.content,
              )
            )
          ))
        }
      }
    
    new Theme[F] {
      def inputs = themeInputs
      def extensions = Seq(bundle)
      def treeProcessor = { 
        case HTML => addDownloadPage.andThen(addToc(HTML)).andThen(addLandingPage)
        case format => addToc(format)
      }
    }
  } 
  
}

object Helium {
  
  // TODO - separate values per format where necessary
  val defaults: Helium = Helium(
    Seq(
      
    ), // TODO - define
    ThemeFonts("Lato", "Lato", "FiraCode"),
    FontSizes(
      body = pt(10),
      code = pt(9),
      title = pt(24),
      header2 = pt(14),
      header3 = pt(12),
      header4 = pt(11),
      small = pt(8)
    ),
    ColorSet(
      primary = Color.hex("007c99"),
      secondary = Color.hex("931813"),
      primaryDark = Color.hex("007c99"),
      primaryLight = Color.hex("ebf6f7"),
      messages = MessageColors(
        info = Color.hex("007c99"),
        infoLight = Color.hex("ebf6f7"),
        warning = Color.hex("b1a400"),
        warningLight = Color.hex("fcfacd"),
        error = Color.hex("d83030"),
        errorLight = Color.hex("ffe9e3"),
      ),
      syntaxHighlighting = SyntaxColors(
        base = ColorQuintet(
          Color.hex("F6F1EF"), Color.hex("AF9E84"), Color.hex("937F61"), Color.hex("645133"), Color.hex("362E21")
        ),
        wheel = ColorQuintet(
          Color.hex("9A6799"), Color.hex("9F4C46"), Color.hex("A0742D"), Color.hex("7D8D4C"), Color.hex("6498AE")
        )
      )
    ),
    None,
    WebLayout(
      contentWidth = px(860), 
      navigationWidth = px(275),
      defaultBlockSpacing = px(10),
      defaultLineHeight = 1.5, 
      anchorPlacement = AnchorPlacement.Left),
    PDFLayout(
      pageWidth = cm(21), 
      pageHeight = cm(29.7), 
      marginTop = cm(1),
      marginRight = cm(2.5),
      marginBottom = cm(1),
      marginLeft = cm(2.5),
      defaultBlockSpacing = mm(3),
      defaultLineHeight = 1.5,
      keepTogetherDecoratedLines = 12
    )
  )
  
}
