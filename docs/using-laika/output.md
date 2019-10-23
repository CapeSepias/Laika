
Supported Output Formats
========================

The current release supports HTML, EPUB, PDF, XSL-FO and AST.
Rendering happens from a generic document tree model shared between all parsers,
so that no renderer implementation has to understand specifics about a concrete
markup syntax like Markdown or reStructuredText.

Customization of the output is possible on two levels, first most formats (except
for AST) can be styled with CSS. Secondly the rendering of specific nodes
can be overridden with a simple partial function as described in [Customizing Renderers].

Finally you can develop an entirely new renderer for a format not supported by Laika
out of the box. See chapter [Implementing a Renderer] for details.


HTML
----

The HTML renderer can be used with the `Transform` or `Render` APIs:

    val html: String = Transformer
      .from(Markdown)
      .to(HTML).
      .build
      .transform("hello *there*")

    val doc: Document = Parser
      .of(Markdown)
      .build
      .parse("hello *there*")
      
    val html: String = Renderer
      .of(HTML)
      .build
      .render(doc)
    
See [Using the Library API] for more details on these APIs.
    
If you are using the sbt plugin you can use several of its task for generating
HTML output:

* `laikaHTML` for transforming a directory of input files to HTML
* `laikaGenerate html <other formats>` for transforming a directory of input files to HTML
  and other output formats with a single parse operation
* `laikaSite` for generating a site optionally containing API documentation (scaladoc) and
  PDF files.
  
See [Using the sbt Plugin] for more details.


### Templating

Laika supports Templating for most output formats. The following example
uses variable references to include the title and content of the input
document, as well as a directive called `@toc` to inset a table of contents:

    <html>
      <head>
        <title>${document.title}</title>
      </head>
      <body>
        @:toc
        <div class="content">
          ${document.content}
        </div>
      </body>
    </html>
    
If you save such a template in a file called `default.template.html` in the
root directory of your input sources it will get applied to all markup documents
in those directories. You can optionally override the template for an individual
sub-directory simply by including a different template named `default.template.html`
in that sub-directory.

See [Templates] for more details.


### HTML Renderer Properties

The `unformatted` property tells the renderer to omit any formatting (line breaks or indentation) 
around tags. Useful when storing the output in a database for example:

    val transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .unformatted 
      .build

The `withMessageLevel` property instructs the renderer to include system messages in the
generated HTML. Messages may get inserted into the document tree for problems during
parsing or reference resolution, e.g. an internal link to a destination that does not
exist. By default these messages are not included in the output. They are mostly useful
for testing and debugging, or for providing feedback to application users producing 
markup input:

    val transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .withMessageLevel(Warning)
      .build


### CSS, JavaScript and other Files

The generated HTML can be styled with CSS like any other static HTML files.
If you transform an entire directory Laika will copy all static files like CSS
and JavaScript files over to the target directory alongside the generated HTML.
It does that recursively including sub-directories.


### Customizing the HTML Renderer

Finally you can adjust the rendered output for one or more node types
of the document tree programmatically with a simple partial function:

    val transformer = Transformer
      .from(Markdown)
      .to(HTML)
      .rendering {
        case (fmt, Emphasized(content, opt)) => 
          fmt.element("em", opt, content, "class" -> "big")  
      }
      .build
    
Note that in some cases the simpler way to achieve the same result may be
styling with CSS.

See [Customizing Renderers] for more details.


EPUB
----

Since version 0.11.0 Laika support the generation of e-books in the EPUB format.
Similar to the PDF export, it allows you to transform an entire directory with
text markup, CSS, image and font files into a single EPUB container.

If you are using the sbt plugin you can use several of its task for generating
EPUB files:

* `laikaEPUB` for transforming a directory of input files to a single PDF file
* `laikaGenerate epub <other formats>` for transforming a directory of input files to PDF
  and other output formats with a single parse operation
