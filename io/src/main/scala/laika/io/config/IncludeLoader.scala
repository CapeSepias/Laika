/*
 * Copyright 2012-2019 the original author or authors.
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

package laika.io.config

import cats.effect.Async
import laika.config.ConfigError
import laika.io.runtime.Runtime
import laika.parse.hocon.{IncludeResource, ObjectBuilderValue}

/**
  * @author Jens Halm
  */
object IncludeLoader {
  
  type IncludeMap = Map[IncludeResource, Either[ConfigError, ObjectBuilderValue]]

  def load[F[_]: Async : Runtime] (includes: Seq[IncludeResource]): F[IncludeMap] =
    Async[F].pure(Map.empty) // TODO - 0.13 - implement
  
}
