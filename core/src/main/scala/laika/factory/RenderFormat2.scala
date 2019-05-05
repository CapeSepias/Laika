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

package laika.factory

import laika.ast.{Element, Path, StyleDeclarationSet, TemplateRoot}
import laika.bundle.{RenderTheme2, StaticDocuments}
import laika.config.RenderConfig
import laika.render.Indentation

/**
  * @param renderChild a render function to use for rendering the children of an element
  * @param root the root element the new renderer will be used for
  * @param styles the styles the new renderer should apply to the rendered elements
  * @param path the (virtual) path the output will be rendered to              
  * @param config additional configuration for the renderer
  */
case class RenderContext2[FMT] (renderChild: (FMT, Element) => String,
                          root: Element, 
                          styles: StyleDeclarationSet,
                          path: Path,
                          config: RenderConfig) {
  
  val indentation: Indentation = if (config.renderFormatted) Indentation.default else Indentation.none
  
}

/** Responsible for creating renderer instances for a specific output format.
 *  A renderer is simply a function of type `(Formatter, Element) => String`. In addition
 *  to the actual renderer function, the factory method also produces
 *  an instance of the generic `FMT` type which is the formatter API to use
 *  for custom renderer functions and which is specific to the output format.
 *  
 *  @author Jens Halm
 */
trait RenderFormat2[FMT] {

  
  /** The file suffix to use when rendering the output
   *  to a file. When transforming entire directories
   *  the suffix of the markup file will be automatically
   *  replaced by the suffix for the output format.
   */
  def fileSuffix: String

  /** The default theme to use if no theme is explicitly specified.
    */
  def defaultTheme: Theme
  
  def defaultRenderer: (FMT, Element) => String
  
  def formatterFactory: RenderContext2[FMT] => FMT
  
  type CustomRenderFunction[FMT] = PartialFunction[(FMT, Element), String] // TODO - 0.12 - move

  /** Creates a new renderer and a new writer instance for the specified
   *  context. The delegate function of the context needs to be used
   *  whenever an element renders its children, as the user might have
   *  installed custom renderer overrides this instance is not aware of.
   *  If no custom renderer is responsible for the children, the invocation
   *  will fall back to calling this renderer again.
   * 
   *  In contrast to the parser function, a new render function will be created for
   *  each render operation. In addition
   *  to the actual renderer function, this method also produces
   *  an instance of the generic `W` type which is the writer API to use
   *  for custom renderer functions and which is specific to the output format.
   *  
   *  @param context the setup, environment, path and base renderers to use
   *  @return a new writer API of type `W` and a new render function
   */
  // def newFormatter (context: RenderContext2[FMT]): FMT


  case class Theme (customRenderer: CustomRenderFunction[FMT] = PartialFunction.empty,
                    defaultTemplate: Option[TemplateRoot] = None,
                    defaultStyles: StyleDeclarationSet = StyleDeclarationSet.empty,
                    staticDocuments: StaticDocuments = StaticDocuments.empty) extends RenderTheme2 {

    type Formatter = FMT

    def withBase (base: Theme): Theme = Theme(
      customRenderer.orElse(base.customRenderer),
      defaultTemplate.orElse(base.defaultTemplate),
      base.defaultStyles ++ defaultStyles,
      StaticDocuments(staticDocuments.merge(base.staticDocuments.tree))
    )

  }

}