* `laikaSite` for generating a site optionally containing API documentation (scaladoc) and
  PDF and/or EPUB files.
  
See [Using the sbt Plugin] for more details.

If you want to produce EPUB files with the library API,
the `laika-io` module is required for the binary output:

    libraryDependencies += "org.planet42" %% "laika-io" % "0.12.0"

The EPUB renderer can be used with the `Transform` or `Render` APIs:

    implicit val cs: ContextShift[IO] = 
      IO.contextShift(ExecutionContext.global)
      
    val blocker = Blocker.liftExecutionContext(
      ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
    )
    
    val transformer = Transformer
      .from(Markdown)
      .to(EPUB)
      .using(GitHubFlavor)
      .io(blocker)
      .parallel[IO]
      .build
    
    transformer
      .fromDirectory("src")
      .toFile("hello.epub")
      .transform


See [Using the Library API] for more details on these APIs.



### EPUB Directory Structure

The structure of the generated EPUB file will exactly mirror the structure of the
input directory, apart from the additional metadata files it needs to generate.
This means that the file size of the resulting HTML documents inside the EPUB container
will roughly correspond to the size of the text markup documents used as input.
For that reason it is recommended to split the input into multiple files to avoid
large files which might slow down the experience in the e-book reader.

Laika supports a directory structure with sub-directories of any depth. Since EPUB
requires a linear spine to be defined for its navigation order, the transformer
will produce a spine that corresponds to a depth-first traversal of you input directories.


### CSS for EPUB

Since content files for EPUB are standard XHMTL files (apart from optional EPUB-specific attributes), you
can style your e-books with standard CSS. It is sufficient to simply place all CSS into the input directory,
alongside the text markup and other file types. References to these CSS files will be automatically added
to the header section of all generated HTML files. When referencing images or fonts from your CSS files,
you can use relative paths, as the directory layout will be retained inside the EPUB container.


### Images, Fonts and other File Types

You can also place images, fonts and other supported file types into the input directory.
Laika will add these files to the generated EPUB container and metadata files.

The supported file types / suffixes are:

* Images: `jpg`, `jpeg`, `gif`, `png`, `svg`
* Audio: `mp3`, `mp4`
* HTML: `html`, `xhtml`
* JavaScript: `js`
* CSS: `css`   
* Fonts: `woff2`, `woff`, `ttf`, `otf` 


### EPUB XHTML Templates

Like the HTML renderer, the EPUB renderer supports templating. EPUB requires XHTML as the output
format and also may contain custom attributes specific to EPUB. Therefore they are handled separately
from regular HTML templates and are recognised by the suffix `.epub.xhtml`. 

You can have a look at the [default EPUB XHTML template][default-epub-template] used 
by the EPUB renderer for reference.

[default-epub-template]: https://github.com/planet42/Laika/blob/master/core/src/main/resources/templates/default.template.epub.xhtml

You can override it if required by saving a custom template in a file called 
`default.template.epub.xhtml` in the root directory of your input sources.

       

### Configuration

There are several configuration options for EPUB generation that can be set
in the file `directory.conf` in the root directory of your input sources:

    epub {
      toc.depth = 3
      toc.title = "Contents"
      coverImage = "cover.png"
    }  
    metadata {
      identifier = "urn:isbn:978-3-16-148410-0"
      date = "2018-01-01T12:00:00Z"
      language = "en:GB"
      author = "Mia Miller"
    }

These properties control the following aspects of the rendering:
 
* `toc.depth` the number of levels to generate a table of contents for. 
  Every level of the tree hierarchy will be considered for an entry in the table 
  of contents: directories, files and sections within files.
  The default value is `Int.MaxValue`.
* `toc.title` specifies the title for the table of contents. The default value is `Contents`.
* `coverImage` specifies the cover image for the book
* `metadata` specifies document metadata to be added to the container configuration. Three of the 
  properties are mandatory, but Laika will use sensible defaults if they are not set explicitly
  (a random UUID for the identifier, the current time and the language of the Locale of the JVM process)
  
