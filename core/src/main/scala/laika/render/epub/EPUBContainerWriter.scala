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

package laika.render.epub

import laika.ast.DocumentTree
import laika.format.EPUB
import laika.io.Output.BinaryOutput
import laika.io.OutputTree.ResultTree

/**
  * @author Jens Halm
  */
class EPUBContainerWriter (config: EPUB.Config) {


  def write (tree: DocumentTree, html: ResultTree, output: BinaryOutput): Unit = ???


}
