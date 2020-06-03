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

package laika.io.theme

import cats.data.Kleisli
import cats.effect.Sync
import laika.bundle.ExtensionBundle
import laika.io.model.{ParsedTree, TreeInput}

/**
  * @author Jens Halm
  */
trait Theme[F[_]] {

  def inputs: F[TreeInput[F]]
  
  def extensions: Seq[ExtensionBundle]
  
  def treeTransformer: Kleisli[F, ParsedTree[F], ParsedTree[F]]
  
}

object Theme {

  def default[F[_]: Sync]: Theme[F] = new Theme[F] {

    // TODO - 0.16 - populate with defaults
    
    def inputs: F[TreeInput[F]] = Sync[F].pure(TreeInput.empty)

    def extensions: Seq[ExtensionBundle] = Nil

    def treeTransformer: Kleisli[F, ParsedTree[F], ParsedTree[F]] = Kleisli(Sync[F].pure)
  }

}
