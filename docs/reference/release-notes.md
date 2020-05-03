
Release Notes
=============


0.14.0 (Feb 28, 2020)
---------------------

* Introduce support for Scala.js for the entire laika-core module:
    * Brings the complete functionality of Laika to Scala.js with the only
      exceptions being File/Stream IO and support for PDF and EPUB output
    * Eliminate the last, semi-hidden usages of Java reflection to enable Scala.js support
    * Avoid use of `java.time.Instant` in the shared base to not force another heavy dependency
      on Scala.js users
* Laika's integrated syntax highlighting:
    * Add support for Dotty, JSX/TSX, SQL, EBNF, Laika's own AST format
* Parser APIs:
    * Introduce `PrefixedParser` trait to make span parser optimizations implicit
      for 90% of the use cases and no longer require the developer to explicitly 
      supply a set of possible start characters for the span
    * Introduce new parser for delimiters that generalizes typical checks for preceding
      and following characters in markup parsers (e.g. `delimiter("**").prevNot(whitespace)`),
      to reduce boilerplate for inline parsers
    * Introduce shortcuts for very common usages of text parsers (e.g. `oneOf('a','b')` instead of
      `anyOf('a','b').take(1)`)
    * Expand the API of the `Parser` base trait: add `source` method for obtaining the consumed
      part of the input instead of the result of the parser, and `count` to obtain the number of
      consumed characters
    * Introduce `laika.parse.implicits._` for extension methods for common parsers, e.g.
      `.concat`, `mapN`.
    * Introduce shortcut for repeating a parser with a separator
    * Deprecate all APIs that relied on implicit conversions
    * Deprecate some of the rather cryptic symbol methods on `Parser` in favor of named methods
    * `DelimitedText` does no longer have a type parameter
* AST changes and improvements:
    * Introduce RelativePath as a separate type in addition to the existing Path type
      which is now only used for absolute paths
    * Introduce a range of shortcut constructors for Laika's AST nodes, that allow you
      to write `Paragraph("some text")` instead of `Paragraph(Seq(Text("some text")))`
      for example
* Directives:
    * Cleaner syntax for default attributes which are no longer part of the HOCON
      attribute block for named attributes
* Demo App:
    * Cleanup from six to two (bigger) panels for improved usability
    * Use Laika's own syntax highlighting in the output
    * Allow to switch from JVM execution to Scala.js
    * Move from Akka HTTP to http4s
    * Integrate source into Laika's main repository
* Bugfix:
    * Syntax highlighting for interpolated strings in Scala was broken in 0.13.0
    * Fix a flaky test that relied on the ordering of HashMap entries
    

0.13.0 (Jan 26, 2020)
---------------------

* Introduce integrated syntax highlighting based on the libraries own parsers
    * Resulting AST nodes for code spans are part of the document AST and
      can be processed or transformed like all other nodes
    * Works with any renderer, including PDF
    * Initially supported are Scala, Java, Python, JavaScript, TypeScript,
      HTML, CSS, XML, JSON, HOCON
    * Convenient base parsers for common syntax like string and number literals
      or identifiers to facilitate development of new syntax highlighters
* HOCON parser: add support for `include` statements, this final feature addition 
  makes Laika's HOCON support fully spec-compliant      
* New transformation hooks in the `laika-io` module for parallel transformers: 
  `mapDocuments(Document => Document)`, `evalMapDocuments(Document => F[Document])` 
  and the corresponsing `mapTree` and `evalMapTree`
* Transformer introspection: introduce `describe` method for parsers, renderers
  and transformers and `laikaDescribe` setting in the sbt plugin that provides
  formatted information about the transformer setup and installed extensions
* sbt plugin: improved accuracy for caching logic for EPUB and PDF output
  that still works when the artifact name or version changes
* Upgrade dependency on cats-core to 2.1.0


0.12.1 (Dec 1, 2019)
--------------------

* Fixes and Improvements for the new HOCON parser introduced in version 0.12.0
    * Significant improvements for error messages in HOCON parser
    * Fixes for nested self references, missing optional self references,
      and objects without '=' or ':' separator
* Parser Combinators: The '|' alternative parser now keeps the failure with the
  most processed characters and not the last one, for improved error messages
* Fix for script tag with attributes not being recognized in verbatim HTML in Markdown


0.12.0 (Oct 30, 2019)
---------------------

