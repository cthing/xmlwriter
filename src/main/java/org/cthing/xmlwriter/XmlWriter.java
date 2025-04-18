/*
 * Copyright 2022 C Thing Software
 * SPDX-License-Identifier: Apache-2.0
 */
package org.cthing.xmlwriter;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;

import org.cthing.annotations.AccessForTesting;
import org.cthing.escapers.XmlEscaper;
import org.jspecify.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Attributes2;
import org.xml.sax.ext.Attributes2Impl;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;
import org.xml.sax.helpers.XMLFilterImpl;


/**
 * This class writes XML either as a SAX2 event filter or as a standalone XML writer. XML can be written
 * as-is or pretty printed.
 *
 * <h2>Standalone Usage</h2>
 *
 * <p>The XmlWriter class can be used standalone in applications that need to write XML. For standalone usage:</p>
 * <ol>
 *      <li>Create an XmlWriter specifying the output destination.</li>
 *      <li>As needed, configure the writer output properties (e.g. enable pretty printing).</li>
 *      <li>Begin the XML output by calling one of the {@link #startDocument() startDocument} methods.
 *          One of these methods must be called before any other output methods.</li>
 *      <li>Call XML output methods as needed (e.g. {@link #startElement(String) startElement}).</li>
 *      <li>Complete the XML output by calling the {@link #endDocument endDocument} method to properly
 *          terminate the XML document. No XML output methods can be called following the call to endDocument.</li>
 *      <li>Optionally, reuse the XmlWriter instance by calling the {@link #reset reset} method.</li>
 * </ol>
 *
 * <p>The following is an example of using a standalone XmlWriter to write a simple XML document to the standard
 * output:</p>
 * <pre>
 * XmlWriter w = new XmlWriter();
 *
 * w.startDocument();
 * w.startElement("elem1");
 * w.characters("Hello World");
 * w.endElement();
 * w.endDocument();
 * </pre>
 *
 * <p>The following XML is displayed on the standard output:</p>
 * <pre>
 * &lt;?xml version="1.0" standalone="yes"?&gt;
 *
 * &lt;elem1&gt;Hello World&lt;/elem1&gt;
 * </pre>
 *
 * <h2>SAX Filter Usage</h2>
 *
 * <p>The XmlWriter can be used as a SAX2 stream filter. The class receives SAX events from the downstream XMLReader,
 * outputs the appropriate XML, and forwards the event to the upstream filter. For usage as a SAX filter:</p>
 * <ol>
 *      <li>Create a SAX parser using the appropriate factory.</li>
 *      <li>Create an XmlWriter specifying the reader corresponding to the parser and the output destination.</li>
 *      <li>Call the XmlWriter's parse method to parse the document and write its XML to the output. The parse
 *          method must be called on the outermost object in the filter chain.</li>
 *      <li>When used as part of a filter chain, the XmlWriter is not reused.</li>
 * </ol>
 *
 * <p>In the following example, the XmlWriter is used to output XML that is being parsed by an
 * {@link org.xml.sax.XMLReader XMLReader} and written to the standard output.</p>
 * <pre>
 * final SAXParserFactory factory = SAXParserFactory.newInstance();
 * factory.setNamespaceAware(true);
 * factory.setValidating(false);
 * factory.setXIncludeAware(false);
 *
 * final SAXParser parser = factory.newSAXParser();
 * final XMLReader xmlReader = parser.getXMLReader();
 * final XmlWriter xmlWriter = new XmlWriter(xmlReader);
 *
 * xmlWriter.setProperty("http://xml.org/sax/properties/lexical-handler", xmlWriter);
 * xmlWriter.parse(new InputSource(new FileReader("Foo.xml")));
 * </pre>
 *
 * <p>An XmlWriter can process more SAX events than those specified in the
 * {@link org.xml.sax.helpers.XMLFilterImpl XMLFilterImpl} base class. Specifically, the XmlWriter can process
 * the DTD, CDATA and comment events reported by the {@link org.xml.sax.ext.LexicalHandler LexicalHandler}
 * interface. The call to {@link org.xml.sax.helpers.XMLFilterImpl#setProperty(java.lang.String, java.lang.Object)
 * setProperty} in the above example, allows the XmlWriter to receive these additional events.</p>
 *
 * <p><strong>Note:</strong> When an XmlWriter is used in a SAX filter chain, the input XML header is not output.
 * Instead, a generic XML header is generated. See the {@link #startDocument() startDocument} and
 * {@link #setStandalone(boolean) setStandalone} methods for more information. In addition, internal DTD subsets are
 * not output.</p>
 *
 * <h2>Namespace Support</h2>
 *
 * <p>The XmlWriter fully supports XML namespaces including namespace prefix management and writing {@code xmlns}
 * attributes.</p>
 *
 * <p>The <a href="http://www.w3.org/TR/REC-xml-names/">XML namespace specification</a> states that element and
 * attribute names consist of a namespace URI, a local name and possibly a qualified name as shown in the following
 * example:
 * </p>
 * <pre>
 * &lt;ns1:elem1 xmlns:ns1="https://www.cthing.com/foo"&gt;
 * </pre>
 *
 * <p>In the above example, the element's qualified name is {@code ns1:elem1} consisting of the namespace prefix
 * {@code ns1} and the local name {@code elem1}. The element is associated with the namespace URI
 * {@code https://www.cthing.com/foo} using the namespace declaration attribute. An element belongs to the default
 * namespace when the prefix is not specified:</p>
 * <pre>
 * &lt;elem1 xmlns="https://www.cthing.com/foo"&gt;
 * </pre>
 *
 * <p>The XmlWriter provides many overloaded methods for writing elements and attributes with various combinations
 * of namespace components: namespace URI, local name, and qualified name. The namespace URI is used to look up a
 * prefix for the element. If a prefix cannot be found, but there is a prefix on the qualified name, that prefix is
 * used. If there is no prefix on the qualified name, a prefix is synthesized by the XmlWriter. The synthesized
 * prefixes are of the form {@code __NS}<em>n</em> where <em>n</em> is a positive integer starting at one. The
 * following are examples of start element method calls and their corresponding XML output:</p>
 *
 * <pre>
 *    startElement("elem1");
 *    &lt;elem1&gt;
 * </pre>
 * <pre>
 *    startElement("https://www.cthing.com/foo", "elem1");
 *    &lt;__NS1:elem1 xmlns:__NS1="https://www.cthing.com/foo"&gt;
 * </pre>
 * <pre>
 *    startElement("https://www.cthing.com/foo", "elem1", "n1:elem1");
 *    &lt;n1:elem1 xmlns:n1="https://www.cthing.com/foo"&gt;
 * </pre>
 *
 * <p>As described above, the XmlWriter will generate a namespace prefix if one cannot be found. A namespace prefix
 * can be specified by always using a qualified element and attribute names. A less verbose solution is to pre-register
 * the mapping of namespace URI's to prefixes using the {@link #addNSPrefix(String, String) addNSPrefix} method.
 * Without prefix mapping the following code produces XML that uses a synthesized prefix.</p>
 * <pre>
 *    startElement("elem1");
 *    emptyElement("https://www.cthing.com/foo", "elem2");
 *    emptyElement("https://www.cthing.com/foo", "elem3");
 *    endElement();
 *
 *    &lt;elem1&gt;
 *        &lt;__NS1:elem2 xmlns:__NS1="https://www.cthing.com/foo"/&gt;
 *        &lt;__NS1:elem3 xmlns:__NS1="https://www.cthing.com/foo"/&gt;
 *    &lt;/elem1&gt;
 * </pre>
 *
 * <p>A more appropriate prefix can be specified using the {@link #addNSPrefix(String, String) addNSPrefix} method</p>
 * <pre>
 *    addNSPrefix("n1", "https://www.cthing.com/foo");
 *    startElement("elem1");
 *    emptyElement("https://www.cthing.com/foo", "elem2");
 *    emptyElement("https://www.cthing.com/foo", "elem3");
 *    endElement();
 *
 *    &lt;elem1&gt;
 *        &lt;n1:elem2 xmlns:n1="https://www.cthing.com/foo"/&gt;
 *        &lt;n1:elem3 xmlns:n1="https://www.cthing.com/foo"/&gt;
 *    &lt;/elem1&gt;
 * </pre>
 *
 * <p>The addNSPrefix method specifies namespace prefixes, but as shown in the above example, the namespaces
 * declarations appear on every element leading to nearly unreadable verbose XML. Typically, namespaces are declared
 * once on the root element. To achieve this using the XmlWriter, call one of the
 * {@link #addNSRootDecl(String) addNSRootDecl} methods. One form of the addNSRootDecl method allows a namespace to
 * be pre-declared on the root element and a prefix to be defined for that namespace in one method call as
 * shown in the following example:</p>
 * <pre>
 *    addNSRootDecl("ns1", "https://www.cthing.com/foo");
 *    startElement("elem1");
 *    emptyElement("https://www.cthing.com/foo", "elem2");
 *    emptyElement("https://www.cthing.com/foo", "elem3");
 *    endElement();
 *
 *    &lt;elem1 xmlns:ns1="https://www.cthing.com/foo"&gt;
 *        &lt;ns1:elem2/&gt;
 *        &lt;ns1:elem3/&gt;
 *    &lt;/elem1&gt;
 * </pre>
 *
 * <p>The default namespace can be specified by calling the addNSPrefix or addNSRootDecl method with an empty string
 * ("") for the prefix as shown in the following example:</p>
 * <pre>
 *    addNSRootDecl("", "https://www.cthing.com/foo");
 *    startElement("elem1");
 *    emptyElement("https://www.cthing.com/foo", "elem2");
 *    emptyElement("https://www.cthing.com/foo", "elem3");
 *    endElement();
 *
 *    &lt;elem1 xmlns="https://www.cthing.com/foo"&gt;
 *        &lt;elem2/&gt;
 *        &lt;elem3/&gt;
 *    &lt;/elem1&gt;
 * </pre>
 *
 * <h2>Pretty Printing</h2>
 *
 * <p>The XmlWriter can format the XML output and provides a number of options for controlling the appearance of the
 * output.</p>
 *
 * <p><strong>Basic Pretty Printing -</strong> To enable pretty printing, call the
 * {@link #setPrettyPrint(boolean) setPrettyPrint} method with a value of {@code true}. This produces XML output with
 * appropriate element indentation and newline insertion. The character string used for indentation can be specified
 * using the {@link #setIndentString(String) setIndentString} methods. In addition to the indentation string, a
 * starting offset for the line can be specified. Pretty printing this XML:</p>
 * <pre>
 * &lt;?xml version="1.0" standalone="yes"?&gt;
 * &lt;elem1&gt;&lt;elem2&gt;Hello World&lt;/elem2&gt;&lt;/elem1&gt;
 * </pre>
 *
 * <p>results in this output:</p>
 * <pre>
 * &lt;?xml version="1.0" standalone="yes"?&gt;
 *
 * &lt;elem1&gt;
 *     &lt;elem2&gt;Hello World&lt;/elem2&gt;
 * &lt;/elem1&gt;
 * </pre>
 *
 * <p><strong>Pretty Printing Attributes -</strong> By default the XmlWriter writes element attributes on the same
 * line as the element name. Call {@link #setAttrPerLine(boolean) setAttrPerLine} with a value of {@code true} to
 * have each attribute written on a separate line. The formatting of attributes can be selected independently basic
 * pretty printing. By default, element attributes are written as:</p>
 * <pre>
 * &lt;elem1 a1="v1" a2="v2" a3="v3"/&gt;
 * </pre>
 *
 * <p>Using attribute formatting, element attributes are written as:</p>
 * <pre>
 * &lt;elem1
 *     a1="v1"
 *     a2="v2"
 *     a3="v3"
 * /&gt;
 * </pre>
 *
 * <p><strong>Empty Tag Minimization -</strong> By default, if a start element is followed immediately by an end
 * element, the XmlWriter combines these into a single empty element. For example, the elements
 * {@code &lt;foo&gt;&lt;/foo&gt;} are written as {@code &lt;foo/&gt;}. This empty tag minimization is controlled
 * using the {@link #setMinimizeEmpty(boolean) setMinimizeEmpty} method.</p>
 *
 * <p>Output formatting methods can be called at any time during XML writing. This allows formatting to be applied
 * selectively to different portions of the XML output. For example, elements with many attributes can be output
 * with one attribute per line while elements with only a few attributes can be output all on the same line as the
 * element name.</p>
 *
 * <h2>Default Attribute Handling</h2>
 *
 * <p>Certain element attributes exist only because a default value for them is specified in a DTD or schema.
 * By default, the XmlWriter does not output these attributes. To write these attribute values call the
 * {@link #setSpecifiedAttributes(boolean) setSpecifiedAttributes} method with a value of {@code false}. This method
 * can be called at any time during XML writing.</p>
 *
 * <p><strong>Note:</strong> The handling of default attributes is only relevant when the XmlWriter is used in a SAX
 * filter chain. The SAX2 extensions must be supported by an XML reader so that the XmlWriter can determine whether an
 * attribute has been explicitly specified. SAX2 extensions are supported when the
 * {@code http://xml.org/sax/features/use-attributes2} flag is {@code true}.</p>
 *
 * <h2>Acknowledgments</h2>
 *
 * <p>The ability to use an XML writer in a SAX filter stream was demonstrated by
 * <a href="mailto:david@megginson.com">David Megginson</a> in his
 * <a href="https://mvnrepository.com/artifact/com.megginson.sax/xml-writer">XmlWriter</a>, which is in the
 * public domain. The algorithm for determining a namespace prefix also comes from Megginson's XmlWriter.</p>
 *
 * @see <a href="https://mvnrepository.com/artifact/com.megginson.sax/xml-writer">XmlWriter</a>
 * @see org.xml.sax.helpers.XMLFilterImpl
 * @see org.xml.sax.ext.LexicalHandler
 * @see org.xml.sax.XMLReader
 * @see <a href="http://www.saxproject.org/">SAX</a>
 */
