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

package laika.parse.css

import laika.parse.css.Styles._

trait StyleBuilders {

  val defaultStyleMap = Map("foo"->"bar")
  
  def selector (predicates: Predicate*) = Selector(predicates.toSet)
  
  def selector (selector: Selector, parent: Selector, immediate: Boolean) =
    selector.copy(parent = Some(ParentSelector(parent, immediate)))
  
  def styleDecl (styles: Map[String,String], selector: Selector) = 
    StyleDeclaration(selector, styles)
    
  def styleDecl (selector: Selector) = 
    StyleDeclaration(selector, defaultStyleMap)

  def styleDecl (predicates: Predicate*) = StyleDeclaration(Selector(predicates.toSet), defaultStyleMap)

  def styleDecl (styles: Map[String,String], predicates: Predicate*) = StyleDeclaration(Selector(predicates.toSet), styles)
  
}