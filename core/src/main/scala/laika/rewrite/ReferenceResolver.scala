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

package laika.rewrite

import laika.config.Config.ConfigResult
import laika.config.{ASTValue, Config, ConfigBuilder, ConfigValue, Field, ObjectValue}
import laika.ast.{Document, Path, SpanSequence, TreeCursor}

/** A resolver for context references in templates or markup documents.
 *  
 *  @author Jens Halm
 */
case class ReferenceResolver (config: Config) {
  
  def resolve (key: Path): ConfigResult[Option[ConfigValue]] = config.getOpt[ConfigValue](key)
  
}

/** Companion for constructing ReferenceResolvers for a particular
 *  target Document.
 */
object ReferenceResolver {
  
  /** Creates a new ReferenceResolver for the specified
   *  document and its parent and configuration.
   */
  def forDocument(document: Document, parent: TreeCursor, config: Config): ReferenceResolver =
    apply(ConfigBuilder
      .withFallback(config)
      .withValue("document", ObjectValue(Seq(
        Field("content", ASTValue(document.content), config.origin),
        Field("title", ASTValue(SpanSequence(document.title)), config.origin),
        Field("fragments", ObjectValue(document.fragments.toSeq.map { 
          case (name, element) => Field(name, ASTValue(element), config.origin) 
        }), config.origin)
      ))) // TODO - 0.12 - insert documented refs to config, document, parent, root
      .build
    )
  
}