@SuppressWarnings({ "UnusedReturnValue", "JavadocLinkAsPlainText" })
public class XmlWriter extends XMLFilterImpl implements LexicalHandler {

    /**
     * When pretty printing, certain XML constructs can be written either
     * inline or in a block format. These hints indicate which formatting
     * should be used, when possible.
     */
    public enum FormattingHint {
        /** Use inline layout where appropriate. */
        INLINE,

        /** Use block layout where appropriate. */
        BLOCK
    }


    /**
     * Represents the state of the writer.
     */
    private enum State {
        BEFORE_DOC_STATE,
        BEFORE_ROOT_STATE,
        IN_START_TAG_STATE,
        IN_CDATA_STATE,
        IN_DTD_STATE,
        AFTER_TAG_STATE,
        AFTER_DATA_STATE,
        AFTER_ROOT_STATE,
        AFTER_DOC_STATE
    }


    /**
     * Represents a state transition event.
     */
    private enum Event {
        ATTRIBUTE_EVENT,
        INLINE_REF_EVENT,
        BLOCK_REF_EVENT,
        CHARACTERS_EVENT,
        COMMENT_EVENT,
        END_CDATA_EVENT,
        END_DOCUMENT_EVENT,
        END_DTD_EVENT,
        END_ELEMENT_EVENT,
        NEWLINE_EVENT,
        PI_EVENT,
        START_CDATA_EVENT,
        START_DOCUMENT_EVENT,
        START_DTD_EVENT,
        START_ELEMENT_EVENT
    }


    private static final String DEFAULT_XML_VERSION = "1.0";
    private static final String EMPTY_STR = "";
    private static final String CDATA = "CDATA";
    private static final String DEF_INDENT = "    ";
    private static final String DEF_OFFSET = "";
    private static final String SYNTH_NS_PREFIX = "__NS";
    private static final AttributesImpl EMPTY_ATTRS = new AttributesImpl();

    /** All output is written to this writer. */
    private Writer out;

    /** Should output be formatted. */
    private boolean prettyPrint;

    /** Options controlling the escaping behavior. */
    private final Set<XmlEscaper.Option> escapeOptions;

    /** Indent string. */
    private String indentStr;

    /** Indent offset string. */
    private String offsetStr;

    /** Has an offset string been specified. */
    private boolean haveOffsetStr;

    /** Turn &lt;foo&gt;&lt;/foo&gt; into &lt;foo/&gt;. */
    private boolean minimize;

    /** Write one attribute per line. */
    private boolean attrPerLine;

    /** Whether to write defaulted attributes. */
    private boolean specifiedAttr;

    /** Version of XML being used. The default is 1.0. */
    private String xmlVersion;

    /** Standalone value when used as filter. */
    private boolean standalone;

    /** Current processing state. */
    private State currentState;

    /** Stack of open elements. */
    private final ElementStack elementStack;

    /** Namespace management. */
    private final NamespaceSupport nsSupport;

    /** Used in creating a namespace prefix. */
    private int nsPrefixCounter;

    /** Maps namespace URI to a prefix. */
    private final Map<String, String> nsPrefixMap;

    /** Prefix to namespace URI mapping. */
    private final Map<String, String> nsDeclMap;

    /** Sets of namespace URIs to declare on the root element. */
    private final Set<String> nsRootDeclSet;


    /**
     * Represents an XML entity.
     */
    public static class Entity {
        private final String name;
        @Nullable
        private final String value;
        @Nullable
        private final String publicId;
        @Nullable
        private final String systemId;
        @Nullable
        private final String notationName;

        /**
         * Defines an internal general entity. A collection of instances of this class can be passed to the
         * {@link XmlWriter#doctype(String, String, String, Collection, Collection) doctype} method to declare
         * the entities using an internal DTD subset.
         *
         * @param entName Specifies the name for the entity
         * @param entValue Specifies the value for the entity
         */
        public Entity(final String entName, final String entValue) {
            this.name = entName;
            this.value = entValue;
            this.publicId = null;
            this.systemId = null;
            this.notationName = null;
        }

        /**
         * Defines an external general entity.
         *
         * @param entName Specifies the name for the entity
         * @param entPublicId Specifies the public ID for the entity or {@code null} if a public ID is not available.
         * @param entSystemId Specifies the system ID for the entity (cannot be {@code null}).
         * @param entNotationName Specifies the name of a notation or {@code null} if there is no notation reference.
         */
        public Entity(final String entName, @Nullable final String entPublicId, final String entSystemId,
                      @Nullable final String entNotationName) {
            this.name = entName;
            this.value = null;
            this.publicId = entPublicId;
            this.systemId = entSystemId;
            this.notationName = entNotationName;
        }

        /**
         * Provides the name for the entity.
         *
         * @return Returns the entity name.
         */
        public String getName() {
            return this.name;
        }

        /**
         * Provides the name of the notation, if any.
         *
         * @return Returns the notationName.
         */
        @Nullable
        public String getNotationName() {
            return this.notationName;
        }

        /**
         * Provides the public identifier.
         *
         * @return Returns the public identifier.
         */
        @Nullable
        public String getPublicId() {
            return this.publicId;
        }

        /**
         * Provides the system identifier.
         *
         * @return Returns the system identifier.
         */
        @Nullable
        public String getSystemId() {
            return this.systemId;
        }

        /**
         * Provides the entity value.
         *
         * @return Returns the entity value.
         */
        @Nullable
        public String getValue() {
            return this.value;
        }
    }


    /**
     * Represents an XML notation. A collection of instances of this class can be passed to the
     * {@link XmlWriter#doctype(String, String, String, Collection, Collection) doctype} method
     * to declare the notations using an internal DTD subset.
     *
     * @param name Specifies the name for the notation
     * @param publicId Specifies the public ID for the notation or {@code null} if a public ID is not available.
     * @param systemId Specifies the system ID for the notation or {@code null} if a system ID is not available.
     */
    public record Notation(String name, @Nullable String publicId, @Nullable String systemId) {
    }


    /**
     * Creates an XML writer that writes to the standard output.
     */
    public XmlWriter() {
        this(null, null);
    }

    /**
     * Creates an XML writer that writes to the specified writer.
     *
     * @param writer Output destination or {@code null} to use the standard output. The writer will not be closed.
     */
    public XmlWriter(@Nullable final Writer writer) {
        this(null, writer);
    }

    /**
     * Creates an XML writer in a filter chain with the specified reader as
     * the parent.
     *
     * @param reader Parent in the filter chain or {@code null} if there is no chain. The reader will not be closed.
     */
    public XmlWriter(@Nullable final XMLReader reader) {
        this(reader, null);
    }

    /**
     * Creates an XML writer in a filter chain with the specified reader as the parent and the specified
     * writer as the output destination.
     *
     * @param reader Parent in the filter chain or {@code null} if there is no chain. The reader will not be closed.
     * @param writer Output destination of {@code null} to use the standard output. The writer will not be closed.
     */
    public XmlWriter(@Nullable final XMLReader reader, @Nullable final Writer writer) {
        super(reader);

        this.elementStack = new ElementStack();
        this.nsSupport = new NamespaceSupport();
        this.nsPrefixMap = new HashMap<>();
        this.nsDeclMap = new HashMap<>();
        this.nsRootDeclSet = new HashSet<>();
        this.prettyPrint = false;
        this.escapeOptions = EnumSet.noneOf(XmlEscaper.Option.class);
        this.minimize = true;
        this.indentStr = DEF_INDENT;
        this.offsetStr = DEF_OFFSET;
        //noinspection ConstantConditions
        this.haveOffsetStr = DEF_OFFSET.isEmpty();
        this.attrPerLine = false;
        this.specifiedAttr = true;
        this.xmlVersion = DEFAULT_XML_VERSION;
        this.standalone = true;
        this.currentState = State.BEFORE_DOC_STATE;
        this.out = setOutput(writer);
    }

    /**
     * Resets the XML writer to its initial state so that it can be reused. After {@link #endDocument() endDocument},
     * the reset method must be called before the XmlWriter can be reused for output.
     */
    public final void reset() {
        this.elementStack.clear();
        this.nsSupport.reset();
        this.nsPrefixCounter = 0;
        this.currentState = State.BEFORE_DOC_STATE;
    }

