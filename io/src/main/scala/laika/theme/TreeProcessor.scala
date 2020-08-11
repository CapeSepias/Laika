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

package laika.theme

import cats.data.Kleisli
import cats.effect.Sync
import laika.io.model.ParsedTree
import laika.io.ops.TreeMapperOps

/**
  * @author Jens Halm
  */
abstract class TreeProcessor[F[_]: Sync] extends TreeMapperOps[F] {

  type MapRes = Kleisli[F, ParsedTree[F], ParsedTree[F]]

  def evalMapTree (f: ParsedTree[F] => F[ParsedTree[F]]): Kleisli[F, ParsedTree[F], ParsedTree[F]] = Kleisli(f)
}

object TreeProcessor {
  
  def apply[F[_]: Sync]: TreeProcessor[F] = new TreeProcessor[F] { }
  
}
