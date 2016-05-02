
Document Structure
==================

Laika adds several features on top of the supported markup languages
that make it easier to deal with structured content.

These features are enabled by default, but can be switched off
explicitly.

This is how you can switch them off for Markdown:

    Transform from Markdown.strict to 
      PDF fromFile "hello.md" toFile "hello.pdf"

And likewise, the same `strict` property is available for reStructuredText:

    Transform from ReStructuredText.strict to 
      HTML fromFile "hello.md" toFile "hello.html"


Document Title
--------------

The document title can then be accessed in a template through
a variable reference:

    <title>{{document.title}}</title>
    
Or in code through a `Document` instance:

    val doc: Document = ...
    doc.title // Seq[Span]
    
The title can be specified in two ways.


### First Headline in the Document

When the document contains only one level 1 headline as the first
headline, with all following section headlines being level 2 or lower,
then the first headline is automatically picked as the title.

This is the default behaviour of reStructuredText, but the original 
Markdown parser did not have the concept of a title.

In case there are multiple level 1 headlines, they are all just
interpreted as section headlines. In this case the document either
has no title or you must specify it explicitly with the second 
option below.


### Explicit Title in Configuration Headers

Like templates, markup documents can contain configuration headers,
enclosed between `{%` and `%}` at the start of the document:

    {%
      title: So long and thanks for all the fish
    %}
    
This would override a level 1 header, if present. Configuration
entries currently do not support inline markup, so it is interpreted
as plain text.



Document Sections
-----------------

All headlines except for the one that serves as the document title
(if present) will be used to build the section structure based
on the levels of the headlines.

These sections can then be referenced in templates:

    @:for "document.sections": {
      <li><a href="#{{id}}">{{title.content}}</a></li>
    } 
    
Or they can be accessed through a `Document` instance:

    val doc: Document = ...
    doc.sections // Seq[SectionInfo]
    
    
### Automatic Section Ids

Laika will automatically generate an id for each headline, so that
you can link to them. The id is derived from the title of the section
by removing all non-alphanumeric characters and connecting them
with dashes. So the title `Code of Conduct` would get the id
`code-of-conduct`.

This is the default behaviour of reStructuredText, so will happen
even when run in strict mode. Markdown does not add ids automatically,
so using strict mode will switch them off.

The ids are required in case you use the `toc` directive to generate
a table of contents, so you should not run Markdown in strict mode
when you intend to use the `toc` directive.



Document Fragments
------------------

Fragments allow to keep some sections of your document separate, to be rendered
in different locations of the output, like headers, footers or sidebars.

They produce a block element which is not part of the main body of the markup
document (so will not be rendered with a `{{document.content}}` reference).
Instead it can be referred to by `{{document.fragments.<fragmentName>}}`.

Example:

    @:fragment sidebar: This content will be *parsed* like all other
      content, but will be available separately from the document content.
      The block elements have to be indented.
  
      Therefore this line still belongs to the fragment.
      
      * As does
      * this list
  
    This line doesn't and will be part of the main document content.


Fragments are also available in code through a `Document` instance:

    val doc: Document = ...
    doc.fragments // Map[String, Element]



Cross Linking
-------------

Laika piggy-backs on the built-in linking syntax of both Markdown and reStructuredText
to add convenient cross linking between documents.

If you have the following headline in one of your documents:

    Monkey Gone To Heaven
    ---------------------

Then you can use the title as an id in link references.

Markdown:

    Here are the lyrics for [Monkey Gone To Heaven].
    
reStructuredText:

    Here are the lyrics for `Monkey Gone To Heaven`_.
    
Like with other link ids, Markdown let's
you specify link text and id separately:

    You have to listen to this [song][Monkey Gone To Heaven].
 
It does not matter whether the headline is located in the same
markup document or in another. In the latter case the headline only has
to be unique for the current directory. It does not have to be globally unique if you reference
it from within the same directory. So if you have a large number of chapters
in separate directories, they can all have a section with the title `Intro` for example.

If the id is not unique within a directory, you can alternatively specify the
target document explicitly:

    For details see the [Introduction][../main.md:Introduction].
    