    /**
     * Flushes the output. This method is especially useful for ensuring that the entire document has been output
     * without having to close the writer.
     *
     * <p>This method is invoked automatically by the {@link #endDocument endDocument} method.
     *
     * @throws SAXException If a problem occurred while flushing the writer an IOException wrapped in a SAXException
     *         is thrown.
     * @see #reset
     */
    public void flush() throws SAXException {
        try {
            this.out.flush();
        } catch (final IOException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Sets a new output destination for the writer.
     *
     * @param writer New output writer to set. If the value of this parameter is {@code null}, the standard
     *         output is used. The writer will not be closed.
     * @return The newly set writer.
     */
    public final Writer setOutput(@Nullable final Writer writer) {
        this.out = (writer == null) ? new OutputStreamWriter(System.out, StandardCharsets.UTF_8) : writer;
        return this.out;
    }

    /**
     * Returns the output destination.
     *
     * @return Output destination for the writer.
     */
    public Writer getOutput() {
        return this.out;
    }

    /**
     * Parses an XML document using the writer as a filter.
     *
     * @param reader Provides the XML document. The reader will not be closed.
     * @throws IOException if there was a problem reading the XML document.
     * @throws SAXException if there was a problem parsing the XML document.
     */
    public void parse(final Reader reader) throws IOException, SAXException {
        parse(new InputSource(reader));
    }

    /**
     * Adds the specified prefix for the specified namespace URI. For more information refer to the discussion
     * of namespace support in the class overview documentation.
     *
     * <p>Note that this method does not force the namespace to be declared on the root element. To do that use the
     * {@link #addNSRootDecl(String) addNSRootDecl} method.
     *
     * @param prefix Prefix for the namespace URI. Use an empty string ("") to specify the default namespace.
     * @param uri URI to be represented by the specified prefix
     * @return This class instance
     * @see #addNSRootDecl(String)
     * @see #addNSRootDecl(String, String)
     */
    public XmlWriter addNSPrefix(final String prefix, final String uri) {
        this.nsPrefixMap.put(uri, prefix);
        return this;
    }

    /**
     * Forces the specified namespace to be declared on the root element.
     *
     * <p>By default, the XmlWriter will declare namespaces on elements as needed. This may result in clutter as the
     * same namespace is declared on multiple elements. To avoid this, use this method to force a namespace declaration
     * on the root element.
     *
     * <p>For more information refer to the discussion of namespace support in the class overview documentation.
     *
     * @param uri The namespace URI to declare on the root element.
     * @return This class instance
     * @see #addNSRootDecl(String, String)
     * @see #addNSPrefix(String, String)
     */
    public XmlWriter addNSRootDecl(final String uri) {
        this.nsRootDeclSet.add(uri);
        return this;
    }

    /**
     * Adds the specified prefix for the specified namespace URI and forces the namespace to be declared on the root
     * element. This method combines the operations performed by the {@link #addNSPrefix(String, String) addNSPrefix}
     * method and the {@link #addNSRootDecl(String) addNSRootDecl} method.
     *
     * <p>By default, the XmlWriter will declare namespaces on elements as needed. This may result in clutter as the same
     * namespace is declared on multiple elements. To avoid this, use this method to force a namespace declaration
     * on the root element.
     *
     * <p>For more information refer to the discussion of namespace support in the class overview documentation.
     *
     * @param prefix Prefix for the namespace URI. Use an empty string ("") to specify the default namespace.
     * @param uri The namespace URI to declare on the root element.
     * @return This class instance
     * @see #addNSRootDecl(String)
     * @see #addNSPrefix(String, String)
     */
    public XmlWriter addNSRootDecl(final String prefix, final String uri) {
        addNSPrefix(prefix, uri);
        addNSRootDecl(uri);
        return this;
    }

    /**
     * Enables or disables automatic output formatting. By default, the XmlWriter does not insert newlines or
     * indentations into the output. This is appropriate for pre-formatted XML output (e.g. a formatted XML file or
     * DOM). To format XML content that is not already formatted, enable pretty printing.
     *
     * @param enable {@code true} to enable automatic output formatting.
     */
    public void setPrettyPrint(final boolean enable) {
        this.prettyPrint = enable;
    }

    /**
     * Indicates whether automatic formatting is enabled.
     *
     * @return Whether automatic output formatting is enabled or disabled.
     */
    public boolean getPrettyPrint() {
        return this.prettyPrint;
    }

    /**
     * Escape characters above the ASCII range (i.e. ch &gt; 0x7F). By default, only ASCII control characters
     * and markup-significant ASCII characters are escaped. Specifying this option causes all ISO Latin-1,
     * Unicode BMP and surrogate pair characters to be escaped.
     *
     * @param enable {@code true} to escape characters outside the ASCII range using numerical entity references
     */
    public void setEscapeNonAscii(final boolean enable) {
        if (enable) {
            this.escapeOptions.add(XmlEscaper.Option.ESCAPE_NON_ASCII);
        } else {
            this.escapeOptions.remove(XmlEscaper.Option.ESCAPE_NON_ASCII);
        }
    }

    /**
     * Indicates whether characters above the ASCII range (i.e. ch &gt; 0x7F) are escaped.
     *
     * @return {@code true} if characters outside the ASCII range are being escaped.
     */
    public boolean getEscapeNonAscii() {
        return this.escapeOptions.contains(XmlEscaper.Option.ESCAPE_NON_ASCII);
    }

    /**
     * Use decimal for numerical character entities (i.e. &amp;#DDDD;). By default, this library uses hexadecimal
     * (i.e. &amp;#xHHH;) for numerical character entities.
     *
     * @param enable {@code true} to use decimal rather than hexadecimal for numerical character entities
     */
    public void setUseDecimal(final boolean enable) {
        if (enable) {
            this.escapeOptions.add(XmlEscaper.Option.USE_DECIMAL);
        } else {
            this.escapeOptions.remove(XmlEscaper.Option.USE_DECIMAL);
        }
    }

    /**
     * Indicates whether decimal is being used for numerical character entities rather than hexadecimal.
     *
     * @return {@code true} if decimal is being used for numerical character entities.
     */
    public boolean getUseDecimal() {
        return this.escapeOptions.contains(XmlEscaper.Option.USE_DECIMAL);
    }

    /**
     * Sets the whitespace string to use for indentation when automatic formatting is enabled using the
     * {@link #setPrettyPrint(boolean) setPrettyPrint} method. Note that calling this method does not enable
     * automatic formatting. The default indentation string is four spaces.
     *
     * @param indent Whitespace string to use for indentation
     * @see #setIndentString(String, String)
     * @see #setPrettyPrint(boolean)
     */
    public void setIndentString(@Nullable final String indent) {
        this.indentStr = (indent == null) ? "" : indent;
    }

    /**
     * Sets the whitespace string to use for indentation and constant offset when automatic formatting is enabled
     * using the {@link #setPrettyPrint(boolean) setPrettyPrint} method. Note that calling this method does not enable
     * automatic formatting. The default offset is the empty string and the default indentation string is four spaces.
     * The total indentation of a line is determined by:
     * <pre>
     * Total indentation = offset + indent * element_level
     * </pre>
     * Where the root element is at level zero.
     *
     * @param offset Whitespace string to use as offset
     * @param indent Whitespace string to use for indentation
     * @see #setIndentString(String)
     * @see #setPrettyPrint(boolean)
     */
    public void setIndentString(@Nullable final String offset, @Nullable final String indent) {
        this.offsetStr = (offset == null) ? DEF_OFFSET : offset;
        this.haveOffsetStr = !this.offsetStr.isEmpty();
        setIndentString(indent);
    }

    /**
     * Returns the string used for indenting.
     *
     * @return The string used for indenting when automatic formatting is enabled.
     */
    public String getIndentString() {
        return this.indentStr;
    }

    /**
     * Returns the string used for line offsetting.
     *
     * @return The string used to offset a line when automatic formatting is enabled.
     */
    public String getOffsetString() {
        return this.offsetStr;
    }

    /**
     * Indicates whether a start tag followed immediately by an end tag should be consolidated into a single empty
     * tag. That is, specifies whether &lt;foo&gt;&lt;/foo&gt; should be output as &lt;foo/&gt;. By default, tag
     * minimization is enabled.
     *
     * @param minimizeEmpty {@code true} indicates that empty start/end tags should be consolidated into a single
     *         empty tag.
     */
    public void setMinimizeEmpty(final boolean minimizeEmpty) {
        this.minimize = minimizeEmpty;
    }

    /**
     * Indicates whether empty tag minimization is enabled.
     *
     * @return Indicates whether a start tag followed immediately by an end tag is consolidated into a single empty tag.
     */
    public boolean getMinimizeEmpty() {
        return this.minimize;
    }

    /**
     * Attributes can be written all on one line or each on a separate line. By default, all attributes appear on the
     * same line as the start tag. The placement of attributes is independent of whether pretty printing is enabled.
     *
     * @param separateLine {@code true} if attributes should each be placed on a separate line.
     */
    public void setAttrPerLine(final boolean separateLine) {
        this.attrPerLine = separateLine;
    }

    /**
     * Indicates whether all attributes are written on one line.
     *
     * @return Indicates whether attributes are written all on one line or each on a separate line.
     */
    public boolean getAttrPerLine() {
        return this.attrPerLine;
    }

    /**
     * By default, the XmlWriter does not write attributes that are only present due to defaulting from a DTD or
     * schema. This method allows client applications to write these unspecified, defaulted attributes. Typically,
     * the setting of this feature is only relevant when the XmlWriter is used in a SAX filter chain. In that case
     * the SAX2 extensions must be supported by the XML reader in order to determine whether an attribute is not
     * explicitly specified. SAX2 extensions are supported if the {@code http://xml.org/sax/features/use-attributes2}
     * flag is {@code true}
     *
     * @param specified {@code true} to write unspecified attributes with default values.
     */
    public void setSpecifiedAttributes(final boolean specified) {
        this.specifiedAttr = specified;
    }

    /**
     * Indicates if only explicitly specified attributes are output.
     *
     * @return Indicates if only explicitly specified attributes are output.
     */
    public boolean getSpecifiedAttributes() {
        return this.specifiedAttr;
    }

    /**
     * Obtains the version of XML used by the document.
     *
     * @return Version of XML used by the document. The default is 1.0.
     */
    public String getXmlVersion() {
        return this.xmlVersion;
    }

    /**
     * Sets the version of XML used by the document.
     *
     * @param xmlVersion  Version of XML used by the document.
     */
    public void setXmlVersion(final String xmlVersion) {
        this.xmlVersion = xmlVersion;
    }

    /**
     * Specifies the value for the standalone attribute on the XML header when the XmlWriter is used in a SAX filter chain.
     *
     * @param sa {@code true} if the standalone attribute value should be set to &quot;yes&quot; and {@code false}
     *         if the attribute value should be set to &quot;no&quot;.
     * @see #startDocument(String, boolean, boolean)
     */
    public void setStandalone(final boolean sa) {
        this.standalone = sa;
    }

    /**
     * Indicates the state of the XML standalone attribute.
     *
     * @return Indicates the value for the standalone attribute on the XML header.
     */
    public boolean getStandalone() {
        return this.standalone;
    }

    /**
     * Starts an XML document by writing the XML header. This method writes a header without specifying an encoding
     * and with the standalone attribute defaulted to &quot;yes&quot;. Call the {@link #setStandalone(boolean)
     * setStandalone} method before parsing to change the value of the standalone attribute. This method or the
     * {@link #startDocument(String, boolean, boolean) startDocument(String, boolean, boolean)} method must be called
     * before any other XML writer event method is called. This form of the startDocument method is used when the
     * XmlWriter is in a SAX filter chain. The parameter form of the method is typically used when the XmlWriter is
     * being used standalone.
     *
     * @throws SAXException If there is a problem writing the XML header.
     * @see org.xml.sax.ContentHandler#startDocument()
=     * @see #startDocument(String, boolean, boolean)
     * @see #setStandalone(boolean)
     * @see #setXmlVersion(String)
     */
    @Override
    public void startDocument() throws SAXException {
        startDocument(null, this.standalone, false);
        super.startDocument();
    }

    /**
     * Starts an XML document by writing the XML header. This method or the {@link #startDocument() startDocument()}
     * method must be called before any other XML writer event method is called. This form of the startDocument
     * method is typically used when the XmlWriter is being used standalone.
     *
     * @param encoding The encoding for the document or {@code null} if the encoding should not be specified in the
     *         XML header.
     * @param sa Specify {@code true} if the document does not contain any external markup declarations.
     * @param isFragment Specify {@code true} if the document is only a fragment of a larger XML document in which
     *         case the XML header is not written.
     * @return This class instance
     * @throws SAXException If there is a problem writing the XML header.
     * @see #startDocument()
     * @see #setXmlVersion(String)
     */
    public XmlWriter startDocument(@Nullable final String encoding, final boolean sa, final boolean isFragment)
            throws SAXException {
        handleEvent(Event.START_DOCUMENT_EVENT);

        if (!isFragment) {
            writeRaw("<?xml version=");
            writeQuoted(this.xmlVersion);
            if (encoding != null) {
                writeRaw(" encoding=");
                writeQuoted(encoding);
            }
            writeRaw(" standalone=");
            writeQuoted(sa ? "yes" : "no");
            writeRaw("?>");
            writeNewline();
        }

        return this;
    }

    /**
     * Ends the XML output. This method must be called to properly terminate the XML document. Before the XmlWriter
     * can be reused, the {@link #reset() reset} method must be called. This method does not close the underlying
     * writer object.
     *
     * @throws SAXException If there is a problem ending the XML output.
     * @see org.xml.sax.ContentHandler#endDocument()
     * @see #reset()
     */
    @Override
    public void endDocument() throws SAXException {
        handleEvent(Event.END_DOCUMENT_EVENT);

        writeNewline();
        flush();

        super.endDocument();
    }

    /**
     * Writes a Document Type Declaration.
     *
     * @param name Root element name
     * @param publicId Public identifier or {@code null} if no public identifier is available.
     * @param systemId System identifier (must be specified).
     * @return This class instance
     * @throws SAXException If there is a problem writing the DOCTYPE.
     */
    public XmlWriter doctype(final String name, @Nullable final String publicId, final String systemId)
            throws SAXException {
        startDTD(name, publicId, systemId);
        endDTD();
        return this;
    }

    /**
     * Writes a Document Type Declaration.
     *
     * @param name Root element name
     * @param publicId Public identifier of {@code null} if no public identifier is available.
     * @param systemId System identified (must be specified).
     * @param entities Collection of Entity objects to declare in an internal subset. Specify {@code null} or a
     *         zero size collection if there are no entities to declare.
     * @param notations Collection of Notation objects to declare in an internal subset. Specify {@code null} or a
     *         zero size collection if there are no notations to declare.
     * @return This class instance
     * @throws SAXException If there is a problem writing the DOCTYPE.
     */
    public XmlWriter doctype(final String name, @Nullable final String publicId, final String systemId,
                             @Nullable final Collection<Entity> entities,
                             @Nullable final Collection<Notation> notations) throws SAXException {
        startDTD(name, publicId, systemId);
        if ((entities != null && !entities.isEmpty()) || (notations != null && !notations.isEmpty())) {
            writeRaw(" [");
            if (entities != null) {
                for (final Entity entity : entities) {
                    writeNewline();
                    writeRaw(this.offsetStr + this.indentStr);
                    writeEntityDecl(entity);
                }
            }
            if (notations != null) {
                for (final Notation notation : notations) {
                    writeNewline();
                    writeRaw(this.offsetStr + this.indentStr);
                    writeNotationDecl(notation);
                }
            }
            writeNewline();
            writeRaw(']');
        }
        endDTD();
        return this;
    }

    /**
     * Begins a DTD declaration.
     *
     * @param name Root element name
     * @param publicId Public identifier
     * @param systemId System identifier
     * @throws SAXException If there is a problem writing the DTD
     */
    @Override
    public void startDTD(final String name, @Nullable final String publicId,
                         final String systemId) throws SAXException {
        handleEvent(Event.START_DTD_EVENT);

        writeRaw("<!DOCTYPE ");
        writeRaw(name);

        if (publicId != null) {
            writeRaw(" PUBLIC \"" + publicId + '"');
        } else {
            writeRaw(" SYSTEM");
        }
        writeRaw(" \"" + systemId + '"');
    }

    /**
     * Ends a DTD declaration.
     *
     * @throws SAXException If there is an error while writing the DTD
     */
    @Override
    public void endDTD() throws SAXException {
        handleEvent(Event.END_DTD_EVENT);

        writeRaw('>');
        writeNewline();
        writeNewline();
    }

    /**
     * Begins an entity declaration.
     *
     * @param name Name of the entity
     */
    @Override
    public void startEntity(final String name) {
    }

    /**
     * Ends an entity declaration.
     *
     * @param name Name of the entity
     */
    @Override
    public void endEntity(final String name) {
    }

    /**
     * Start a new element. Attributes for the element can be specified using this method or the
     * {@link #setAttributes(Attributes) setAttributes} method. The set of attributes can be augmented using the
     * {@link #addAttributes(Attributes) addAttributes} method or one of the
     * {@link #addAttribute(String, String) addAttribute} methods. This form of the startElement method is called
     * when the XmlWriter is used in a SAX filter chain. Typically, standalone applications of the XmlWriter will
     * use one of the more compact forms of this method. Each call to startElement requires a corresponding call to
     * {@link #endElement() endElement}.
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @param qName The element's qualified (prefixed) name, or the empty string if none is available. This method
     *         will use the qName as a template for generating a prefix if necessary, but it is not guaranteed
     *         to use the same qName.
     * @param attrs An initial set of attributes for the element.
     * @throws SAXException If there is an error writing the start tag, or if a handler further down the filter chain
     *         raises an exception.
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes attrs)
            throws SAXException {
        final State previousState = handleEvent(Event.START_ELEMENT_EVENT);

        this.nsSupport.pushContext();

        this.elementStack.push(uri, localName, qName, attrs,
                               false, previousState);

        if (getElementLevel() == 1) {
            setNSRootDecls();
        }

        super.startElement(uri, localName, qName, attrs);
    }

    /**
     * Start a new element. Attributes for the element can be specified using the
     * {@link #setAttributes(Attributes) setAttributes} method, {@link #addAttributes(Attributes) addAttributes}
     * method, or one of the {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #startElement(String, String, String, Attributes) startElement} with an empty initial set of attributes.
     * Each call to startElement requires a corresponding call to {@link #endElement() endElement}.
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @param qName The element's qualified (prefixed) name, or the empty string if none is available. This method
     *         will use the qName as a template for generating a prefix if necessary, but it is not guaranteed to use
     *         the same qName.
     * @return This class instance
     * @throws SAXException If there is an error writing the start tag, or if a handler further down the filter chain
     *         raises an exception.
     * @see #startElement(String, String, String, Attributes)
     */
    public XmlWriter startElement(final String uri, final String localName, final String qName) throws SAXException {
        startElement(uri, localName, qName, EMPTY_ATTRS);
        return this;
    }

    /**
     * Start a new element. Attributes for the element can be specified using this method or the
     * {@link #setAttributes(Attributes) setAttributes} method. The set of attributes can be augmented using the
     * {@link #addAttributes(Attributes) addAttributes} method or one of the
     * {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #startElement(String, String, String, Attributes) startElement} with an empty string for the qualified
     * name. Each call to startElement requires a corresponding call to {@link #endElement() endElement}.
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @param attrs An initial set of attributes for the element.
     * @return This class instance
     * @throws SAXException If there is an error writing the start tag, or if a handler further down the filter chain
     *         raises an exception.
     * @see #startElement(String, String, String, Attributes)
     */
    public XmlWriter startElement(final String uri, final String localName,
                                  final Attributes attrs) throws SAXException {
        startElement(uri, localName, "", attrs);
        return this;
    }

    /**
     * Start a new element. Attributes for the element can be specified using the
     * {@link #setAttributes(Attributes) setAttributes} method, {@link #addAttributes(Attributes) addAttributes}
     * method, or one of the {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #startElement(String, String, String, Attributes) startElement} with an empty string for the qualified
     * name and an empty initial set of attributes. Each call to startElement requires a corresponding call to
     * {@link #endElement() endElement}.
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @return This class instance
     * @throws SAXException If there is an error writing the start tag, or if a handler further down the filter
     *         chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     */
    public XmlWriter startElement(final String uri, final String localName) throws SAXException {
        startElement(uri, localName, "", EMPTY_ATTRS);
        return this;
    }

    /**
     * Start a new element. Attributes for the element can be specified using this method or the
     * {@link #setAttributes(Attributes) setAttributes} method. The set of attributes can be augmented using the
     * {@link #addAttributes(Attributes) addAttributes} method or one of the
     * {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #startElement(String, String, String, Attributes) startElement} with an empty string for the namespace
     * URI and the qualified name. Each call to startElement requires a corresponding call to {@link #endElement() endElement}.
     *
     * @param localName The element's local name
     * @param attrs An initial set of attributes for the element.
     * @return This class instance
     * @throws SAXException If there is an error writing the start tag, or if a handler further down the filter
     *         chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     */
    public XmlWriter startElement(final String localName, final Attributes attrs) throws SAXException {
        startElement(XMLConstants.NULL_NS_URI, localName, "", attrs);
        return this;
    }

    /**
     * Start a new element. Attributes for the element can be specified using the
     * {@link #setAttributes(Attributes) setAttributes} method, {@link #addAttributes(Attributes) addAttributes}
     * method, or one of the {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #startElement(String, String, String, Attributes) startElement} with an empty string for the namespace
     * URI and the qualified name, and an empty initial set of attributes. Each call to startElement requires a
     * corresponding call to {@link #endElement() endElement}.
     *
     * @param localName The element's local name
     * @return This class instance
     * @throws SAXException If there is an error writing the start tag, or if a handler further down the filter
     *         chain raises an exception.
     * @see #startElement(String, String, String, Attributes)
     */
    public XmlWriter startElement(final String localName) throws SAXException {
        startElement(XMLConstants.NULL_NS_URI, localName, "", EMPTY_ATTRS);
        return this;
    }

    /**
     * This form of the endElement method is called when the XmlWriter is part of a SAX filter chain. Typically,
     * standalone applications of the XmlWriter will call the empty parameter version of the {@link #endElement()
     * endElement()} method. Each call to {@link #startElement(String) startElement} requires a corresponding call
     * to endElement. <strong>Since empty elements do not have closing tags, do not call this method to close an
     * {@link #emptyElement(String) emptyElement}.</strong>
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @param qName The element's qualified (prefixed) name, or the empty string if none is available. This
     *         method will use the qName as a template for generating a prefix if necessary, but it is not guaranteed
     *         to use the same qName.
     * @throws SAXException If there is an error writing the tag, or if
     *         a handler further down the filter chain raises an exception.
     * @see org.xml.sax.ContentHandler#endElement(String, String, String)
     */
    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        handleEvent(Event.END_ELEMENT_EVENT);
        super.endElement(uri, localName, qName);
    }

    /**
     * Writes an end tag for the element that is open at the current level. Typically, this form of the endElement
     * method is called when the XmlWriter is used standalone. Each call to {@link #startElement(String)
     * startElement} requires a corresponding call to endElement. <strong>Since empty elements do not have closing
     * tags, do not call this method to close an {@link #emptyElement(String) emptyElement}.</strong>
     *
     * @throws SAXException If there is an error writing the tag, or if a handler further down the filter chain
     *         raises an exception.
     */
    public void endElement() throws SAXException {
        handleEvent(Event.END_ELEMENT_EVENT);
    }

    /**
     * Create an empty element. Attributes for the element can be specified using this method or the
     * {@link #setAttributes(Attributes) setAttributes} method. The set of attributes can be augmented using the
     * {@link #addAttributes(Attributes) addAttributes} method or one of the
     * {@link #addAttribute(String, String) addAttribute} methods. <strong>Since empty elements do not have closing
     * tags, do not call {@link #endElement() endElement} to close this element.</strong>
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @param qName The element's qualified (prefixed) name, or the empty string if none is available. This method
     *         will use the qName as a template for generating a prefix if necessary, but it is not guaranteed to use
     *         the same qName.
     * @param attrs An initial set of attributes for the element.
     * @return This class instance
     * @throws SAXException If there is an error writing the tag, or if a handler further down the filter chain
     *         raises an exception.
     */
    public XmlWriter emptyElement(final String uri, final String localName, final String qName, final Attributes attrs)
            throws SAXException {
        final State previousState = handleEvent(Event.START_ELEMENT_EVENT);

        this.nsSupport.pushContext();

        this.elementStack.push(uri, localName, qName, attrs,
                               true, previousState);

        if (getElementLevel() == 1) {
            setNSRootDecls();
        }

        super.startElement(uri, localName, qName, attrs);
        return this;
    }

    /**
     * Create an empty element. Attributes for the element can be specified using the
     * {@link #setAttributes(Attributes) setAttributes} method, {@link #addAttributes(Attributes) addAttributes}
     * method, or one of the {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #emptyElement(String, String, String, Attributes) emptyElement} with an empty initial set of attributes.
     * <strong>Since empty elements do not have closing tags, do not call {@link #endElement() endElement} to close
     * this element.</strong>
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @param qName The element's qualified (prefixed) name, or the empty string if none is available. This method
     *         will use the qName as a template for generating a prefix if necessary, but it is not guaranteed to use
     *         the same qName.
     * @return This class instance
     * @throws SAXException If there is an error writing the start tag, or if a handler further down the filter chain
     *         raises an exception.
     * @see #emptyElement(String, String, String, Attributes)
     */
    public XmlWriter emptyElement(final String uri, final String localName, final String qName) throws SAXException {
        return emptyElement(uri, localName, qName, EMPTY_ATTRS);
    }

    /**
     * Create an empty element. Attributes for the element can be specified using this method or the
     * {@link #setAttributes(Attributes) setAttributes} method. The set of attributes can be augmented using the
     * {@link #addAttributes(Attributes) addAttributes} method or one of the
     * {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #emptyElement(String, String, String, Attributes) emptyElement} with an empty string for the qualified
     * name. <strong>Since empty elements do not have closing tags, do not call {@link #endElement() endElement} to
     * close this element.</strong>
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @param attrs An initial set of attributes for the element.
     * @return This class instance
     * @throws SAXException If there is an error writing the tag, or if a handler further down the filter chain raises
     *         an exception.
     * @see #emptyElement(String, String, String, Attributes)
     */
    public XmlWriter emptyElement(final String uri, final String localName,
                                  final Attributes attrs) throws SAXException {
        return emptyElement(uri, localName, "", attrs);
    }

    /**
     * Create an empty element. Attributes for the element can be specified using the
     * {@link #setAttributes(Attributes) setAttributes} method, {@link #addAttributes(Attributes) addAttributes}
     * method, or one of the {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #emptyElement(String, String, String, Attributes) emptyElement} with an empty string for the qualified
     * name and an empty initial set of attributes. <strong>Since empty elements do not have closing tags, do not call
     * {@link #endElement() endElement} to close this element.</strong>
     *
     * @param uri The element's namespace URI
     * @param localName The element's local name
     * @return This class instance
     * @throws SAXException If there is an error writing the tag, or ifa handler further down the filter chain raises
     *         an exception.
     * @see #emptyElement(String, String, String, Attributes)
     */
    public XmlWriter emptyElement(final String uri, final String localName) throws SAXException {
        return emptyElement(uri, localName, "", EMPTY_ATTRS);
    }

    /**
     * Create an empty element. Attributes for the element can be specified using this method or the
     * {@link #setAttributes(Attributes) setAttributes} method. The set of attributes can be augmented using the
     * {@link #addAttributes(Attributes) addAttributes} method or one of the
     * {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #emptyElement(String, String, String, Attributes) emptyElement} with an empty string for the namespace
     * URI and the qualified name. <strong>Since empty elements do not have closing tags, do not call
     * {@link #endElement() endElement} to close this element.</strong>
     *
     * @param localName The element's local name
     * @param attrs An initial set of attributes for the element.
     * @return This class instance
     * @throws SAXException If there is an error writing the tag, or if a handler further down the filter chain raises
     *         an exception.
     * @see #emptyElement(String, String, String, Attributes)
     */
    public XmlWriter emptyElement(final String localName, final Attributes attrs) throws SAXException {
        return emptyElement(XMLConstants.NULL_NS_URI, localName, "", attrs);
    }

    /**
     * Create an empty element. Attributes for the element can be specified using this method or the
     * {@link #setAttributes(Attributes) setAttributes} method. The set of attributes can be augmented using the
     * {@link #addAttributes(Attributes) addAttributes} method or one of the
     * {@link #addAttribute(String, String) addAttribute} methods. This method invokes
     * {@link #emptyElement(String, String, String, Attributes) emptyElement} with an empty string for the namespace
     * URI and the qualified name, and an empty initial set of attributes. <strong>Since empty elements do not have
     * closing tags, do not call {@link #endElement() endElement} to close this element.</strong>
     *
     * @param localName The element's local name
     * @return This class instance
     * @throws SAXException If there is an error writing the tag, or if a handler further down the filter chain
     *         raises an exception.
     * @see #emptyElement(String, String, String, Attributes)
     */
    public XmlWriter emptyElement(final String localName) throws SAXException {
        return emptyElement(XMLConstants.NULL_NS_URI, localName, "", EMPTY_ATTRS);
    }

    /**
     * Replaces the attributes already set on the current start tag with the specified attributes.
     *
     * @param attrs Attributes to set on the current start tag.
     * @return This class instance
     * @throws SAXException If there is an error writing the attributes or if a handler further down the filter chain
     *         raises an exception.
     * @see #addAttributes(Attributes)
     */
    public XmlWriter setAttributes(final Attributes attrs) throws SAXException {
        handleEvent(Event.ATTRIBUTE_EVENT);

        topElement().attrs.setAttributes(attrs);
        return this;
    }

    /**
     * Adds the specified set of attributes to those already set on the current start tag.
     *
     * @param attrs Attributes to add to the current start tag.
     * @return This class instance
     * @throws SAXException If there is an error writing the attributes or if a handler further down the filter chain
     *         raises an exception.
     * @see #setAttributes(Attributes)
     */
    public XmlWriter addAttributes(final Attributes attrs) throws SAXException {
        handleEvent(Event.ATTRIBUTE_EVENT);

        final Element element = topElement();
        final int len = attrs.getLength();

        for (int i = 0; i < len; i++) {
            element.attrs.addAttribute(attrs.getURI(i), attrs.getLocalName(i),
                                       attrs.getQName(i), attrs.getType(i),
                                       attrs.getValue(i));
        }
        return this;
    }

    /**
     * Adds an attribute to the current start tag.
     *
     * @param uri The attribute's namespace URI
     * @param localName The attribute's local name
     * @param qName The attribute's qualified (prefixed) name, or the empty string if none is available. This method
     *         will use the qName as a template for generating a prefix if necessary, but it is not guaranteed to use the
     *         same qName.
     * @param type The attribute's data type. Can be one of:
     *         <ul>
     *             <li>{@code CDATA}</li>
     *             <li>{@code ID}</li>
     *             <li>{@code IDREF}</li>
     *             <li>{@code IDREFS}</li>
     *             <li>{@code NMTOKEN}</li>
     *             <li>{@code NMTOKENS}</li>
     *             <li>{@code ENTITY}</li>
     *             <li>{@code ENTITIES}</li>
     *             <li>{@code NOTATION}</li>
     *         </ul>
     *         Specify {@code CDATA} if the type is not known (per
     *         <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize">section 3.3.3</a> of the XML 1.0
     *         specification)
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter chain
     *         raises an exception.
     */
    public XmlWriter addAttribute(final String uri, final String localName, final String qName, final String type,
                                  final String value) throws SAXException {
        handleEvent(Event.ATTRIBUTE_EVENT);

        topElement().attrs.addAttribute(uri, localName, qName, type, value);
        return this;
    }

    /**
     * Adds an attribute to the current start tag.
     *
     * @param uri The attribute's namespace URI
     * @param localName The attribute's local name
     * @param qName The attribute's qualified (prefixed) name, or the empty string if none is available. This method
     *         will use the qName as a template for generating a prefix if necessary, but it is not guaranteed to use the
     *         same qName.
     * @param type The attribute's data type. Can be one of:
     *         <ul>
     *             <li>{@code CDATA}</li>
     *             <li>{@code ID}</li>
     *             <li>{@code IDREF}</li>
     *             <li>{@code IDREFS}</li>
     *             <li>{@code NMTOKEN}</li>
     *             <li>{@code NMTOKENS}</li>
     *             <li>{@code ENTITY}</li>
     *             <li>{@code ENTITIES}</li>
     *             <li>{@code NOTATION}</li>
     *         </ul>
     *         Specify {@code CDATA} if the type is not known (per
     *         <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize"> section 3.3.3</a> of the XML 1.0
     *         specification)
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter chain
     *         raises an exception.
     */
    public XmlWriter addAttribute(final String uri, final String localName, final String qName, final String type,
                                  final Object value) throws SAXException {
        return addAttribute(uri, localName, qName, type, value.toString());
    }

    /**
     * Adds an attribute to the current start tag. This method invokes the
     * {@link #addAttribute(String, String, String, String, String) addAttribute} method with a CDATA type (per
     * <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize">section 3.3.3</a> of the XML 1.0 specification).
     *
     * @param uri The attribute's namespace URI
     * @param localName The attribute's local name
     * @param qName The attribute's qualified (prefixed) name, or the empty string if none is available. This
     *         method will use the qName as a template for generating a prefix if necessary, but it is not guaranteed
     *         to use the same qName.
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter
     *         chain raises an exception.
     * @see #addAttribute(String, String, String, String, String)
     */
    public XmlWriter addAttribute(final String uri, final String localName, final String qName, final String value)
            throws SAXException {
        return addAttribute(uri, localName, qName, CDATA, value);
    }

    /**
     * Adds an attribute to the current start tag. This method invokes the
     * {@link #addAttribute(String, String, String, String, String) addAttribute} method with a CDATA type (per
     * <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize">section 3.3.3</a> of the XML 1.0 specification).
     *
     * @param uri The attribute's namespace URI
     * @param localName The attribute's local name
     * @param qName The attribute's qualified (prefixed) name, or the empty string if none is available. This method
     *         will use the qName as a template for generating a prefix if necessary, but it is not guaranteed to use the
     *         same qName.
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter chain
     *         raises an exception.
     * @see #addAttribute(String, String, String, String, String)
     */
    public XmlWriter addAttribute(final String uri, final String localName, final String qName, final Object value)
            throws SAXException {
        return addAttribute(uri, localName, qName, CDATA, value.toString());
    }

    /**
     * Adds an attribute to the current start tag. This method invokes the
     * {@link #addAttribute(String, String, String, String, String) addAttribute} method with an empty qualified name,
     * and a CDATA type (per <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize">section 3.3.3</a> of
     * the XML 1.0 specification).
     *
     * @param uri The attribute's namespace URI
     * @param localName The attribute's local name
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter chain
     *         raises an exception.
     * @see #addAttribute(String, String, String, String, String)
     */
    public XmlWriter addAttribute(final String uri, final String localName, final String value) throws SAXException {
        return addAttribute(uri, localName, "", CDATA, value);
    }

    /**
     * Adds an attribute to the current start tag. This method invokes the
     * {@link #addAttribute(String, String, String, String, String) addAttribute} method with an empty qualified name,
     * and a CDATA type (per <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize">section 3.3.3</a> of
     * the XML 1.0 specification).
     *
     * @param uri The attribute's namespace URI
     * @param localName The attribute's local name
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter chain
     *         raises an exception.
     * @see #addAttribute(String, String, String, String, String)
     */
    public XmlWriter addAttribute(final String uri, final String localName, final Object value) throws SAXException {
        return addAttribute(uri, localName, "", CDATA, value.toString());
    }

    /**
     * Adds an attribute to the current start tag. This method invokes the
     * {@link #addAttribute(String, String, String, String, String) addAttribute}
     * method with an empty namespace URI, an empty qualified name, and a CDATA type (per
     * <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize">section 3.3.3</a> of the XML 1.0 specification).
     *
     * @param localName The attribute's local name
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter
     *         chain raises an exception.
     * @see #addAttribute(String, String, String, String, String)
     */
    public XmlWriter addAttribute(final String localName, final String value) throws SAXException {
        return addAttribute(XMLConstants.NULL_NS_URI, localName, "", CDATA, value);
    }

    /**
     * Adds an attribute to the current start tag. This method invokes the
     * {@link #addAttribute(String, String, String, String, String) addAttribute} method with an empty namespace URI,
     * an empty qualified name, and a CDATA type (per
     * <a href="http://www.w3.org/TR/2004/REC-xml-20040204/#AVNormalize">section 3.3.3</a> of the XML 1.0 specification).
     *
     * @param localName The attribute's local name
     * @param value The attribute's value
     * @return This class instance
     * @throws SAXException If there is an error writing the attribute, or if a handler further down the filter chain
     *         raises an exception.
     * @see #addAttribute(String, String, String, String, String)
     */
    public XmlWriter addAttribute(final String localName, final Object value) throws SAXException {
        return addAttribute(XMLConstants.NULL_NS_URI, localName, "", CDATA, value.toString());
    }

    /**
     * Writes the specified character array as escaped XML data.
     *
     * @param carr Character array to write
     * @param start Starting index in the array
     * @param length Number of characters to write
     * @throws SAXException If there is an error writing the characters.
     */
    @Override
    public void characters(final char[] carr, final int start, final int length) throws SAXException {
        handleEvent(Event.CHARACTERS_EVENT);

        if (this.currentState == State.IN_CDATA_STATE) {
            writeRaw(carr, start, length);
        } else {
            writeEscaped(carr, start, length);
        }

        super.characters(carr, start, length);
    }

    /**
     * Writes the specified string as escaped XML data. Invokes the {@link #characters(char[], int, int) characters}
     * method.
     *
     * @param data String to write. If {@code null} is specified, nothing is written.
     * @return This class instance
     * @throws SAXException If there is a problem writing the data.
     * @see #characters(char[], int, int)
     */
    public XmlWriter characters(@Nullable final String data) throws SAXException {
        if (data != null) {
            characters(data.toCharArray(), 0, data.length());
        }
        return this;
    }

    /**
     * Writes the specified string as unescaped XML data.
     *
     * @param data String to write
     * @return This class instance
     * @throws SAXException If there is a problem writing the data.
     */
    public XmlWriter data(final String data) throws SAXException {
        handleEvent(Event.CHARACTERS_EVENT);

        writeRaw(data);
        return this;
    }

    /**
     * Begins a CDATA section.
     *
     * @throws SAXException If there is a problem starting the section
     */
    @Override
    public void startCDATA() throws SAXException {
        handleEvent(Event.START_CDATA_EVENT);

        writeRaw("<![CDATA[");
    }

    /**
     * Ends a CDATA section.
     *
     * @throws SAXException If there is a problem ending the section
     */
    @Override
    public void endCDATA() throws SAXException {
        handleEvent(Event.END_CDATA_EVENT);

        writeRaw("]]>");
    }

    /**
     * Writes a CDATA section that consists of the CDATA start tag, the specified data, and the CDATA end tag.
     *
     * @param data Data for the CDATA section
     * @return This class instance
     * @throws SAXException If there is a problem writing the CDATA section.
     */
    public XmlWriter cdataSection(final String data) throws SAXException {
        startCDATA();
        characters(data);
        endCDATA();
        return this;
    }

    /**
     * Writes the specified whitespace. This method is called when the XmlWriter is used as part of a validating
     * SAX filter chain and is not typically used in standalone applications of the class.
     *
     * @param carr Array of whitespace characters
     * @param start Starting index into the character array
     * @param length Number of characters to write from the array
     * @throws SAXException If there is a problem writing the whitespace.
     */
    @Override
    public void ignorableWhitespace(final char[] carr, final int start, final int length) throws SAXException {
        handleEvent(Event.CHARACTERS_EVENT);

        writeEscaped(carr, start, length);

        super.ignorableWhitespace(carr, start, length);
    }

    /**
     * Writes the specified character array as an XML comment.
     *
     * @param carr Comment as a character array
     * @param start Starting index into the array
     * @param length Number of character to write from the array
     * @throws SAXException If there is a problem writing the comment
     */
    @Override
    public void comment(final char[] carr, final int start, final int length) throws SAXException {
        handleEvent(Event.COMMENT_EVENT);

        if (this.currentState != State.IN_DTD_STATE) {
            writeRaw("<!--");
            writeRaw(carr, start, length);
            writeRaw("-->");
        }
    }

    /**
     * Writes the specified string as an XML comment. Comments are always written in-line regardless of pretty
     * printing. To place a comment on its own line, use the {@link #newline() newline} method.
     *
     * @param info Comment to write
     * @return This class instance
     * @throws SAXException If there is a problem writing the comment.
     */
    public XmlWriter comment(final String info) throws SAXException {
        comment(info.toCharArray(), 0, info.length());
        return this;
    }

    /**
     * Writes a processing instruction. Processing instructions (PI) are always written in-line regardless of
     * pretty printing. To place a PI on its own line, use the {@link #newline() newline} method.
     *
     * @param target Target command for the instruction
     * @param data Data for the command
     * @throws SAXException If there is a problem writing the processing instruction.
     * @see org.xml.sax.ContentHandler#processingInstruction(String, String)
     */
    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        handleEvent(Event.PI_EVENT);

        writeRaw("<?");
        writeRaw(target);
        writeRaw(' ');
        writeRaw(data);
        writeRaw("?>");

        super.processingInstruction(target, data);
    }

