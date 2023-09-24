# ![C Thing Software](https://www.cthing.com/branding/CThingSoftware-57x60.png "C Thing Software") xmlwriter

[![CI](https://github.com/cthing/xmlwriter/actions/workflows/ci.yml/badge.svg)](https://github.com/cthing/xmlwriter/actions/workflows/ci.yml)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.cthing/xmlwriter/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.cthing/xmlwriter)
[![javadoc](https://javadoc.io/badge2/org.cthing/xmlwriter/javadoc.svg)](https://javadoc.io/doc/org.cthing/xmlwriter)

This library writes XML either as a SAX2 event filter or as a standalone XML writer. XML can be written
as-is or pretty printed. The library has no dependencies on Java XML serialization or JAXB.

## Usage
The library is available from [Maven Central](https://repo.maven.apache.org/maven2/org/cthing/xmlwriter/) using the following Maven dependency:
```xml
<dependency>
  <groupId>org.cthing</groupId>
  <artifactId>xmlwriter</artifactId>
  <version>2.0.0</version>
</dependency>
```
or the following Gradle dependency:
```kotlin
implementation("org.cthing:xmlwriter:2.0.0")
```

### Standalone Usage
The XmlWriter class can be used standalone in applications that need to write XML. For standalone usage:
* Create an `XmlWriter` instance specifying the output destination
* Configure the writer output properties (e.g. enable pretty printing) as needed
* Begin the XML output by calling one of the `startDocument` methods. One 
  of these methods must be called before any other output methods.
* Call XML output methods as needed (e.g. `startElement`).
* Complete the XML output by calling the `endDocument` method to properly terminate the
  XML document. No XML output methods can be called following the call to `endDocument`.
* Optionally, reuse the XmlWriter instance by calling the `reset` method.

The following example uses a standalone `XmlWriter` to write a simple XML document to the standard output:
```java
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
The `XmlWriter` can be used as a SAX2 stream filter. The class receives SAX events from a downstream `XMLReader`, 
outputs the appropriate XML, and forwards events to the upstream filter. For usage as a SAX filter:
* Create a SAX parser using the appropriate factory
* Create an `XmlWriter` instance specifying the reader corresponding to the parser and the output destination
* Call the `XmlWriter.parse` method to parse the document and write its XML to the output. The `parse`
  method must be called on the outermost object in the filter chain.
* When used as part of a filter chain, an `XmlWriter` instance cannot be not reused

In the following example, the `XmlWriter` is used to output XML that is being parsed by an
`org.xml.sax.XMLReader` and written to the standard output.
```java
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

### Detailed Usage and Design
See the [Javadoc in the XmlWriter](https://javadoc.io/doc/org.cthing/xmlwriter/latest/org/cthing/xmlwriter/XmlWriter.html) class for detailed
usage and configuration information. See the [State Machine document](dev/docs/StateMachine.md) for details on the formatter state machine
at the heart of the `XmlWriter` class.

### Building
The libray is compiled for Java 17. If a Java 17 toolchain is not available, one will be downloaded.

Gradle is used to build the library:
```
./gradlew build
```
