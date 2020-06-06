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

package laika.io.helper

import cats.data.Kleisli
import cats.effect.IO
import laika.ast.Document
import laika.bundle.ExtensionBundle
import laika.io.model.{ParsedTree, TreeInput}
import laika.io.theme.{Theme, TreeTransformer}


object ThemeBuilder {

  def forInputs (themeInputs: IO[TreeInput[IO]]): Theme[IO] = new Theme[IO] {
    def inputs = themeInputs
    def extensions = Nil
    def treeTransformer = Kleisli(IO.pure)
  }

  def forBundle (bundle: ExtensionBundle): Theme[IO] = forBundles(Seq(bundle))

  def forBundles (bundles: Seq[ExtensionBundle]): Theme[IO] = new Theme[IO] {
    def inputs = IO.pure(TreeInput.empty)
    def extensions = bundles
    def treeTransformer = Kleisli(IO.pure)
  }
  
  def forDocumentMapper (f: Document => Document): Theme[IO] = new Theme[IO] {
    def inputs = IO.pure(TreeInput.empty)
    def extensions = Nil
    def treeTransformer = TreeTransformer[IO].mapDocuments(f)
  }
  
}