The part of before the `:` is interpreted as the relative path to the target
document, the part after the colon as the id of the section.



Autonumbering Documents and Sections
------------------------------------

Laika supports auto-numbering of documents or sections or both. If you enable both
the section numbers will be added to the document number. E.g. in the document
with the number `2.1` the number for the first section will be `2.1.1`.

Auto-numbering can be switched on per configuration. Usually this is a global
switch, so you would add this section to a file named `directory.conf` inside
the root directory of your markup files:

    autonumbering {
      scope: all
      depth: 3
    }

The configuration above will number both documents and sections of documents,
but stop after the third level. Other possible values for the `scope` attribute
are `documents` (numbers documents, but not sections), `sections` (numbers
sections, but not documents) and `none` (the default, no autonumbering).

The numbers will be added to the headers of the sections and also appear
in tables of contents.



Table of Contents
-----------------

The standard `toc` directive allows to add a table of contents to templates
or text markup documents. Depending on the root of the tree to build a table
for and the depth you specify, such a table can span both, a list of documents
and then nested inside the sections of these documents.

In contrast to several similar tools content in Laika is hierarchical.
Subdirectories can contain markup files, too, and the hierarchy can get
visualized in a table of contents.

When using the default settings, you can simply use an empty tag:

    @:toc.
    
If you specify all available options it would look like this:

    @:toc title="List of Chapters" root="../intro" depth=2.
   
   
* The `title` attribute adds a title above the table. 

* The `depth` attribute
specifies how deeply the table should be nested, the default is unlimited.

* The `root` attribute
is a relative or absolute path (within the virtual tree of processed
documents, not an absolute file system path), pointing to the
root node of the table. Instead of specifying a path you can also
use three convenient special attributes:

    * `#rootTree`: Starts from the root of the document tree
    * `#currentTree`: Starts from the current tree (seen from the current
      document), so will include all sibling documents and their sections
    * `#currentDocument`: Builds a local table of contents, only from the
      sections of the current document.

The directive inserts `BulletList` objects into the tree (remember that directives
do not directly produce string output). The items get the styles
`toc` and `levelN` applied, with N being the level number. For
HTML these will then be rendered as class attributes.



Document Ordering
-----------------

For features like tables of contents and autonumbering the order of
documents is relevant. The default ordering is alphabetical, with
all markup documents coming first, followed by all subdirectories
in alphabetical order.

If you want a different ordering you can define it explicitly
for each directory in the `directory.conf` file:

    navigationOrder = [
      apples.md
      oranges.md
      strawberries.md
      some-subdirectory
      other-subdirectory
    ]
    
The example above contains both markup files and subdirectory.
They would appear in this order in tables of contents and the
same order would be applied when autonumbering.
    


Document Types
--------------

Laika recognizes several different document types inside the
directories it processes. The type of document is determined
by its name, in the following way:

* `directory.conf`: the configuration for this directory
* `*.conf`: other configuration files (currently ignored)
* `default.template.html`: the default template to apply to documents
  in this directory
* `*.template.html`: other templates that markup documents can
  explicitly refer to
* `*.<markup-suffix>`: markup files with a suffix recognized
  by the parser in use, e.g. `.md` or `.markdown` for Markdown
  and `.rst` for reStructuredText
* `*.dynamic.html`: a dynamic file, which has the same syntax
  as a template, but does not get applied to a markup document.
  This means it should not have a `{{document.content}}` reference
  like normal templates, but may use any of the other template
  features. The result of processing will be copied to the
  output directory (with the `.dynamic` part stripped from 
  its name) alongside the transformed markup documents.
* `*.git`, `*.svn`: these directories will be ignored
* all other files: treated as static files and copied to the 
  output directory unmodified.
  
If you need to customize the document type recognition,
you can do that with a simple function:

    val matcher: Path => DocumentType
    
    Transform from Markdown to HTML fromDirectory 
      "source" withDocTypeMatcher matcher toDirectory "target"

The valid return types correspond to the document types listed
above:

    Markup, Template, Dynamic, Static, Config, Ignored