For more details on these features see [Document Structure].


PDF
---

The PDF support in Laika does not require installation of external tools as it
is not based on LaTeX like many other PDF renderers. To follow Laika's general
principle to allow for embedded use without further installations, it is based
on XSL-FO as an interim format and uses [Apache FOP] for transforming the XSL-FO
generated by Laika to the binary PDF file. Therefore several characteristics
like performance and memory consumption depend entirely on Apache FOP. If you
plan to use Laika's PDF support embedded in live application it is recommended
to first do performance and load testing.

[Apache FOP]: https://xmlgraphics.apache.org/fop/

If you are using the sbt plugin you can use several of its task for generating
PDF files:

* `laikaPDF` for transforming a directory of input files to a single PDF file
* `laikaGenerate pdf <other formats>` for transforming a directory of input files to PDF
  and other output formats with a single parse operation
* `laikaSite` for generating a site optionally containing API documentation (scaladoc) and
  PDF files.
  
See [Using the sbt Plugin] for more details.

If you want to produce PDF files with the library API,
you need to add the `laika-pdf` module to your build:

    libraryDependencies += "org.planet42" %% "laika-pdf" % "0.12.0"

The PDF renderer can be used with the `Transform` or `Render` APIs:

    implicit val cs: ContextShift[IO] = 
      IO.contextShift(ExecutionContext.global)
      
    val blocker = Blocker.liftExecutionContext(
      ExecutionContext.fromExecutor(Executors.newCachedThreadPool())
    )
    
    val transformer = Transformer
      .from(Markdown)
      .to(PDF)
      .using(GitHubFlavor)
      .io(blocker)
      .parallel[IO]
      .build
    
    transformer
      .fromDirectory("src")
      .toFile("hello.pdf")
      .transform


See [Using the Library API] for more details on these APIs.
    


### CSS for PDF

Laika offers the unusual, but convenient and probably unique feature of CSS styling for PDF.
It allows for customization in a syntax familiar to most users. However, the set of available 
attributes is quite different from what you know from web CSS, as PDF is a page-based format 
that requires features like page breaks or footnotes that are not available in the context
of a web page.

The following example shows the style for headers from Laika's default CSS:

    Header {
      font-family: sans-serif;
      font-weight: bold;
      font-size: 12pt;
    }

The font-related attributes in this case are identical to the ones you know from web CSS,
but the type selector does not refer to an HTML tag, but instead to a class name from the
hierarchy of case classes forming the document tree of a parsed input source.

The CSS files need to be placed into the root directory of your sources with a name
in the format `<name>.fo.css`. Like with template files you can place alternative CSS
files into subdirectories of your input tree for styles that should only be applied to 
that directory.

For an overview over the available attributes you can refer to the [Formatting Properties][fo-props] chapter
in the XSL-FO specification.

For an overview over supported type selectors see the sections following below.

[fo-props]: http://www.w3.org/TR/xsl11/#pr-section


#### Type Selectors

A type selector in the context of Laika's PDF support does not refer to the name of an HTML tag,
but instead to a class name from the hierarchy of case classes forming the document tree of a 
parsed input source. See [Elements Scaladoc][elements-scaladoc] for an overview of all node types.

[elements-scaladoc]: ../api/laika/ast/

Example:

    Paragraph {
      font-size: 12pt;
    }


#### Class Selectors

A class selector refers to a style attribute of a node in the document tree, similar to the
class attributes of HTML tags. Only a subset of Laika's nodes get rendered with style attributes.
Some exceptions are:

* Headers get rendered with a level style (`level1` to `levelN`)
* Titles get rendered with a `title` style
* An entry in the table of contents has a `toc` style
* Text nodes can have `.subscript` or `.superscript` styles
* Figures come with `caption` and `legion` styles

