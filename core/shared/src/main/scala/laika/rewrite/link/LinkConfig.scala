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

package laika.rewrite.link

import laika.ast.{Path, Target}
import laika.config._

/**
  * @author Jens Halm
  */
case class LinkConfig (targets: Seq[TargetDefinition], excludeFromValidation: Seq[Path])

object LinkConfig {
  
  val empty: LinkConfig = LinkConfig(Nil, Nil)
  
  implicit val key: DefaultKey[LinkConfig] = DefaultKey("links")
  
  implicit val decoder: ConfigDecoder[LinkConfig] = {
    case Traced(ov: ObjectValue, _) =>
      val config = ov.toConfig
      for {
        targets <- config.get[Map[String, String]]("targets", Map.empty[String,String])
        exclude <- config.get[Seq[Path]]("excludeFromValidation", Nil)
      } yield {
        val mappedTargets = targets.map {
          case (id, targetURL) => TargetDefinition(id, Target.create(targetURL))
        }
        LinkConfig(mappedTargets.toSeq, exclude)
      }

    case Traced(invalid: ConfigValue, _) => Left(InvalidType("Object", invalid))
  }
  
}

case class TargetDefinition (id: String, target: Target)
