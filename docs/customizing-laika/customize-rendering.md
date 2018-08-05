
Customizing Renderers
=====================

In some cases you might want to override the output of a renderer for a few types
of document tree nodes only, while keeping the default for the rest. Both the
Transform and Render APIs offer a hook to easily do that without modifying
or extending the existing renderer. 

This is the signature of a custom renderer hook:

    W => PartialFunction[Element,Unit]
    
`W` is a generic type representing the writer API which is different for each 
output format. For HTML it is `HTMLWriter`, for XSL-FO it is `FOWriter`. 
This way renderers can offer the most convenient API for a specific output format.

Creating a function that expects a writer instance and returns the actual custom
render function in form of a partial function allows to 'capture' the writer
in a concise way like the examples below will show.
  
  
  
Defining a Render Function
--------------------------

This section explains how a render function is implemented and the subsequent sections
show the three different ways to register such a function.

In the following example only the HTML output for emphasized text will be modified,
adding a specific style class:

    val open = """<em class="big">"""
    val close = "</em>"

    val renderer: HTMLWriter => RenderFunction = { out => 
      { case Emphasized(content, _) => out << open << content << close } 
    }

For all node types where the partial function is not defined, the default renderer
will be used.

Multiple custom renderers can be specified for the same transformation, they will be 
tried in the order you added them, falling back to the default in case none is defined 
for a specific node.

The `content` value above is of type `Seq[Span]`. `<<` and other methods of the
`HTMLWriter` API are overloaded and accept `String`, `Element` or `Seq[Element]` 
as a parameter, with `Element` being the abstract base type of all tree nodes.
This way rendering child elements can be delegated to other renderers, either another
custom renderer or the default. 

In almost all cases, a custom renderer should not render
the children of a node passed to the function itself.



Registering a Render Function
-----------------------------

The mechanism is slightly different, depending on whether you are using the sbt
plugin or Laika embedded in an application. In the latter case you have two
choices, one for performing a full transformation, the other for a separate
render operation. All three options are described below.


### Using the sbt Plugin

In `build.sbt`:

    import laika.tree.Elements._
    
    laikaSiteRenderers += laikaSiteRenderer { out => {
      case Emphasized(content, _) => 
          out << """<em class="big">""" << content << "</em>" 
    }}

    
### Using the Transform API

    val open = """<em class="big">"""
    val close = "</em>"

    Transform from Markdown to HTML rendering { out => 
      { case Emphasized(content, _) => out << open << content << close } 
    } fromFile "hello.md" toFile "hello.html"
    

### Using the Render API

    val doc: Document = ...
    
    val open = """<em class="big">"""
    val close = "</em>"
    
    Render as HTML using { out => 
      { case Emphasized(content, _) => out << open << content << close } 
    } from doc toString
    


The Writer APIs
---------------

In the examples above we only needed the basic `<<` method. This secion provides
an overview over the full API.

Btw: in case you do not fancy operators as method names, the writer APIs are the only ones
in Laika making use of them. It turned out to improve readability for most common
scenarios where you have to chain lots of elements, often including indentation
and newline characters for making the output prettier. There are not that many,
so it should be easy to memorize them.

Writers differ from all other Laika objects in that they are stateful. But the
only state they keep internally is the current level of indentation for prettier
output which greatly simplifies the use of the API for rendering elements
recursively. A new writer instance is created for each render operation,
so there is no need to share it between threads.  


### TextWriter

This is the base API supported by both the `XSL-FO` and `HTML` renderer,
both of them adding several methods with rendering logic specific to that format.

All methods below are overloaded and accept `String`, `Element` or `Seq[Element]` 
as a parameter, with `Element` being the abstract base type of all tree nodes.
When passing Elements to the writer it will delegate to the renderers responsible
for those nodes.

* `<<` appends to the output on the current line.

* `<<|` appends to the output on a new line, with the current level of indentation

* `<<|>` appends to the output on a new line, increasing indentation one level to the right

When using the last method with Element instances which might themselves delegate
rendering of their children, you get a nicely formatted output without much effort.


### HTMLWriter

This writer supports all methods of the `TextWriter` API shown above, and adds
the following methods:

* For the three methods above, there is a variant that replaces special HTML
  characters with HTML entities and should be used for writing text nodes. They
  are named `<<&`, `<<|&` and `<<|>&` and otherwise behave the same as their
  counterparts without the `&`.
  
* The `<<<&` method does not have a counterpart without `&`. Like the others
  it replaces special HTML characters, but it writes without any indentation,
  no matter which level of indentation the writer currently has,
  which is needed for writing nodes like `<pre>` where indentation would be
  significant. 
  
* The `<<@` method is a convenient way to write an HTML attribute. It is overloaded
  and either takes `(String, String)` or `(String, Option[String])` as parameters
  for name and value of the attribute. If the value is None, nothing will be written,
  but it often makes writing optional attributes more concise.


### Themes

A theme is a collection of customized renderers as shown in the previous sections,
plus optionally default templates and/or static files to be copied to the output
directory.

This is the signature of the `Theme` case class:

    case class Theme (customRenderer: W => RenderFunction,
                      defaultTemplate: Option[TemplateRoot],
                      defaultStyles: StyleDeclarationSet,
                      staticDocuments: StaticDocuments)

* The `customRenderer` is a renderer function that overrides the built-in renderers
  for one or more nodes, see the sections above for details on how to write such a function.
  
* The `defaultTemplate` is the AST for the template to embed the render result in, 
  overriding the default template of the library. You can create the AST programmatically
  or use Laika's templating syntax and parse it from a file or the resource directory if
  you bundle your theme in a jar:
  
      DefaultTemplateParser.parse(Input.fromClasspath(
        "/templates/default.template.html", Root / "default.template.html"
      )) 

* The `defaultStyles` allow you to customize the default CSS for a format. This
  is currently only processed for PDF, as it is the only format where Laika processes
  the CSS and applies it to the render result. If you want to add CSS for HTML, add them
  as static files instead, as Laika will just copy them over without processing them.
  
* The `staticDocuments` provides a collection of files to be copied over to the output
  directory on each transformation. You can bundle images, script or CSS files this way:
  
      val docs = StaticDocuments.fromDirectory("theme-files")
      
A theme can be installed as part of an extension bundle:

    object MyExtensions extends ExtensionBundle {
    
      override val themes = Seq(HTML.Theme(...), PDF.Theme(...))
        
    }
    
    Transform.from(Markdown).to(HTML).using(MyExtensions)
      .fromFile("hello.md").toFile("hello.html")         

A theme is specific to a particular output format, separate instances need to be 
provided if you want to install themes for several formats like HTML and PDF.
