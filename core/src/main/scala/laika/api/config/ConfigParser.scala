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

package laika.api.config

import laika.ast.Path
import laika.parse.hocon.HoconParsers.Origin
import laika.parse.hocon.{ConfigResolver, HoconParsers}

/**
  * @author Jens Halm
  */
class ConfigParser(input: String, origin: Origin, fallback: Config = EmptyConfig) {
  
  def resolve: Either[ConfigError, Config] = HoconParsers.rootObject
    .parse(input)
    .toEither
    .left.map(ConfigParserError)
    .map { builderValue =>
      val root = ConfigResolver.resolve(builderValue, fallback) // TODO - 0.12 - this must return an Either, too
      new ObjectConfig(root, origin, fallback)
    }

  def withFallback(other: Config): ConfigParser = new ConfigParser(input, origin, fallback.withFallback(other))
  
}

object ConfigParser {

  def parse(input: String): ConfigParser = new ConfigParser(input, Origin(Path.Root))

  def parse(input: String, origin: Path): ConfigParser = new ConfigParser(input, Origin(origin))
  
}
