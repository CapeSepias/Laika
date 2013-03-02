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
import laika.util.Builders._

/** API for creating directives, the extension mechanism of reStructuredText.
 *  The API did not aim to mimic the API of the original Python reference implementation.
 *  Instead the goal was to create an API that is idiomatic Scala, fully typesafe and as concise as possible.
 *  Yet it should be flexible enough to semantically support the options of the Python directives, so that
 *  ideally most existing Python directives could theoretically get ported to Laika.
 * 
 *  Entry points are the `BlockDirective` and `SpanDirective` objects. The Python reference parser does
 *  not make this distinction on the API level, but does this internally based on the context a 
 *  directive is parsed in. Since Laika APIs are typesafe, the distinction is necessary since
 *  block level and span level directives create different types of document tree nodes.
 *  A `SpanDirective` can only be used in a substitution definition which can then be used
 *  within flow elements. A `BlockDirective` can be used directly in any location other block
 *  level content like paragraphs or lists can be used.
 * 
 *  TODO - examples for directive setup
 * 
 *  @author Jens Halm
 */
object Directives {

  
  class Result[+A] (a: => A) {
    
    def get = a
    
    def map [B](f: A => B) = new Result(f(get))
    
  }
  
  trait DirectiveParser {
      
    def requiredArg [T](f: String => T): Result[T]
    
    def optionalArg [T](f: String => T): Result[Option[T]]
    
    def requiredField [T](name: String, f: String => T): Result[T]
    
    def optionalField [T](name: String, f: String => T): Result[Option[T]]
    
    def standardContent: Result[Seq[Block]]
    
    def content [T](f: String => T): Result[T]
    
  }
  
  abstract class DirectivePart[+A] extends (DirectiveParser => Result[A]) { self =>
    
    def map [B](f: A => B) = new DirectivePart[B] { 
      def apply (p: DirectiveParser) = self(p) map f 
    }
    
  }
  
  implicit object CanBuildDirectivePart extends CanBuild[DirectivePart] {
    
    def apply [A,B](ma: DirectivePart[A], mb: DirectivePart[B]) = new DirectivePart[A~B] {
      def apply (p: DirectiveParser) = {
        val a = ma(p)
        val b = mb(p)
        new Result(new ~(a.get,b.get))
      }
    }
  
    def map [A,B](m: DirectivePart[A], f: A => B) = m map f
    
  }
 
  object Parts {
    
    private def part [T](f: DirectiveParser => Result[T]) = new DirectivePart[T] {
      def apply (p: DirectiveParser) = f(p)
    }
    
    def requiredArg: DirectivePart[String] = part(_.requiredArg(s => s))
    
    def requiredArg [T](f: String => T): DirectivePart[T] = part(_.requiredArg(f))
  
    def optionalArg: DirectivePart[Option[String]] = part(_.optionalArg(s => s))
    
    def optionalArg [T](f: String => T): DirectivePart[Option[T]] = part(_.optionalArg(f))
    
    def requiredField [T](name: String): DirectivePart[String] = part(_.requiredField(name, s => s))
    
    def requiredField [T](name: String, f: String => T): DirectivePart[T] = part(_.requiredField(name, f))
    
    def optionalField (name: String): DirectivePart[Option[String]] = part(_.optionalField(name, s => s))
    
    def optionalField [T](name: String, f: String => T): DirectivePart[Option[T]] = part(_.optionalField(name, f))
    
    def standardContent: DirectivePart[Seq[Block]] = part(_.standardContent) // TODO - maybe use blockContent/spanContent
    
    def content [T](f: String => T): DirectivePart[T] = part(_.content(f))
    
  }

  class Directive [E <: Element] (val name: String)(val part: DirectivePart[Seq[E]])

  object SpanDirective {
    
    def apply (name: String)(part: DirectivePart[Seq[Span]]) = new Directive(name)(part)
    
  }
  
  object BlockDirective {
    
    def apply (name: String)(part: DirectivePart[Seq[Block]]) = new Directive(name)(part)
    
  }

  
}







