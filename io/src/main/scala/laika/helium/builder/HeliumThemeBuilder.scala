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

package laika.helium.builder

import cats.effect.{Resource, Sync}
import laika.format.{HTML, XSLFO}
import laika.io.runtime.Runtime
import laika.helium.Helium
import laika.helium.generate._
import laika.theme.{Theme, ThemeBuilder, ThemeProvider}

/**
  * @author Jens Halm
  */
private[helium] class HeliumThemeBuilder (helium: Helium) extends ThemeProvider {

  def build[F[_]: Sync: Runtime]: Resource[F, Theme[F]] = {

    import helium._

    val treeProcessor = new HeliumTreeProcessor[F](helium)

    ThemeBuilder("Helium")
      .addInputs(HeliumInputBuilder.build(helium))
      .addBaseConfig(ConfigGenerator.populateConfig(helium))
      .addRewriteRules(HeliumRewriteRules.build(helium))
      .addRenderOverrides(HTML.Overrides(HeliumRenderOverrides.forHTML(siteSettings.layout.anchorPlacement)))
      .addRenderOverrides(XSLFO.Overrides(HeliumRenderOverrides.forPDF))
      .processTree(treeProcessor.forHTML, HTML)
      .processTree(treeProcessor.forAllFormats)
      .build

  }

}
