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

import java.util.Date

import cats.effect.{Resource, Sync}
import laika.ast.Path.Root
import laika.ast._
import laika.helium.Helium.{CommonConfigOps, SingleConfigOps}
import laika.helium.builder.HeliumThemeBuilder
import laika.helium.config._
import laika.theme._

/**
  * @author Jens Halm
  */
class Helium private[laika] (private[laika] val siteSettings: Helium.SiteSettings,
                             private[laika] val epubSettings: Helium.EPUBSettings,
                             private[laika] val pdfSettings: Helium.PDFSettings) { self =>
  
  object site extends SingleConfigOps {
    def fontResources (defn: FontDefinition*): Helium = 
      new Helium(siteSettings.copy(fontResources = defn), epubSettings, pdfSettings)
    protected def currentColors: ColorSet = siteSettings.colors
    protected def withFontFamilies (fonts: ThemeFonts): Helium =
      new Helium(siteSettings.copy(themeFonts = fonts), epubSettings, pdfSettings)
    protected def withFontSizes (sizes: FontSizes): Helium =
      new Helium(siteSettings.copy(fontSizes = sizes), epubSettings, pdfSettings)
    protected def withColors (colors: ColorSet): Helium =
      new Helium(siteSettings.copy(colors = colors), epubSettings, pdfSettings)
    protected def withMetadata (metadata: DocumentMetadata): Helium =
      new Helium(siteSettings.copy(metadata = metadata), epubSettings, pdfSettings)

    def autoLinkCSS (paths: Path*): Helium = 
      new Helium(siteSettings.copy(htmlIncludes = siteSettings.htmlIncludes.copy(includeCSS = paths)), epubSettings, pdfSettings)
    def autoLinkJS (paths: Path*): Helium =
      new Helium(siteSettings.copy(htmlIncludes = siteSettings.htmlIncludes.copy(includeJS = paths)), epubSettings, pdfSettings)
    
    def layout (contentWidth: Size,
                navigationWidth: Size,
                defaultBlockSpacing: Size,
                defaultLineHeight: Double,
                anchorPlacement: AnchorPlacement): Helium = {
      val layout = siteSettings.webLayout.copy(
        contentWidth = contentWidth,
        navigationWidth = navigationWidth,
        defaultBlockSpacing = defaultBlockSpacing,
        defaultLineHeight = defaultLineHeight,
        anchorPlacement = anchorPlacement
      )
      new Helium(siteSettings.copy(webLayout = layout), epubSettings, pdfSettings)
    }
      
    def favIcons (icons: Favicon*): Helium = {
      val newLayout = siteSettings.webLayout.copy(favIcons = icons)
      new Helium(siteSettings.copy(webLayout = newLayout), epubSettings, pdfSettings)
    }
    def topNavigationBar (logo: Option[Image], links: Seq[ThemeLink]): Helium = {
      val newLayout = siteSettings.webLayout.copy(topNavigationBar = TopNavigationBar(logo, links))
      new Helium(siteSettings.copy(webLayout = newLayout), epubSettings, pdfSettings)
    }
    def tableOfContent (title: String, depth: Int): Helium = {
      val newLayout = siteSettings.webLayout.copy(tableOfContent = Some(TableOfContent(title, depth)))
      new Helium(siteSettings.copy(webLayout = newLayout), epubSettings, pdfSettings)
    }
    def downloadPage (title: String, description: Option[String], downloadPath: Path = Root / "downloads",
                      includeEPUB: Boolean = true, includePDF: Boolean = true): Helium = {
      val newLayout = siteSettings.webLayout
        .copy(downloadPage = Some(DownloadPage(title, description, downloadPath, includeEPUB, includePDF)))
      new Helium(siteSettings.copy(webLayout = newLayout), epubSettings, pdfSettings)
    }
    def markupEditLinks (text: String, baseURL: String): Helium = {
      val newLayout = siteSettings.webLayout.copy(markupEditLinks = Some(MarkupEditLinks(text, baseURL)))
      new Helium(siteSettings.copy(webLayout = newLayout), epubSettings, pdfSettings)
    }
    
    def landingPage (logo: Option[Image] = None,
                     title: Option[String] = None,
                     subtitle: Option[String] = None,
                     latestReleases: Seq[ReleaseInfo] = Nil,
                     license: Option[String] = None,
                     documentationLinks: Seq[TextLink] = Nil,
                     projectLinks: Seq[ThemeLink] = Nil,
                     teasers: Seq[Teaser] = Nil): Helium = {
      val page = LandingPage(logo, title, subtitle, latestReleases, license, documentationLinks, projectLinks, teasers)
      new Helium(siteSettings.copy(landingPage = Some(page)), epubSettings, pdfSettings)
    }
  }
  
  object epub extends SingleConfigOps {
    def fontResources (defn: FontDefinition*): Helium =
      new Helium(siteSettings, epubSettings.copy(bookConfig = epubSettings.bookConfig.copy(fonts = defn)), pdfSettings)
    protected def currentColors: ColorSet = epubSettings.colors
    protected def withFontFamilies (fonts: ThemeFonts): Helium =
      new Helium(siteSettings, epubSettings.copy(themeFonts = fonts), pdfSettings)
    protected def withFontSizes (sizes: FontSizes): Helium =
      new Helium(siteSettings, epubSettings.copy(fontSizes = sizes), pdfSettings)
    protected def withColors (colors: ColorSet): Helium =
      new Helium(siteSettings, epubSettings.copy(colors = colors), pdfSettings)
    protected def withMetadata (metadata: DocumentMetadata): Helium =
      new Helium(siteSettings, epubSettings.copy(bookConfig = epubSettings.bookConfig.copy(metadata = metadata)), pdfSettings)
    
    def autoLinkCSS (paths: Path*): Helium =
      new Helium(siteSettings, epubSettings.copy(htmlIncludes = epubSettings.htmlIncludes.copy(includeCSS = paths)), pdfSettings)
    def autoLinkJS (paths: Path*): Helium = 
      new Helium(siteSettings, epubSettings.copy(htmlIncludes = epubSettings.htmlIncludes.copy(includeJS = paths)), pdfSettings)
  }
  
  object pdf extends SingleConfigOps {
    def fontResources (defn: FontDefinition*): Helium =
      new Helium(siteSettings, epubSettings, pdfSettings.copy(bookConfig = pdfSettings.bookConfig.copy(fonts = defn)))
    protected def currentColors: ColorSet = pdfSettings.colors
    protected def withFontFamilies (fonts: ThemeFonts): Helium =
      new Helium(siteSettings, epubSettings, pdfSettings.copy(themeFonts = fonts))
    protected def withFontSizes (sizes: FontSizes): Helium =
      new Helium(siteSettings, epubSettings, pdfSettings.copy(fontSizes = sizes))
    protected def withColors (colors: ColorSet): Helium =
      new Helium(siteSettings, epubSettings, pdfSettings.copy(colors = colors))
    protected def withMetadata (metadata: DocumentMetadata): Helium =
      new Helium(siteSettings, epubSettings, pdfSettings.copy(bookConfig = pdfSettings.bookConfig.copy(metadata = metadata)))
    
    def layout (pageWidth: Size, pageHeight: Size,
                marginTop: Size, marginRight: Size, marginBottom: Size, marginLeft: Size,
                defaultBlockSpacing: Size, defaultLineHeight: Double,
                keepTogetherDecoratedLines: Int,
                navigationDepth: Int,
                tableOfContent: Option[TableOfContent] = None,
                coverImage: Option[Path] = None): Helium =
      new Helium(siteSettings, epubSettings, pdfSettings.copy(pdfLayout = PDFLayout(
        pageWidth, pageHeight, marginTop, marginRight, marginBottom, marginLeft,
        defaultBlockSpacing, defaultLineHeight, keepTogetherDecoratedLines, tableOfContent
      )))
  }

  // TODO - extract
  object allFormats extends CommonConfigOps {
    private val formats: Seq[Helium => CommonConfigOps] = Seq(_.site, _.epub, _.pdf)
    def fontResources (defn: FontDefinition*): Helium = formats.foldLeft(self) {
      case (helium, format) => format(helium).fontResources(defn:_*)
    }
    def fontFamilies (body: String, headlines: String, code: String): Helium = formats.foldLeft(self) {
      case (helium, format) => format(helium).fontFamilies(body, headlines, code)
    }
    def fontSizes (body: Size,
                   code: Size,
                   title: Size,
                   header2: Size,
                   header3: Size,
                   header4: Size,
                   small: Size): Helium = formats.foldLeft(self) {
      case (helium, format) => format(helium).fontSizes(body, code, title, header2, header3, header4, small)
    }
    def themeColors (primary: Color,
                  primaryDark: Color,
                  primaryLight: Color,
                  secondary: Color): Helium = formats.foldLeft(self) {
      case (helium, format) => 
        format(helium).themeColors(primary, primaryDark, primaryLight, secondary)
    }
    def messageColors (info: Color,
                       infoLight: Color,
                       warning: Color,
                       warningLight: Color,
                       error: Color,
                       errorLight: Color): Helium = formats.foldLeft(self) {
      case (helium, format) =>
        format(helium).messageColors(info, infoLight, warning, warningLight, error, errorLight)
    }
    def syntaxHighlightingColors (base: ColorQuintet, wheel: ColorQuintet): Helium = formats.foldLeft(self) {
      case (helium, format) =>
        format(helium).syntaxHighlightingColors(base, wheel)
    }
    def metadata (title: Option[String] = None,
                  description: Option[String] = None,
                  identifier: Option[String] = None,
                  authors: Seq[String] = Nil,
                  language: Option[String] = None,
                  date: Option[Date] = None,
                  version: Option[String] = None): Helium = formats.foldLeft(self) {
      case (helium, format) =>
        format(helium).metadata(title, description, identifier, authors, language, date, version)
    }
  }
  
  def build[F[_]: Sync]: Resource[F, Theme[F]] = HeliumThemeBuilder.build(this)
  
}