    /**
     * Writes the specified entity as an entity reference. For example, the entity name "amp" is written as the
     * entity reference &amp;amp;. When {@link #setPrettyPrint(boolean) pretty printing} is enabled, this method
     * attempts to write the entity references inline with element and character data. To write an entity reference
     * using block formatting, use the {@link #entityRef(String, FormattingHint) entityRef} method. Note that the
     * actual formatting of entity references depends on the context in which the reference is written.
     *
     * @param entityName Name of the entity to write as an entity reference
     * @return This class instance
     * @throws SAXException If there is a problem writing the entity reference.
     */
    public XmlWriter entityRef(final String entityName) throws SAXException {
        return entityRef(entityName, FormattingHint.INLINE);
    }

    /**
     * Writes the specified entity as an entity reference. For example, the entity name "amp" is written as the
     * entity reference &amp;amp;. When {@link #setPrettyPrint(boolean) pretty printing} is enabled, this method
     * attempts to write the entity references either inline with element and character data, or as a standalone
     * block. Note that the actual formatting of entity references depends on the context in which the reference
     * is written.
     *
     * @param entityName Name of the entity to write as an entity reference
     * @param hint Hint to indicate if the entity reference should be written inline with element and character
     *         data, or as a standalone block. The hint is ignored when not in pretty printing mode.
     * @return This class instance
     * @throws SAXException If there is a problem writing the entity reference.
     */
    public XmlWriter entityRef(final String entityName, final FormattingHint hint) throws SAXException {
        handleEvent((hint == FormattingHint.INLINE) ? Event.INLINE_REF_EVENT : Event.BLOCK_REF_EVENT);

        if (this.prettyPrint && hint == FormattingHint.BLOCK) {
            writeNewline();
            writeIndent(1);
        }

        writeRaw('&');
        writeRaw(entityName);
        writeRaw(';');

        if (this.prettyPrint && hint == FormattingHint.BLOCK) {
            writeNewline();
            writeIndent(1);
        }

        return this;
    }

