/*
 * Copyright 2013-2016 the original author or authors.
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

package laika.api

import laika.api.builder.TransformerBuilder
import laika.config.OperationConfig
import laika.factory.{MarkupFormat, RenderFormat}

@deprecated("use Transformer instead", "0.12.0")
object Transform {

  class BuilderFactory private[Transform] (parser: MarkupFormat, config: OperationConfig) {
    def to [FMT] (format: RenderFormat[FMT]): TransformerBuilder[FMT] = new TransformerBuilder(parser, format, config)
  }

  @deprecated("use Transformer.of(...) instead", "0.12.0")
  def from (format: MarkupFormat): BuilderFactory = new BuilderFactory(format, OperationConfig.default.withBundlesFor(format))
  
}