* New laika-io Module
    * Functionality extracted from existing laika-core module
    * Contains File/Stream IO, EPUB output, Parallel transformations
    * Based on cats-effect type classes (in "Bring-Your-Own-Effect" style) 
    * Leaves laika-core pure, preparing it for later support for Scala.js
* Referential Transparency
    * No method in the public API throws Exceptions anymore
    * The result of pure operations is provided by instances of `Either`
    * The result of side-effecting operations is provided by a return type of `F[A]`,
      with `F[_]` being an effect type from cats-effect
    * Eliminated all uses of runtime reflection
* Changes to the APIs for creating and running parsers, renderers and transformers
    * Necessary due to the changes listed above
    * See the migration guide for details and examples  
* Changes to the Directive Syntax in Templating
    * The syntax of separators for the attribute and body sections have changed
    * HOCON syntax is now used for attributes
    * The old syntax is still supported, but will be removed at some point before the 1.0 release
* Changes to the Directive DSL for creating directives
    * `attribute(Default)` is now `defaultAttribute`
    * `body` is now either `parsedBody` or `rawBody`
    * Type conversions happen with the new `as` method: `attribute("title").as[String]`,
      based on the `ConfigDecoder` type class that is also used for the new Config API
    * Named body parts have been replaced by the more flexible Separator Directives
    * The built-in helper for mapping directive parts with different arity has
      been replaced by cats' `mapN`
* Refactoring of AST Rewrite API to be fully type-safe and avoid runtime reflection and exceptions.
    * Return types are now more explicit (e.g. `Replace(newSpan)` instead of `Some(newSpan)`)
    * Rules for rewriting spans and blocks get registered separately for increased
      type-safety, as it is invalid to replace a span with a block element.
* Refactoring of the Render API to be referentially transparent
    * Also applies to the API for registering custom renderers for individual AST nodes
* New Config API and built-in HOCON parser
    * Removed the dependency on the Typesafe Config library and its impure Java API
    * Added a new lightweight and pure HOCON parser as part of laika-core,
      supporting the full spec except for file includes (for now).
* Enhancement for the DocumentTree API      
    * The result of a tree parsing operation is now a new type called `DocumentTreeRoot`
    * It has a `coverDocument` property and contains the recursive tree structure of the parsed content.
    * Each `DocumentTree` in the structure now has an explicit `titleDocument: Option[Document]` property
      for more explicit content organization in e-books.
    * Properties that previously held references to streams and other impure data had been
      removed from the pure content model (e.g. `DocumentTree.staticDocuments`). 
* Bug fixes for fenced code blocks with blank lines in GitHub-Flavored Markdown      


0.11.0 (June 12, 2019)
----------------------

* New Renderer for producing EPUB containers
* New `laikaEPUB` task in the sbt plugin
* New `laikaIncludeEPUB` setting for the `laikaSite` task
* Support for cover images for EPUB and PDF
* Support for document metadata (author, language, date, etc.) for EPUB and PDF
* Support for title pages per chapter
* Backwards-compatible to 0.9.0 and 0.10.0 - if you update from earlier version, please see
  the release notes for 0.9.0 for migration


0.10.0 (Dec 1, 2018)
--------------------

* Add support for GitHub Flavored Markdown:
    * Tables
    * Fenced Code Blocks
    * Auto-Links
    * Strikethrough
* Preparing for Scala 2.13    
    * Adjust use of Collection API for breaking changes and deprecations in 2.13
    * Replace use of parallel collections with custom executor
* Level of parallelism of transformations is now configurable


0.9.0 (Sep 15, 2018)
--------------------

* New ExtensionBundle APIs allow to bundle extensions into a single object for easier reuse. Supported extension
  hooks include directives, markup parser extensions, rewrite rules, custom renderers, document type matchers, 
  alternative parsers for stylesheets, templates or configuration headers and default templates per output format. 
* Reduced number of settings and tasks in the sbt plugin, by using the new ExtensionBundle API for sbt settings.
* Improved package structure to reduce number of required imports. 


0.8.0 (June 4, 2018)
--------------------

* Doubles parsing speed for both Markdown and reStructuredText
* Much lower number of parser instance creations on repeated runs
* Performance goals had been achieved through replacing the former
  Scala SDK parser combinators with a custom, optimized combinator design:
    * Fewer dependent types and base parsers in objects instead of traits, making it easier to freely compose parsers
    * Create parser error messages lazily, as most of them will never be accessed
    * Avoid cost of by-name args in all cases except | combinator
