/*
 * Copyright 2013 the original author or authors.
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

package laika.parse.rst

import laika.tree.Elements._

/** Provides the elements of the document tree that are too specific to reStructuredText
 *  to be added to the generic tree model. 
 *  
 *  @author Jens Halm
 */
object Elements {

  
  /** A two-column table-like structure used for bibliographic fields or directive options.
   */
  case class FieldList (content: Seq[Field]) extends Block with BlockContainer[FieldList]

  /** A single entry in a field list consisting of name and body.
   */
  case class Field (name: Seq[Span], content: Seq[Block]) extends Block with BlockContainer[Field]
  
  
  
}