object Helium {
  
//  trait CommonSettings {
//    def fontResources: Seq[FontDefinition]
//    def themeFonts: ThemeFonts
//    def fontSizes: FontSizes
//    def colors: ColorSet
//  }
  
  trait CommonConfigOps {
    
    def fontResources (defn: FontDefinition*): Helium
    def fontFamilies (body: String, headlines: String, code: String): Helium
    def fontSizes (body: Size, 
                   code: Size, 
                   title: Size, 
                   header2: Size, 
                   header3: Size, 
                   header4: Size, 
                   small: Size): Helium
    def themeColors (primary: Color,
                  primaryDark: Color,
                  primaryLight: Color,
                  secondary: Color): Helium
    def messageColors (info: Color,
                       infoLight: Color,
                       warning: Color,
                       warningLight: Color,
                       error: Color,
                       errorLight: Color): Helium
    def syntaxHighlightingColors (base: ColorQuintet, wheel: ColorQuintet): Helium
    
    def metadata (title: Option[String] = None,
                  description: Option[String] = None,
                  identifier: Option[String] = None,
                  authors: Seq[String] = Nil,
                  language: Option[String] = None,
                  date: Option[Date] = None,
                  version: Option[String] = None): Helium
  }
  
  private[laika] trait SingleConfigOps extends CommonConfigOps {
    protected def currentColors: ColorSet
    protected def withFontFamilies (fonts: ThemeFonts): Helium
    protected def withFontSizes (sizes: FontSizes): Helium
    protected def withColors (colors: ColorSet): Helium
    protected def withMetadata (metadata: DocumentMetadata): Helium

    def fontResources (defn: FontDefinition*): Helium
    def fontFamilies (body: String, headlines: String, code: String): Helium =
      withFontFamilies(ThemeFonts(body, headlines, code))
    def fontSizes (body: Size,
                   code: Size,
                   title: Size,
                   header2: Size,
                   header3: Size,
                   header4: Size,
                   small: Size): Helium =
      withFontSizes(FontSizes(body, code, title, header2, header3, header4, small))
    def themeColors (primary: Color,
                     primaryDark: Color,
                     primaryLight: Color,
                     secondary: Color): Helium = withColors(currentColors.copy(
      primary = primary, primaryDark = primaryDark, primaryLight = primaryLight
    ))
    def messageColors (info: Color,
                       infoLight: Color,
                       warning: Color,
                       warningLight: Color,
                       error: Color,
                       errorLight: Color): Helium = withColors(currentColors.copy(messages = 
      MessageColors(info, infoLight, warning, warningLight, error, errorLight)
    ))
    def syntaxHighlightingColors (base: ColorQuintet, wheel: ColorQuintet): Helium = 
      withColors(currentColors.copy(syntaxHighlighting =
        SyntaxColors(base, wheel)
      ))
    def metadata (title: Option[String] = None,
                  description: Option[String] = None,
                  identifier: Option[String] = None,
                  authors: Seq[String] = Nil,
                  language: Option[String] = None,
                  date: Option[Date] = None,
                  version: Option[String] = None): Helium =
      withMetadata(DocumentMetadata(title, description, identifier, authors, language, date, version))
  }
  

  case class SiteSettings (fontResources: Seq[FontDefinition],
                           themeFonts: ThemeFonts,
                           fontSizes: FontSizes,
                           colors: ColorSet,
                           htmlIncludes: HTMLIncludes,
                           landingPage: Option[LandingPage],
                           webLayout: WebLayout,
                           metadata: DocumentMetadata)
  
  case class PDFSettings (bookConfig: BookConfig,
                          themeFonts: ThemeFonts,
                          fontSizes: FontSizes,
                          colors: ColorSet,
                          pdfLayout: PDFLayout)
  
  case class EPUBSettings (bookConfig: BookConfig,
                           themeFonts: ThemeFonts,
                           fontSizes: FontSizes,
                           colors: ColorSet,
                           htmlIncludes: HTMLIncludes)
  
  val defaults: Helium = HeliumDefaults.instance
    
}

object HeliumStyles {
  val button: Options = Styles("button")
}