    /**
     * Write the specified character as a character reference. For example, the character 'a' is written as the
     * character reference &amp;#97;.
     *
     * @param ch Character to write as a character reference
     * @return This class instance
     * @throws SAXException If there is a problem writing the character reference.
     */
    public XmlWriter characterRef(final char ch) throws SAXException {
        handleEvent(Event.INLINE_REF_EVENT);

        writeRaw("&#");
        writeRaw(Integer.toString(ch));
        writeRaw(';');

        return this;
    }

    /**
     * Writes a newline. If pretty printing is enabled and a CDATA section is not open, the new line begins at the
     * current indentation.
     *
     * @return This class instance
     * @throws SAXException If there is a problem writing the newline.
     */
    public XmlWriter newline() throws SAXException {
        handleEvent(Event.NEWLINE_EVENT);

        writeNewline();
        if (this.prettyPrint && this.currentState != State.IN_CDATA_STATE) {
            writeIndent(1);
        }

        return this;
    }

    @Override
    public void error(final SAXParseException e) throws SAXException {
        if (getErrorHandler() == null) {
            throw new SAXException(e);
        }
        super.error(e);
    }

    @Override
    public void fatalError(final SAXParseException e) throws SAXException {
        if (getErrorHandler() == null) {
            throw new SAXException(e);
        }
        super.fatalError(e);
    }

