/*
 * Copyright 2014 the original author or authors.
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

import laika.io.OutputProvider.OutputConfigBuilder
import laika.tree.Documents.DocumentTree
import laika.io.Output
import laika.io.OutputProvider.OutputConfig

/** Post processor for the result output of a renderer.
 *  Useful for scenarios where interim formats will be generated
 *  (e.g. XSL-FO for a PDF target or HTML for an epub target).
 *  
 *  @author Jens Halm
 */
trait RenderResultProcessor[Writer] {


  /** The factory for the renderer that produces the interim result.
   */
  def factory: RendererFactory[Writer]

  /** Processes the tree by first using the specified render function
   *  to produce the interim result, process the result and write
   *  it to the specified final output.
   * 
   *  @param tree the tree to render to the interim result
   *  @param render the render function for producing the interim result
   *  @param output the output to write the final result to
   */
  def process (tree: DocumentTree, render: OutputConfig => Unit, output: Output): Unit
  
  
}