If you customize the renderer you can also add custom styles to any Laika node.

Example for using the `title` style:

    .title {
      font-size: 18pt;
    }


#### Id Selectors

An id selector refers to the unique id of a node in the document tree, similar to the id
of an HTML tag. Out of the box only a subset of nodes get rendered with an id: those that
internal links refer to, like headers, footnotes or citations.

Example:

    #my-header {
      font-size: 18pt;
    }
    

#### Selector Combinations

Like web CSS Laika supports combinations of selectors to refer to child elements
or define styles for multiple selectors at once:

* Combining type and style selectors: `Header.level1` refers to a `Header` node with a `level1` style
* Combining type and id selectors: `Header#my-title` refers to a `Header` node with the id `my-title`
* Referring to child elements: `Header .section-num` refers to a node with a style `section-num` as a child of a `Header` node
* Referring to immediate child elements: `Header > .section-num` refers to a node with a style `section-num` as an immediate child of a `Header` node
* Referring to multiple selectors: `Title, Header` refers to both, all Title and all Header nodes


#### Unsupported Selectors

Some selector types of the CSS specification are not supported as most of them do not add much value
in the context of a Laika document tree:

* Pseudo-classes like `:hover`
* Attibute selectors like `[attribute~=value]` (since Laika nodes do not have many properties)
* `Element1+Element2` or `Element1~Element2` for selecting based on sibling elements


### Configuration

There are several configuration options for PDF rendering that can be set
either in the file `directory.conf` in the root directory of your input sources
or programmatically on the `PDF` renderer.

In `directory.conf` you can set the following options:

    pdf {
      bookmarks.depth = 3
      toc.depth = 3
      toc.title = "Contents"
      coverImage = "cover.png"
    }  

The same options are available programmatically through the `withConfig` method on the `PDF` renderer:

    val config = PDFConfig(
      bookmarkDepth = 3,
      tocDepth = 3,
      tocTitle = Some("Contents")
    )
    
    val transformer = Transformer
      .from(Markdown)
      .to(PDF.withConfig(config))
      .build

These properties control the following aspects of the rendering:
 
* `bookmarkDepth` the number of levels bookmarks should be generated for, 
  you can use 0 to switch off bookmark generation entirely. Every level of the tree hierarchy will
  be considered for a bookmark entry: directories, files and sections within files.
  The default value is `Int.MaxValue`.
* `toc.depth` the number of levels to generate a table of contents for, 
  you can use 0 to switch off toc generation entirely. Every level of the tree hierarchy will
  be considered for an entry in the table of contents: directories, files and sections within files.
  The default value is `Int.MaxValue`.
* `toc.title` specifies the title for the table of contents. The default value is `None`.
* `coverImage` specifies the cover image for the book
  
For more details on these features see [Document Structure].


#### Customizing Apache FOP

Rendering a PDF file is (roughly) a 3-step process:

1. Parsing all markup files and producing an in-memory document tree representation
2. Applying templates and CSS to the document tree and render it as XSL-FO
3. Produce the final PDF file from the resulting XSL-FO

While step 1 and 2 are entirely managed by Laika, without any dependencies to external
libraries or tools, step 3 is almost solely taken care of by Apache FOP. 

Therefore, any customization for this step is best left for the configuration hooks of FOP itself.
They allow you to define aspects like the target resolution, custom fonts, custom stemmers and
a lot more.
The available options are described in the [Apache FOP documentation][fop-config-docs].

When you are using the sbt plugin, you can specify an Apache FOP configuration file with
the `fopConfig` setting:

    fopConfig := Some(baseDirectory.value / "customFop.xconf")
    
Note that the default is `None` as FOPs default configuration is often sufficient.

When you are using Laika embedded, the PDF renderer has a hook to specify a custom
`FopFactory`:

    val configFile = "/path/to/customFop.xconf"
    val factory = FopFactory.newInstance(new File(configFile))
    
    val transformer = Transformer
      .from(Markdown)
      .to(PDF.withFopFactory(factory))
      .build

