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

package laika.sbt

import laika.api.ext.{ExtensionBundle, RenderTheme}
import laika.format.{HTML, XSLFO}
import laika.io.DocumentType
import laika.render.{FOWriter, HTMLWriter}
import laika.rewrite.DocumentCursor
import laika.tree.Elements.{RenderFunction, RewriteRule}
import laika.tree.Paths

/** API shortcuts for the most common extension points that create
  * an extension bundle from a single feature, so that it can be passed
  * to the `laikaExtensions` setting.
  *
  * Example:
  *
  * {{{
  * laikaExtensions += laikaRewriteRule {
  *   case Emphasized(content, _) => Some(Strong(content))
  * }
  * }}}
  *
  * @author Jens Halm
  */
trait ExtensionBundles {

  @deprecated("renamed to laikaHtmlRenderer", "0.9.0")
  def laikaSiteRenderer (f: HTMLWriter => RenderFunction): ExtensionBundle = laikaHtmlRenderer(f)

  /** Create an extension bundle based on the specified custom HTML render function.
    */
  def laikaHtmlRenderer (f: HTMLWriter => RenderFunction): ExtensionBundle = new ExtensionBundle {
    override def themes: Seq[RenderTheme] = Seq(HTML.Theme(customRenderer = f))
  }

  /** Create an extension bundle based on the specified custom XSL-FO render function.
    *
    * Such a render function will also be used for PDF rendering, as XSL-FO is an interim
    * format for the PDF renderer.
    */
  def laikaFoRenderer (f: FOWriter => RenderFunction): ExtensionBundle = new ExtensionBundle {
    override def themes: Seq[RenderTheme] = Seq(XSLFO.Theme(customRenderer = f))
  }

  /** Create an extension bundle based on the specified rewrite rule.
    *
    * Rewrite rules allow the modification of the document AST between parse and render operations.
    */
  def laikaRewriteRule (rule: RewriteRule): ExtensionBundle = laikaRewriteRuleFactory(_ => rule)

  /** Create an extension bundle based on the specified rewrite rule.
    *
    * Rewrite rules allow the modification of the document AST between parse and render operations.
    * The supplied function will get invoked for every document in the transformation, therefore
    * creating a new rule for each document.
    */
  def laikaRewriteRuleFactory (factory: DocumentCursor => RewriteRule): ExtensionBundle = new ExtensionBundle {
    override def rewriteRules: Seq[DocumentCursor => RewriteRule] = Seq(factory)
  }

  /** Create an extension bundle based on the specified document type matcher.
    *
    * The matcher function determines the document type of the input based on its path.
    */
  def laikaDocTypeMatcher (f: PartialFunction[Paths.Path, DocumentType]): ExtensionBundle = new ExtensionBundle {
    override def docTypeMatcher: PartialFunction[Paths.Path, DocumentType] = f
  }

}
