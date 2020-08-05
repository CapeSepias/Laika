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

package laika.rewrite.nav

import laika.ast.Path
import laika.config.{ConfigDecoder, ConfigEncoder}

/** Configuration for a cover image for e-books (EPUB or PDF).
  * 
  * The optional classifier can be used if the `@:choices` directive
  * is used to produce multiple e-books with slightly different content.
  * The classifier would refer to the name of the configured choice,
  * or in case of multiple choices, to the combination of their names concatenated with `-`.
  * 
  * @author Jens Halm
  */
case class CoverImage (path: Path, classifier: Option[String])

object CoverImage {

  def apply (path: Path, classifier: String): CoverImage = CoverImage(path, Some(classifier))
  
  implicit val decoder: ConfigDecoder[CoverImage] = ConfigDecoder.config.flatMap { config =>
    for {
      path       <- config.get[Path]("path")
      classifier <- config.getOpt[String]("classifier")
    } yield {
      CoverImage(path, classifier)
    }
  }
  
  implicit val encoder: ConfigEncoder[CoverImage] = ConfigEncoder[CoverImage] { coverImage =>
    ConfigEncoder.ObjectBuilder.empty
      .withValue("path", coverImage.path)
      .withValue("classifier", coverImage.classifier)
      .build
  }
  
}