Note that a `FopFactory` is a fairly heavy-weight object, so make sure that you reuse
either the `FopFactory` instance itself or the resulting `PDF` renderer.
In case you do not specify a custom factory, Laika ensures that the default
factory is reused between renderers.

[fop-config-docs]: https://xmlgraphics.apache.org/fop/2.1/configuration.html


### XSL-FO Templates

Like the HTML renderer, the PDF renderer supports templating. However, there should be
significantly less scenarios where you'd need to use them, as most of the visual aspects
of the rendering can be controlled by Laika's CSS for PDF feature. Since the PDF renderer
uses XSL-FO as its interim format, the templating is based on this format.

You can have a look at the [default XSL-FO template][default-fo-template] used 
by the PDF renderer for reference.

[default-fo-template]: https://github.com/planet42/Laika/blob/master/core/src/main/resources/templates/default.template.fo

You can override it if required by saving a custom template in a file called 
`default.template.fo` in the root directory of your input sources.


### Customizing the XSL-FO Renderer

Finally you can adjust the `fo` tags rendered for one or more node types
of the document tree programmatically with a simple partial function:

    val transformer = Transformer
      .from(Markdown)
      .to(PDF)
      .rendering {
        case (fmt, elem @ Emphasized(content, _)) => 
          fmt.inline(elem.copy(options = Style("myStyle")), content)   
      }
      .build

Note that in most cases the simpler way to achieve the same result will be
styling with CSS.

See [Customizing Renderers] for more details.


XSL-FO
------

XSL-FO is primarily intended as an interim format for producing PDF output, but you
can alternatively use it as the final output format and then post-process it with other
tools.

The XSL-FO renderer can be used with the `Transformer` or `Renderer` APIs:

    val result: String = Transformer
      .from(Markdown)
      .to(XSLFO)
      .build
      .transform("hello *there*)

    val doc: Document = Parser
      .of(Markdown)
      .build
      .parse("hello *there*")
      
    val html: String = Renderer
      .of(XSLFO)
      .build
      .render(doc)

See [Using the Library API] for more details on these APIs.
    
If you are using the sbt plugin you can use several of its task for generating
XSL-FO output:

* `laikaXSLFO` for transforming a directory of input files to XSL-FO
* `laikaGenerate xslfo <other formats>` for transforming a directory of input files to XSL-FO
  and other output formats with a single parse operation
  
See [Using the sbt Plugin] for more details. 

For customizing XSL-FO rendering the same approach applies as for PDF rendering
as the latter uses XSL-FO as an interim format. See the sections above on 
[CSS for PDF], [XSL-FO Templates] and [Customizing the XSL-FO Renderer] for more 
details.

The `XLSFO` renderer instance also has similar properties as the `HTML` renderer:
`withMessageLevel` and `unformatted` for controlling aspect of the output as 
described in [HTML Renderer Properties] and finally `withStyles` to programmatically
apply a custom CSS declarations.


Formatted AST
-------------

A renderer that visualizes the document tree structure, essentially a formatted
`toString` for a tree of case classes, mainly useful for testing and debugging
purposes.

You can use this renderer with the Transformer API:

    val input = "some *text* example"
    
    Transformer
      .from(Markdown)
      .to(AST)
      .build
      .transform(input)
    
    res0: java.lang.String = Document - Blocks: 1
    . Paragraph - Spans: 3
    . . Text - 'some '
    . . Emphasized - Spans: 1
    . . . Text - 'text'
    . . Text - ' example'

Alternatively you can use the Render API to render an existing document:
    
    val input = "some *text* example"
    
    val doc = Parser.of(Markdown).build.parse(input)
    
    Renderer.of(AST).build.render(doc)

The above will yield the same result as the previous example.

Finally, if you are using the sbt plugin you can use the `laikaAST` task.
