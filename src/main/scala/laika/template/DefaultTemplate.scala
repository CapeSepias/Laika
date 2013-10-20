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

package laika.template

import laika.io.Input
import laika.tree.Templates.TemplateDocument
import laika.directive.Directives.Templates

/** 
 *  @author Jens Halm
 */
class DefaultTemplate private (
    directives: List[Templates.Directive]) extends (Input => TemplateDocument) {

  
  private lazy val parser = new TemplateParsers.Templates {
    lazy val directiveMap  = directives  map { d => (d.name, d) } toMap
    def getTemplateDirective (name: String) = directiveMap.get(name)
  }
  
  def withDirectives (directives: Templates.Directive*) =
    new DefaultTemplate(this.directives ++ directives)      
  
  /** The actual parser function, fully parsing the specified input and
   *  returning a document tree.
   */
  def apply (input: Input) = TemplateDocument(input.path, parser.parseTemplate(input.asParserInput), null) // TODO - pass config
  
}

object DefaultTemplate extends DefaultTemplate(Nil)