    @Override
    public void warning(final SAXParseException e) throws SAXException {
        if (getErrorHandler() == null) {
            throw new SAXException(e);
        }
        super.warning(e);
    }


    /**
     * Internal representation of an element.
     *
     * @param uri Namespace URI for the element
     * @param localName Local name for the element
     * @param qName Qualified name for the element
     * @param attrs Element attributes
     * @param isEmpty Indicates if the element can contain content
     * @param containingState Writer state in which this element is being written
     */
    private record Element(String uri, String localName, String qName, Attributes2Impl attrs, boolean isEmpty,
                           State containingState) {
    }


    /**
     * A stack for elements. An unsynchronized collection is used as the basis to improve performance.
     */
    private static final class ElementStack {

        /**
         * Initial capacity of the stack.
         */
        private static final int INIT_CAP = 20;

        private final Deque<Element> elements = new ArrayDeque<>(INIT_CAP);

        private ElementStack() {
        }

        /**
         * Returns the top element off of this stack without removing it.
         *
         * @return The top element on the stack
         */
        public Element peek() {
            return this.elements.getLast();
        }

        /**
         * Pops the top element off of this stack.
         */
        public void pop() {
            this.elements.removeLast();
        }

        /**
         * Pushes a new element onto the top of this stack. The pushed element is also returned. This is equivalent
         * to calling {@code add}.
         *
         * @param namespaceUri Namespace URI or empty string
         * @param name Local name for the element
         * @param qualifiedName Qualified name for the element or empty string
         * @param attributes Initial set of attributes. Can be an empty set
         * @param empty Indicates if the element was created as an empty element
         * @param state State in which element is started
         */
        public void push(final String namespaceUri, final String name, final String qualifiedName,
                         final Attributes attributes, final boolean empty, final State state) {
            final Attributes2Impl attrs = new Attributes2Impl(attributes);
            final Element element = new Element(namespaceUri, name, qualifiedName, attrs, empty, state);
            this.elements.add(element);
        }

