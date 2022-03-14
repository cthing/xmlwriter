# ![C Thing Software](https://www.cthing.com/branding/CThingSoftware-57x60.png "C Thing Software") xmlwriter
This library writes XML either as a SAX2 event filter or as a standalone XML writer. XML can be written
as-is or pretty printed. The library has no dependencies on Java XML serialization or JAXB.

### Standalone Usage
The XmlWriter class can be used standalone in applications that need to write XML. For standalone usage:
* Create an XmlWriter specifying the output destination
* As needed, configure the writer output properties (e.g. enable pretty printing)
* Begin the XML output by calling one of the {@link #startDocument() startDocument} methods. One 
  of these methods must be called before any other output methods.</li>
* Call XML output methods as needed (e.g. {@link #startElement(String) startElement}).
* Complete the XML output by calling the {@link #endDocument endDocument} method to properly terminate the
  XML document. No XML output methods can be called following the call to endDocument.
* Optionally, reuse the XmlWriter instance by calling the {@link #reset reset} method.

The following is an example of using a standalone XmlWriter to write a simple XML document to the standard output:
```
XmlWriter w = new XmlWriter();
w.startDocument();
w.startElement("elem1");
w.characters("Hello World");
w.endElement();
w.endDocument();
```
The following XML is displayed on the standard output:
```
<?xml version="1.0" standalone="yes"?>

<elem1>Hello World</elem1>
```

### SAX Filter Usage
The XmlWriter can be used as a SAX2 stream filter. The class receives SAX events from the downstream XMLReader, 
outputs the appropriate XML, and forwards the event to the upstream filter. For usage as a SAX filter:
* Create a SAX parser using the appropriate factory.
* Create an XmlWriter specifying the reader corresponding to the parser and the output destination.
* Call the XmlWriter's parse method to parse the document and write its XML to the output. The parse
  method must be called on the outermost object in the filter chain.
* When used as part of a filter chain, the XmlWriter is not reused.

In the following example, the XmlWriter is used to output XML that is being parsed by an
`org.xml.sax.XMLReader` and written to the standard output.
```
final SAXParserFactory factory = SAXParserFactory.newInstance();
factory.setNamespaceAware(true);
factory.setValidating(false);
factory.setXIncludeAware(false);

final SAXParser parser = factory.newSAXParser();
final XMLReader xmlReader = parser.getXMLReader();
final XmlWriter xmlWriter = new XmlWriter(xmlReader);

xmlWriter.setProperty("http://xml.org/sax/properties/lexical-handler", xmlWriter);
xmlWriter.parse(new InputSource(new FileReader("Foo.xml")));
```

### Additional Details
See the [Javadoc in the XmlWriter](src/main/java/org/cthing/xmlwriter/XmlWriter.java) class for additional details
on the API. See the [State Machine document](dev/docs/StateMachine.md) for details on the state machine at the
heart of the `XmlWriter` class.

### Building
The libray is compiled for Java 11.

Gradle is used to build the library:
```
./gradlew build
```
