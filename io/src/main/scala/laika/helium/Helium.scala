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
import cats.effect.{Async, IO}
import laika.ast.LengthUnit.{cm, mm, pt, px}
import laika.ast.Path.Root
import laika.ast.{Block, BlockContainer, BlockSequence, CodeBlock, DocumentCursor, Replace, RewriteRules, SpanContainer, Style, TemplateDocument}
import laika.bundle.{BundleOrigin, ExtensionBundle, Precedence}
import laika.format.HTML
import laika.helium.generate.{CSSVarGenerator, EPUBTemplate, FOStyles, FOTemplate, HTMLTemplate}
import laika.io.model.InputTree
import laika.io.theme.Theme

/**
  * @author Jens Halm
  */
case class Helium (fontResources: Seq[FontDefinition],
                   themeFonts: ThemeFonts,
                   fontSizes: FontSizes,
                   colors: ColorSet,
                   landingPage: LandingPage,
                   webLayout: WebLayout,
                   pdfLayout: PDFLayout) {
  
  /*
  def withFontFamilies (body: String, header: String, code: String) = withFontFamilies(EPUB, PDF, HTML)(...)
  def withFontFamilies (format: RenderFormat[_], formats: RenderFormat[_]*)(body: String, header: String, code: String)
  */
  
  def build[F[_]: Async]: Theme[F] = {
    
    val themeInputs = InputTree[F]
      .addTemplate(TemplateDocument(Root / "default.template.html", new HTMLTemplate(this).root))
      .addTemplate(TemplateDocument(Root / "default.template.epub.xhtml", EPUBTemplate.default))
      .addTemplate(TemplateDocument(Root / "default.template.fo", new FOTemplate(this).root))
      .addStyles(new FOStyles(this).styles.styles , Root / "styles.fo.css", Precedence.Low)
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
    
    val bundle: ExtensionBundle = new ExtensionBundle {
      override def origin: BundleOrigin = BundleOrigin.Theme
      def description = "Helium Theme Rewrite Rules"
      override def rewriteRules: Seq[DocumentCursor => RewriteRules] = Seq(_ => rewriteRule)
      override def renderOverrides = Seq(HTML.Overrides(HeliumRenderOverrides.create(webLayout.anchorPlacement)))
    }
    
    new Theme[F] {
      def inputs = themeInputs
      def extensions = Seq(bundle)
      def treeTransformer = Kleisli(Async[F].pure)
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
    LandingPage(),
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