        /**
         * Provides the current depth of the stack.
         *
         * @return Depth of the stack.
         */
        public int depth() {
            return this.elements.size();
        }

        /**
         * Removes all elements from the stack.
         */
        public void clear() {
            this.elements.clear();
        }
    }


    /**
     * Heart of the XmlWriter state machine. Based on the current state and the specified event, an action is fired,
     * if any, and the next state is set. If the event is illegal for the current state, a SAXException is thrown.
     *
     * @param event The event to handle
     * @return Previous state
     * @throws SAXException If the event is illegal given the current state.
     */
    private State handleEvent(final Event event) throws SAXException {
        final State previousState = this.currentState;
        boolean invalidEvent = false;

        switch (this.currentState) {
            case BEFORE_DOC_STATE:
                if (event == Event.START_DOCUMENT_EVENT) {
                    this.currentState = State.BEFORE_ROOT_STATE;
                } else {
                    invalidEvent = true;
                }
                break;
            case BEFORE_ROOT_STATE:
                switch (event) {
                    case INLINE_REF_EVENT, BLOCK_REF_EVENT, CHARACTERS_EVENT, COMMENT_EVENT, NEWLINE_EVENT, PI_EVENT:
                        break;
                    case START_ELEMENT_EVENT:
                        this.currentState = State.IN_START_TAG_STATE;
                        break;
                    case START_DTD_EVENT:
                        this.currentState = State.IN_DTD_STATE;
                        break;
                    case END_DOCUMENT_EVENT:
                        this.currentState = State.AFTER_DOC_STATE;
                        break;
                    default:
                        invalidEvent = true;
                        break;
                }
                break;
            case IN_START_TAG_STATE:
                switch (event) {
                    case ATTRIBUTE_EVENT:
                        break;
                    case INLINE_REF_EVENT, CHARACTERS_EVENT:
                        writeStartElement(false);
                        this.currentState = State.AFTER_DATA_STATE;
                        break;
                    case NEWLINE_EVENT, PI_EVENT, BLOCK_REF_EVENT, COMMENT_EVENT:
                        writeStartElement(false);
                        this.currentState = State.AFTER_TAG_STATE;
                        break;
                    case START_ELEMENT_EVENT:
                        writeStartElement(false);
                        this.currentState = State.IN_START_TAG_STATE;
                        break;
                    case END_ELEMENT_EVENT: {
                        final Element element = topElement();
                        writeStartElement(this.minimize);
                        if (element.isEmpty || !this.minimize) {
                            writeEndElement();
                        }
                        this.currentState = (getElementLevel() == 0) ? State.AFTER_ROOT_STATE : State.AFTER_TAG_STATE;
                        break;
                    }
                    case START_CDATA_EVENT:
                        writeStartElement(false);
                        this.currentState = State.IN_CDATA_STATE;
                        break;
                    case END_DOCUMENT_EVENT:
                        writeStartElement(this.minimize);
                        if (!this.minimize) {
                            writeEndElement();
                        }
                        this.currentState = State.AFTER_DOC_STATE;
                        break;
                    default:
                        invalidEvent = true;
                        break;
                }
                break;
            case IN_CDATA_STATE:
                switch (event) {
                    case INLINE_REF_EVENT, BLOCK_REF_EVENT, CHARACTERS_EVENT, COMMENT_EVENT, NEWLINE_EVENT:
                        break;
                    case END_CDATA_EVENT:
                        this.currentState = State.AFTER_DATA_STATE;
                        break;
                    default:
                        invalidEvent = true;
                        break;
                }
                break;
            case IN_DTD_STATE:
                switch (event) {
                    case CHARACTERS_EVENT, COMMENT_EVENT, NEWLINE_EVENT:
                        break;
                    case END_DTD_EVENT:
                        this.currentState = State.BEFORE_ROOT_STATE;
                        break;
                    default:
                        invalidEvent = true;
                        break;
                }
                break;
            case AFTER_TAG_STATE:
                switch (event) {
                    case INLINE_REF_EVENT, CHARACTERS_EVENT:
                        this.currentState = State.AFTER_DATA_STATE;
                        break;
                    case BLOCK_REF_EVENT, COMMENT_EVENT, NEWLINE_EVENT, PI_EVENT:
                        break;
                    case START_CDATA_EVENT:
                        this.currentState = State.IN_CDATA_STATE;
                        break;
                    case START_ELEMENT_EVENT:
                        this.currentState = State.IN_START_TAG_STATE;
                        break;
                    case END_ELEMENT_EVENT:
                        writeEndElement();
                        this.currentState = (getElementLevel() == 0) ? State.AFTER_ROOT_STATE : State.AFTER_TAG_STATE;
                        break;
                    default:
                        invalidEvent = true;
                        break;
                }
                break;
            case AFTER_DATA_STATE:
                switch (event) {
                    case INLINE_REF_EVENT, BLOCK_REF_EVENT, CHARACTERS_EVENT, COMMENT_EVENT, NEWLINE_EVENT, PI_EVENT:
                        break;
                    case START_CDATA_EVENT:
                        this.currentState = State.IN_CDATA_STATE;
                        break;
                    case START_ELEMENT_EVENT:
                        this.currentState = State.IN_START_TAG_STATE;
                        break;
                    case END_ELEMENT_EVENT:
                        writeEndElement();
                        this.currentState = (getElementLevel() == 0) ? State.AFTER_ROOT_STATE : State.AFTER_TAG_STATE;
                        break;
                    default:
                        invalidEvent = true;
                        break;
                }
                break;
            case AFTER_ROOT_STATE:
                switch (event) {
                    case INLINE_REF_EVENT, BLOCK_REF_EVENT, CHARACTERS_EVENT, COMMENT_EVENT, NEWLINE_EVENT, PI_EVENT:
                        break;
                    case END_DOCUMENT_EVENT:
                        this.currentState = State.AFTER_DOC_STATE;
                        break;
                    default:
                        invalidEvent = true;
                        break;
                }
                break;
            case AFTER_DOC_STATE:
                invalidEvent = true;
                break;
            default:
                throw new SAXException("Unrecognized state: " + this.currentState);
        }

        if (invalidEvent) {
            throw new SAXException("Event " + event + " not allowed in state " + this.currentState);
        }

        return previousState;
    }

    /**
     * Returns the top element on the stack.
     *
     * @return Top element on the element stack.
     */
    private Element topElement() {
        return this.elementStack.peek();
    }

    /**
     * Returns the element nesting depth.
     *
     * @return The depth of element nesting. Zero is the level outside the root element.
     */
    private int getElementLevel() {
        return this.elementStack.depth();
    }

    /**
     * Determines a namespace prefix for the specified namespace URI.
     *
     * @param uri Namespace URI for which a prefix is to be determined
     * @param qName A qualified name to be used as a template to help determine a namespace prefix. Specifying a
     *         qualified name does not guarantee that its prefix will be used. Specify {@code null} or the empty string
     *         if a qualified name is not available.
     * @param isElement Specify {@code true} if the prefix is for use on an element. Specify {@code false} if the
     *         prefix is for use on an attribute.
     * @return The prefix for the specified namespace. The method will never return null.
     */
    private String findNSPrefix(final String uri, @Nullable final String qName, final boolean isElement) {
        final String defaultNS = this.nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX);
        final boolean haveDefaultNS = (defaultNS != null);
        final boolean isAttribute = !isElement;

        /*
         * If no namespace URI has been specified assume there is no prefix.
         */
        if (XMLConstants.NULL_NS_URI.equals(uri)) {
            return XMLConstants.DEFAULT_NS_PREFIX;
        }

        /*
         * If the namespace is for an element and the specified URI is
         * the default namespace URI, return the default prefix (i.e. "").
         * Otherwise, try to get the prefix corresponding to the specified URI.
         */
        if (isElement && haveDefaultNS && uri.equals(defaultNS)) {
            return XMLConstants.DEFAULT_NS_PREFIX;
        }

        String prefix = this.nsSupport.getPrefix(uri);

        if (prefix != null) {
            return prefix;
        }

        /*
         * If we get this far, try to obtain the prefix from the table of
         * previously declared namespaces. If a prefix is obtained from the
         * declaration map, it must be checked to see if it can be used. If
         * the prefix is the empty string, it cannot be used for an attribute
         * or if a default namespace is in effect for the context. Further, the
         * prefix cannot be used if it is already in use by another namespace URI.
         */
        prefix = this.nsDeclMap.get(uri);
        if (prefix != null && (((isAttribute || haveDefaultNS) && XMLConstants.DEFAULT_NS_PREFIX.equals(prefix))
                || nsPrefixInUse(prefix))) {
            prefix = null;
        }

        /*
         * If we did not obtain a prefix from the previously declared namespaces,
         * try to get one from the prefix mappings defined by the user through
         * calls to addNSPrefix. As before, if the prefix is the empty string, it
         * cannot be used for an attribute or if a default namespace is in effect
         * for the context. Further, the prefix cannot be used if it is already
         * in use by another namespace URI.
         */
        if (prefix == null) {
            prefix = this.nsPrefixMap.get(uri);
            if (prefix != null && (((isAttribute || haveDefaultNS) && XMLConstants.DEFAULT_NS_PREFIX.equals(prefix))
                    || nsPrefixInUse(prefix))) {
                prefix = null;
            }
        }

        /*
         * If we still don't have a prefix try to get one off the qualified
         * name, if one has been specified.
         */
        if (prefix == null && qName != null && !qName.isEmpty()) {
            final int i = qName.indexOf(':');
            if (i == -1) {
                if (isElement && !haveDefaultNS) {
                    prefix = XMLConstants.DEFAULT_NS_PREFIX;
                }
            } else {
                prefix = qName.substring(0, i);
            }
        }

        /*
         * As a last resort, synthesize a namespace prefix that is guaranteed
         * not to be in use.
         */
        if (prefix == null) {
            prefix = createNSPrefix();
        }

        /*
         * Before returning the prefix to the caller, register it with the
         * namespace context and with our map of declared namespaces.
         */
        this.nsSupport.declarePrefix(prefix, uri);
        this.nsDeclMap.put(uri, prefix);

