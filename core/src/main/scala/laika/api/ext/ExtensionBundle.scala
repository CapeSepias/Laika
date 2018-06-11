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

package laika.api.ext

import com.typesafe.config.{Config, ConfigFactory}
import laika.factory.RendererFactory
import laika.io.{DocumentType, InputProvider}
import laika.parse.core.Parser
import laika.parse.core.markup.RecursiveParsers
import laika.parse.css.ParseStyleSheet
import laika.rewrite.DocumentCursor
import laika.tree.Documents.TemplateDocument
import laika.tree.Elements._
import laika.tree.Paths.Path

/**
  * @author Jens Halm
  */
trait ExtensionBundle { self =>

  def baseConfig: Config = ConfigFactory.empty

  def docTypeMatcher: PartialFunction[Path, DocumentType] = PartialFunction.empty

  def parserDefinitions: ParserDefinitionBuilders = ParserDefinitionBuilders()

  def rewriteRules: Seq[DocumentCursor => RewriteRule] = Seq.empty

  def themeFor[Writer] (rendererFactory: RendererFactory[Writer]): Theme[Writer] = Theme[Writer]()

  // for providing APIs like registering Directives
  def processExtension: PartialFunction[ExtensionBundle, ExtensionBundle] = PartialFunction.empty

  def withBase (bundle: ExtensionBundle): ExtensionBundle = new ExtensionBundle {

    override def baseConfig = self.baseConfig.withFallback(bundle.baseConfig)

    override def docTypeMatcher = self.docTypeMatcher.orElse(bundle.docTypeMatcher)

    override def parserDefinitions: ParserDefinitionBuilders = self.parserDefinitions ++ bundle.parserDefinitions

    override def rewriteRules = self.rewriteRules ++ bundle.rewriteRules

    override def themeFor[Writer] (rendererFactory: RendererFactory[Writer]) =
      self.themeFor(rendererFactory).withBase(bundle.themeFor(rendererFactory))

    override def processExtension: PartialFunction[ExtensionBundle, ExtensionBundle] =
      self.processExtension.orElse(bundle.processExtension)
  }

}

object ExtensionBundle {

  object Empty extends ExtensionBundle

}

trait ExtensionFactory {

  def create (recursiveParsers: RecursiveParsers): ExtensionBundle

}

case class ParserDefinitionBuilders(blockParsers: Seq[ParserDefinitionBuilder[Block]] = Nil,
                                    spanParsers: Seq[ParserDefinitionBuilder[Span]] = Nil,
                                    configHeaderParsers: Seq[Parser[Either[InvalidBlock, Config]]] = Nil) { // TODO - does this belong here or in DirectiveSupport?

  def ++ (builders: ParserDefinitionBuilders): ParserDefinitionBuilders =
    ParserDefinitionBuilders(blockParsers ++ builders.blockParsers,
      spanParsers ++ builders.spanParsers, configHeaderParsers ++ builders.configHeaderParsers)

}

trait ParserDefinitionBuilder[T] {

  def createParser (recursiveParsers: RecursiveParsers): ParserDefinition[T]

}

case class ParserDefinition[T] (startChar: Option[Char],
                                parser: Parser[T],
                                isRecursive: Boolean,
                                useInRecursion: Boolean,
                                precedence: Precedence)

sealed trait Precedence
object Precedence {
  object High extends Precedence
  object Low extends Precedence
}

case class Theme[Writer] (customRenderers: Seq[Writer => RenderFunction] = Nil,
                          config: Config = ConfigFactory.empty,
                          defaultTemplate: Option[TemplateDocument] = None,
                          styleSheetParser: Option[ParseStyleSheet] = None /*,
                          staticDocuments: InputProvider = InputProvider.empty TODO - implement */) {

  def withBase(other: Theme[Writer]): Theme[Writer] = Theme(
    customRenderers ++ other.customRenderers,
    config.withFallback(other.config),
    defaultTemplate.orElse(other.defaultTemplate),
    styleSheetParser.orElse(other.styleSheetParser)/*,
    staticDocuments.merge(other.staticDocuments TODO - implement + simplify InputProvider and related types */
  )

}