* Add support for size and align options for the image directive in reStructuredText
* Fixes for all bugs known and reported to this point 
* Address all deprecation warnings for Scala 2.12    


0.7.5 (Dec 30, 2017)
--------------------

* Support for sbt 1.0
* Laika's sbt plugin is now an AutoPlugin
* Prefixed all task and setting keys to adhere to recommended naming pattern
  (e.g. laikaGenerate) to avoid name conflicts for autoImports
* Adjustments for API changes in sbt 1.0
* Bug fixes in the library
* Drop support for sbt 0.13 and Scala 2.10


0.7.0 (April 17, 2017)
----------------------

* Support for Scala 2.12 (with continued support for 2.11 and 2.10)
* Redesign and cleanup of the Document API: use case classes wherever possible,
  extract features into pluggable traits and introduce a new `Cursor` type for tree rewriting
* Allow to customize the `FopFactory` for the PDF renderer (in API and sbt plugin)
* Fix an issue in the `laika:site` task in the sbt plugin that executed several sub-tasks
  twice which could lead to IllegalStateExceptions caused by the resulting race condition
* Fixes for the reStructuredText parser (for option lists and IP addresses)


0.6.0 (May 23, 2016)
--------------------

* Support for rendering PDF documents
* Support for rendering XSL-FO output
* New CSS parser supporting a large subset of standard CSS
* Support styling of PDF documents with CSS
* Support for different templates per output format
* New sbt tasks: `html`, `pdf`, `xslfo`, `prettyPrint` for rendering
  a single output format
* New sbt task `generate` for rendering one or more output formats
  (e.g. `laika:generate html pdf`)
* Integrate PDF output into existing sbt task `laika:site` via
  new setting `includePDF`
* New directives `pageBreak`, `style` and `format`
* Changes to the `Render` and `Transform` API to allow for the
  merging of an entire directory of input files into a single output
  file (as required by PDF rendering)


0.5.1 (Oct 10, 2015)
--------------------

* Cross-compile for Scala 2.11 and 2.10
* Publish the sbt plugin to the new plugin repository on Bintray
* Upgrade to ScalaTest 2.2.4


0.5.0 (Jan 9, 2014)
-------------------

* New sbt plugin, exposing all Laika features and customization hooks as sbt tasks and settings
* New option to merge multiple input directories into a tree structure with a single root,
  allowing to keep reusable styles or templates ("themes") separately
* New option to use Markdown and reStructuredText markup in the same input tree, including
  cross-linking between the two formats
* Move to a multi-project build and rename the main artifact from `laika` to `laika-core`
* Upgrade to ScalaTest 2.0 and sbt 0.13
* Drop support for Scala 2.9.x


0.4.0 (Nov 22, 2013)
--------------------

* Template-based site generation
* Support for tables of contents, convenient cross-linking between documents 
  and autonumbering of documents and sections for all supported markup formats
* Custom Directives for templates and text markup
* Document Fragments that can be rendered separately from the main document content
* New API for batch processing for parse, render or full transform operations
* Parallel processing of parsers and renderers 

  
0.3.0 (Aug 3, 2013)
-------------------

* Support for most of the standard directives and text roles of the reStructuredText reference
  parser (admonitions, `figure`, `image`, `code`, `raw` and many more)
* Now integrates the official Markdown test suite (and many fixes to make it pass)
* Now integrates a test for transforming the full reStructuredText specification (which, of
  course, is written in reStructuredText) and many fixes to make it pass
* Adds the renderer option `HTML.unformatted` for terse output without indentation or whitespace
  (often desirable when writing the rendered document to a database for example)
* Adds a new [Web Tool] to try out Laika online
* General cleanup of parser implementations and alignments between Markdown and reStructuredText
  parsers, making both of them much more robust


0.2.0 (May 7, 2013)
-------------------

* Support for reStructuredText (full specification)
* Concise and type-safe API for all reStructuredText extensibility options (directives, text roles)
* New document tree nodes for tables, footnotes, citations, definition lists, internal links,
  comments, system messages, invalid elements
* Render hints for document tree nodes in the form of the new Customizable trait


0.1.0 (Jan 12, 2013) 
--------------------

* Support for Markdown as input
* Support for HTML and AST as output
* Customization hooks for renderers
* Document tree rewriting
* Various options for input and output (strings, files, java.io.Reader/Writer, java.io streams)
* Generic base traits for markup parser implementations