        return prefix;
    }

    /**
     * Indicates whether the specified namespace is in use.
     *
     * @param prefix Namespace prefix to test
     * @return {@code true} if the namespace prefix is not in use in the current namespace context.
     */
    private boolean nsPrefixInUse(final String prefix) {
        return this.nsSupport.getURI(prefix) != null;
    }

    /**
     * Creates a namespace prefix.
     *
     * @return A synthesized namespace prefix and ensures that it is not already in use in the current namespace context.
     */
    private String createNSPrefix() {
        String prefix;

        do {
            prefix = SYNTH_NS_PREFIX + (++this.nsPrefixCounter);
        } while (nsPrefixInUse(prefix));

        return prefix;
    }

    /**
     * Force all namespaces that were specified in calls to the {@link #addNSRootDecl(String) addNSRootDecl} methods
     * to be declared. This method is called when the root element is started to ensure that the pre-declared
     * namespaces all appear on that element.
     */
    private void setNSRootDecls() {
        for (final String uri : this.nsRootDeclSet) {
            findNSPrefix(uri, null, true);
        }
    }

    /**
     * Write a start tag and handle the case where the element is empty.
     *
     * @param isEmpty {@code true} to write start element as if it were empty
     * @throws SAXException If there is a problem writing the tag.
     */
    private void writeStartElement(final boolean isEmpty) throws SAXException {
        final Element element = topElement();

        if (this.prettyPrint && (element.containingState != State.AFTER_DATA_STATE) && (getElementLevel() > 1)) {
            writeNewline();
            writeIndent();
        }

        writeRaw('<');
        writeName(element.uri, element.localName, element.qName, true);
        writeAttributes(element.attrs);
        final int numDecls = writeNSDecls();
        if (this.attrPerLine && ((element.attrs.getLength() + numDecls) > 0)) {
            writeNewline();
            writeIndent();
        }
        writeRaw((element.isEmpty || isEmpty) ? "/>" : ">");

        /*
         * If this is an empty tag, act like an end tag has been specified.
         */
        if (element.isEmpty || isEmpty) {
            closeElement(element);
        }
    }

    /**
     * Writes an end tag.
     *
     * @throws SAXException If there is a problem writing the tag.
     */
    private void writeEndElement() throws SAXException {
        final Element element = topElement();

        if (this.prettyPrint && this.currentState != State.AFTER_DATA_STATE) {
            writeNewline();
            writeIndent();
        }
        writeRaw("</");
        writeName(element.uri, element.localName, element.qName, true);
        writeRaw('>');

        closeElement(element);
    }

    /**
     * Performs cleanup when an element ends either due to an explicit call to endElement or because the
     * element is empty.
     *
     * @param element Element to close
     * @throws SAXException If there is a problem closing the element.
     */
    private void closeElement(final Element element) throws SAXException {
        this.elementStack.pop();
        this.nsSupport.popContext();

        super.endElement(element.uri, element.localName, element.qName);
    }

    /**
     * Write out an attribute list, quoting and escaping values. The names will have namespace prefixes added
     * to them as appropriate. The attributes will be written all on one line or on separate lines depending on
     * the attrPerLine flag.
     *
     * @param attrs The attribute list to write.
     * @throws SAXException If there is an error writing the attribute list, this method will throw an
     *         IOException wrapped in a SAXException.
     */
    private void writeAttributes(final Attributes2 attrs) throws SAXException {
        final int len = attrs.getLength();

        for (int i = 0; i < len; i++) {
            if (!this.specifiedAttr || attrs.isSpecified(i)) {
                if (this.attrPerLine) {
                    writeNewline();
                    writeIndent();
                    writeRaw(this.indentStr);
                } else {
                    writeRaw(' ');
                }
                writeName(attrs.getURI(i), attrs.getLocalName(i),
                          attrs.getQName(i), false);
                writeRaw('=');
                final String attrValue = attrs.getValue(i);
                writeQuoted((attrValue == null) ? EMPTY_STR : attrValue);
            }
        }
    }

    /**
     * Writes an entity declaration in the DTD internal subset.
     *
     * @param decl Entity to declare
     * @throws SAXException If there is a problem writing the declaration.
     */
    private void writeEntityDecl(final Entity decl) throws SAXException {
        writeRaw("<!ENTITY ");
        writeRaw(decl.name);

        if (decl.value != null) {
            writeRaw(" \"");
            writeRaw(decl.value);
            writeRaw('"');
        } else {
            if (decl.publicId != null) {
                writeRaw(" PUBLIC \"");
                writeRaw(decl.publicId);
                writeRaw("\" \"");
                writeRaw(decl.systemId == null ? "" : decl.systemId);
                writeRaw('"');
            } else {
                writeRaw(" SYSTEM \"");
                writeRaw(decl.systemId == null ? "" : decl.systemId);
                writeRaw('"');
            }

            if (decl.notationName != null) {
                writeRaw(" NDATA ");
                writeRaw(decl.notationName);
            }
        }
        writeRaw('>');
    }

    /**
     * Write a notation declaration in the DTD internal subset.
     *
     * @param decl Notation to declare
     * @throws SAXException If there is a problem writing the declaration.
     */
    private void writeNotationDecl(final Notation decl) throws SAXException {
        writeRaw("<!NOTATION ");
        writeRaw(decl.name);
        if (decl.publicId != null && decl.systemId != null) {
            writeRaw(" PUBLIC \"");
            writeRaw(decl.publicId);
            writeRaw("\" \"");
            writeRaw(decl.systemId);
            writeRaw('"');
        } else if (decl.publicId != null) {
            writeRaw(" PUBLIC \"");
            writeRaw(decl.publicId);
            writeRaw('"');
        } else if (decl.systemId != null) {
            writeRaw(" SYSTEM \"");
            writeRaw(decl.systemId);
            writeRaw('"');
        }
        writeRaw('>');
    }

    /**
     * Write an element or attribute name.
     *
     * @param uri The namespace URI.
     * @param localName The local name.
     * @param qName The prefixed name, if available, or the empty string.
     * @param isElement {@code true} if this is an element name, {@code false} if it is an attribute name.
     * @throws SAXException This method will throw an IOException wrapped in a SAXException if there is an
     *         error writing the name.
     */
    private void writeName(final String uri, final String localName, final String qName, final boolean isElement)
            throws SAXException {
        final String prefix = findNSPrefix(uri, qName, isElement);
        if (!XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
            writeRaw(prefix);
            writeRaw(':');
        }
        if (localName.isEmpty()) {
            writeRaw(qName);
        } else {
            writeRaw(localName);
        }
    }

    /**
     * Writes the namespace declarations for the current namespace context.
     *
     * @return Number of namespace declaration attributes written
     * @throws SAXException If there is a problem writing the namespaces.
     */
    private int writeNSDecls() throws SAXException {
        // Since the iteration order for namespaces is not stable, sort them.
        final List<String> prefixes = Collections.list(this.nsSupport.getDeclaredPrefixes());
        prefixes.sort(String::compareTo);

        int numAttrs = 0;
        for (final String prefix : prefixes) {
            if (this.attrPerLine) {
                writeNewline();
                writeIndent();
                writeRaw(this.indentStr);
            } else {
                writeRaw(' ');
            }

            writeRaw(XMLConstants.XMLNS_ATTRIBUTE);
            if (!XMLConstants.DEFAULT_NS_PREFIX.equals(prefix)) {
                writeRaw(':');
                writeRaw(prefix);
            }
            writeRaw('=');

            final String uri = this.nsSupport.getURI(prefix);
            writeQuoted((uri == null) ? XMLConstants.NULL_NS_URI : uri);
            numAttrs++;
        }

        return numAttrs;
    }

    /**
     * Writes a newline to the output.
     *
     * @throws SAXException If there is an error writing the newline character. The SAXException wraps an IOException.
     */
    private void writeNewline() throws SAXException {
        try {
            this.out.write(System.lineSeparator());
        } catch (final IOException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Indents the output based on the element nesting level.
     *
     * @throws SAXException If there is a problem writing the indent.
     */
    private void writeIndent() throws SAXException {
        writeIndent(0);
    }

    /**
     * Indents the output based on the element nesting level plus the specified level.
     *
     * @param levelAdjust Add or subtract from the current level for indentation purposes only.
     * @throws SAXException If there is a problem writing the indent.
     */
    private void writeIndent(final int levelAdjust) throws SAXException {
        if (this.haveOffsetStr) {
            writeRaw(this.offsetStr);
        }

        final int level = getElementLevel() - 1 + levelAdjust;
        writeRaw(this.indentStr.repeat(level));
    }

    /**
     * Indicates whether the specified character array contains any single or double quote characters.
     *
     * @param carr Character array to test
     * @param start Starting index in the array
     * @param length Number of characters in the array to test
     * @return {@code true} if the specified character array contains one or more single or double quote characters.
     */
    private static boolean containsQuotes(final char[] carr, final int start, final int length) {
        int end = start + length;
        while (--end >= start) {
            final char c = carr[end];
            if (c == '"' || c == '\'') {
                return true;
            }
        }
        return false;
    }

    /**
     * Write the specified string to the output as an escaped string surrounded by double quotes. In addition to the
     * traditional character escapes, double quotes embedded in the string are also escaped.
     *
     * @param s String to write
     * @throws SAXException If there is an error writing the string. The SAXException wraps an IOException.
     */
    @AccessForTesting
    void writeQuoted(final String s) throws SAXException {
        writeQuoted(s.toCharArray(), 0, s.length());
    }

    /**
     * Write the specified character array to the output as an escaped string surrounded by double quotes.
     * In addition to the traditional character escapes, double quotes embedded in the string are also escaped.
     *
     * @param carr Character array to write
     * @param start Starting index in the array
     * @param length Number of characters to write
     * @throws SAXException If there is an error writing the characters. The SAXException wraps an IOException.
     */
    @AccessForTesting
    void writeQuoted(final char[] carr, final int start, final int length) throws SAXException {
        writeRaw('"');
        writeEscaped(carr, start, length);
        writeRaw('"');
    }

    /**
     * Writes the specified character array to the output escaping the '&amp;', '&lt;', and '&gt;' characters using
     * the standard XML escape sequences and escaping any character above the ASCII range using a numeric character
     * reference.
     *
     * @param carr Character array to write
     * @param start Starting index in the array
     * @param length Number of characters to write
     * @throws SAXException If there is an error writing the characters. The SAXException wraps an IOException.
     */
    @AccessForTesting
    void writeEscaped(final char[] carr, final int start, final int length) throws SAXException {
        try {
            XmlEscaper.escape(carr, start, length, this.out, this.escapeOptions);
        } catch (final IOException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Writes the specified string to the output without escaping.
     *
     * @param s String to write
     * @throws SAXException If there is an error writing the string. The SAXException wraps an IOException.
     */
    @AccessForTesting
    void writeRaw(final String s) throws SAXException {
        try {
            this.out.write(s);
        } catch (final IOException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Writes the specified character array to the output without escaping.
     *
     * @param carr Character array to write
     * @param start Starting index in the array
     * @param length Number of characters to write
     * @throws SAXException If there is an error writing the characters. The SAXException wraps an IOException.
     */
    @AccessForTesting
    void writeRaw(final char[] carr, final int start, final int length) throws SAXException {
        try {
            this.out.write(carr, start, length);
        } catch (final IOException ex) {
            throw new SAXException(ex);
        }
    }

    /**
     * Writes the specified character to the output without escaping.
     *
     * @param c Character to write
     * @throws SAXException If there is an error writing the character. The SAXException wraps an IOException.
     */
    @AccessForTesting
    void writeRaw(final char c) throws SAXException {
        try {
            this.out.write(c);
        } catch (final IOException ex) {
            throw new SAXException(ex);
        }
    }
}
