/*
 * Copyright 2022 C Thing Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cthing.xmlwriter;

import java.io.StringReader;
import java.io.StringWriter;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;

import static org.assertj.core.api.Assertions.assertThat;


class XmlWriterTest {

    /** Specifies the newline character sequence to use. */
    private static final String NEWLINE = System.getProperty("line.separator");

    private StringWriter stringWriter;
    private XmlWriter xmlWriter;

    @BeforeEach
    void setup() {
        this.stringWriter = new StringWriter();
        this.xmlWriter = new XmlWriter(this.stringWriter);
    }


    @Test
    @DisplayName("Write a string without any processing")
    void testWriteRawString() throws Exception {
        final String testString = "Hello &<>\" World\u00A9\n";

        this.xmlWriter.writeRaw(testString);

        assertThat(this.stringWriter).hasToString(testString);
    }

    @Test
    @DisplayName("Write an array without any processing")
    void testWriteRawArray() throws Exception {
        final String testString = "Hello &<>\" World\u00A9\n";

        this.xmlWriter.writeRaw(testString.toCharArray(), 0, testString.length());

        assertThat(this.stringWriter).hasToString(testString);
    }

    @Test
    @DisplayName("Write a character without any processing")
    void testWriteRawChar() throws Exception {
        final String testString = "a";

        this.xmlWriter.writeRaw(testString.charAt(0));

        assertThat(this.stringWriter).hasToString(testString);
    }

    @Test
    @DisplayName("Write an array with escaping")
    void testWriteEscapedArray() throws Exception {
        final String testStringIn = "<Hello &<>\" World\u00A9\u001A\t\n";
        final String testStringOut = "&lt;Hello &amp;&lt;&gt;\" World&#169;ctrl-26\t\n";

        this.xmlWriter.writeEscaped(testStringIn.toCharArray(), 0, testStringIn.length());

        assertThat(this.stringWriter).hasToString(testStringOut);
    }

    @Test
    @DisplayName("Write a character with escaping")
    void testWriteEscapedChar() throws Exception {
        this.xmlWriter.writeEscaped('\u00A9');

        assertThat(this.stringWriter).hasToString("&#169;");
    }

    @Test
    @DisplayName("Write a string with escaping disabled")
    void testWriteEscapedDisabled() throws Exception {
        final String testStringIn = "<Hello &<>\" World\u00A9\u001A\t\n";
        this.xmlWriter.setEscaping(false);

        this.xmlWriter.writeEscaped(testStringIn.toCharArray(), 0, testStringIn.length());

        assertThat(this.stringWriter).hasToString(testStringIn);
    }

    @Test
    @DisplayName("Write a string adding quotes")
    void testWriteQuotedString() throws Exception {
        final String testStringIn = "Hello &<>\"' World\u00A9";
        final String testStringOut = "\"Hello &amp;&lt;&gt;&quot;&apos; World&#169;\"";

        this.xmlWriter.writeQuoted(testStringIn);

        assertThat(this.stringWriter).hasToString(testStringOut);
    }

    @Test
    @DisplayName("Write an array adding quotes")
    void testWriteQuotedArray() throws Exception {
        final String testStringIn = "Hello &<>\"' World\u00A9";
        final String testStringOut = "\"Hello &amp;&lt;&gt;&quot;&apos; World&#169;\"";

        this.xmlWriter.writeQuoted(testStringIn.toCharArray(), 0, testStringIn.length());

        assertThat(this.stringWriter).hasToString(testStringOut);
    }

    @Test
    @DisplayName("Write string adding quotes with escaping disabled")
    void testWriteQuotedNoEscaping() throws Exception {
        final String testStringIn = "Hello &<>\"' World\u00A9";
        this.xmlWriter.setEscaping(false);

        this.xmlWriter.writeQuoted(testStringIn);

        assertThat(this.stringWriter).hasToString("\"" + testStringIn + "\"");
    }

    @ParameterizedTest
    @MethodSource("minimalDocumentProvider")
    @DisplayName("Write a minimal XML document")
    void testMinimalDocument(final String encoding, final boolean standalone, final boolean fragment,
                             final String output) throws Exception {
        this.xmlWriter.startDocument(encoding, standalone, fragment);
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString(output);
    }

    @ParameterizedTest
    @MethodSource("minimalDocumentProvider")
    @DisplayName("Reset the writer")
    void testReset(final String encoding, final boolean standalone, final boolean fragment,
                             final String output) throws Exception {
        this.xmlWriter.startDocument(encoding, standalone, fragment);
        this.xmlWriter.endDocument();
        this.xmlWriter.reset();
        this.stringWriter = new StringWriter();
        this.xmlWriter.setOutput(this.stringWriter);
        this.xmlWriter.startDocument(encoding, standalone, fragment);
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString(output);
    }

    private static Stream<Arguments> minimalDocumentProvider() {
        return Stream.of(
                Arguments.of(null, true, false,
                             "<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE + NEWLINE),
                Arguments.of(null, false, false,
                             "<?xml version=\"1.0\" standalone=\"no\"?>" + NEWLINE + NEWLINE + NEWLINE),
                Arguments.of("UTF-8", false, false,
                             "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>" + NEWLINE + NEWLINE + NEWLINE),
                Arguments.of("UTF-8", false, true, NEWLINE)
        );
    }

    @Test
    @DisplayName("Write document with elements not minimized")
    void testSimpleElementsPlain() throws Exception {
        this.xmlWriter.setMinimizeEmpty(false);
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();
        this.xmlWriter.setMinimizeEmpty(true);

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1></elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with mix of minimized and not minimized elements")
    void testSimpleElementsDisableMinimize() throws Exception {
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.startElement("elem2").endElement();
        this.xmlWriter.emptyElement("elem3");
        this.xmlWriter.setMinimizeEmpty(false);
        this.xmlWriter.startElement("elem4");
        this.xmlWriter.endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();
        this.xmlWriter.setMinimizeEmpty(true);

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1><elem2/><elem3/><elem4></elem4></elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with pretty printing")
    void testSimpleElementsPrettyPrint() throws Exception {
        this.xmlWriter.setPrettyPrint(true);
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.startElement("elem2").endElement();
        this.xmlWriter.emptyElement("elem3");
        this.xmlWriter.setMinimizeEmpty(false);
        this.xmlWriter.startElement("elem4").endElement();
        this.xmlWriter.startElement("elem5");
        this.xmlWriter.startElement("elem6");
        this.xmlWriter.endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();
        this.xmlWriter.setMinimizeEmpty(true);
        this.xmlWriter.setPrettyPrint(false);

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1>" + NEWLINE
                                                        + "    <elem2/>" + NEWLINE
                                                        + "    <elem3/>" + NEWLINE
                                                        + "    <elem4>" + NEWLINE
                                                        + "    </elem4>" + NEWLINE
                                                        + "    <elem5>" + NEWLINE
                                                        + "        <elem6>" + NEWLINE
                                                        + "        </elem6>" + NEWLINE
                                                        + "    </elem5>" + NEWLINE
                                                        + "</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with element attributes")
    void testAttributesPlain() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        final AttributesImpl attrs1 = new AttributesImpl();
        attrs1.addAttribute("", "a1", "", "CDATA", "v1");
        attrs1.addAttribute("", "a2", "", "CDATA", "v2");
        attrs1.addAttribute("", "a3", "", "CDATA", "v3");

        final AttributesImpl attrs2 = new AttributesImpl();
        attrs2.addAttribute("", "b1", "", "CDATA", "v10");
        attrs2.addAttribute("", "b2", "", "CDATA", "v20");
        attrs2.addAttribute("", "b3", "", "CDATA", "v30");

        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1", attrs1);
        this.xmlWriter.startElement("elem2", attrs2);
        this.xmlWriter.endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1 a1=\"v1\" a2=\"v2\" a3=\"v3\">" + NEWLINE
                                                        + "    <elem2 b1=\"v10\" b2=\"v20\" b3=\"v30\"/>" + NEWLINE
                                                        + "</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with each element attribute on a separate line")
    void testAttributesOnePerLine() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        final AttributesImpl attrs1 = new AttributesImpl();
        attrs1.addAttribute("", "a1", "", "CDATA", "v1");
        attrs1.addAttribute("", "a2", "", "CDATA", "v2");
        attrs1.addAttribute("", "a3", "", "CDATA", "v3");

        final AttributesImpl attrs2 = new AttributesImpl();
        attrs2.addAttribute("", "b1", "", "CDATA", "v10");
        attrs2.addAttribute("", "b2", "", "CDATA", "v20");
        attrs2.addAttribute("", "b3", "", "CDATA", "v30");

        this.xmlWriter.setAttrPerLine(true);
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1", attrs1);
        this.xmlWriter.startElement("elem2", attrs2).endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1" + NEWLINE
                                                        + "    a1=\"v1\"" + NEWLINE
                                                        + "    a2=\"v2\"" + NEWLINE
                                                        + "    a3=\"v3\"" + NEWLINE + ">" + NEWLINE
                                                        + "    <elem2" + NEWLINE
                                                        + "        b1=\"v10\"" + NEWLINE
                                                        + "        b2=\"v20\"" + NEWLINE
                                                        + "        b3=\"v30\"" + NEWLINE + "    />" + NEWLINE
                                                        + "</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document adding attribute objects")
    void testAttributesUsingAddAttributes() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        final AttributesImpl attrs1 = new AttributesImpl();
        attrs1.addAttribute("", "a1", "", "CDATA", "v1");
        attrs1.addAttribute("", "a2", "", "CDATA", "v2");
        attrs1.addAttribute("", "a3", "", "CDATA", "v3");

        final AttributesImpl attrs2 = new AttributesImpl();
        attrs2.addAttribute("", "b1", "", "CDATA", "v10");
        attrs2.addAttribute("", "b2", "", "CDATA", "v20");
        attrs2.addAttribute("", "b3", "", "CDATA", "v30");

        this.xmlWriter.startDocument();
        this.xmlWriter.emptyElement("elem1", attrs1).addAttribute("z1", 13).addAttributes(attrs2);
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1 a1=\"v1\" a2=\"v2\" a3=\"v3\" z1=\"13\" "
                                                        + "b1=\"v10\" b2=\"v20\" b3=\"v30\"/>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document setting attribute objects")
    void testAttributesUsingSetAttributes() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        final AttributesImpl attrs1 = new AttributesImpl();
        attrs1.addAttribute("", "a1", "", "CDATA", "v1");
        attrs1.addAttribute("", "a2", "", "CDATA", "v2");
        attrs1.addAttribute("", "a3", "", "CDATA", "v3");

        final AttributesImpl attrs2 = new AttributesImpl();
        attrs2.addAttribute("", "b1", "", "CDATA", "v10");
        attrs2.addAttribute("", "b2", "", "CDATA", "v20");
        attrs2.addAttribute("", "b3", "", "CDATA", "v30");

        this.xmlWriter.startDocument();
        this.xmlWriter.emptyElement("elem1", attrs1);
        this.xmlWriter.setAttributes(attrs2);
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1 b1=\"v10\" b2=\"v20\" b3=\"v30\"/>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with character content")
    void testCharacterData() throws Exception {
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.characters("Hello World");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1>Hello World</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with character content and an XML comment")
    void testCharacterDataAndComments() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.startElement("elem2").characters("Hello World").endElement();
        this.xmlWriter.startElement("elem3");
        this.xmlWriter.newline();
        this.xmlWriter.comment(" A comment ");
        this.xmlWriter.endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();
        this.xmlWriter.setPrettyPrint(false);

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1>" + NEWLINE
                                                        + "    <elem2>Hello World</elem2>" + NEWLINE
                                                        + "    <elem3>" + NEWLINE
                                                        + "        <!-- A comment -->" + NEWLINE
                                                        + "    </elem3>" + NEWLINE
                                                        + "</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with character content, CDATA and an XML comment")
    void testCharacterDataCdataAndComments() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.characters("Hello World");
        this.xmlWriter.entityRef("amp");
        this.xmlWriter.characterRef('a');
        this.xmlWriter.startElement("elem2");
        this.xmlWriter.cdataSection("This is a <test>");
        this.xmlWriter.comment(" First comment ");
        this.xmlWriter.endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();
        this.xmlWriter.setPrettyPrint(false);

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1>Hello World&amp;&#97;<elem2>"
                                                        + "<![CDATA[This is a <test>]]>"
                                                        + "<!-- First comment --></elem2>" + NEWLINE
                                                        + "</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with empty character content")
    void testEmptyCharacterData() throws Exception {
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.characters("");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                                   + "<elem1></elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with null character content")
    void testNullCharacterData() throws Exception {
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.characters(null);
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem1/>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with internal and external entity references")
    void testEntityRefs() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.characters("This ");
        this.xmlWriter.entityRef("amp");
        this.xmlWriter.characters(" that. The letter ");
        this.xmlWriter.characterRef('a');
        this.xmlWriter.emptyElement("elem2");
        this.xmlWriter.newline();
        this.xmlWriter.comment(" First comment ");
        this.xmlWriter.newline();
        this.xmlWriter.entityRef("extFile");
        this.xmlWriter.newline();
        this.xmlWriter.emptyElement("elem3");
        this.xmlWriter.emptyElement("elem4");
        this.xmlWriter.newline();
        this.xmlWriter.comment(" Second comment ").entityRef("charFile").comment(" Third comment ");
        this.xmlWriter.emptyElement("elem5");
        this.xmlWriter.newline();
        this.xmlWriter.comment(" Fourth comment ");
        this.xmlWriter.entityRef("dataFile", XmlWriter.FormattingHint.BLOCK);
        this.xmlWriter.comment(" Fifth comment ");
        this.xmlWriter.emptyElement("elem6");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                            + "<elem1>This &amp; that. The letter &#97;<elem2/>" + NEWLINE
                                            + "    <!-- First comment -->" + NEWLINE
                                            + "    &extFile;" + NEWLINE
                                            + "    <elem3/>" + NEWLINE
                                            + "    <elem4/>" + NEWLINE
                                            + "    <!-- Second comment -->&charFile;<!-- Third comment --><elem5/>" + NEWLINE
                                            + "    <!-- Fourth comment -->" + NEWLINE
                                            + "    &dataFile;" + NEWLINE
                                            + "    <!-- Fifth comment -->" + NEWLINE
                                            + "    <elem6/>" + NEWLINE
                                            + "</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write document with XML comments")
    void testComments() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.comment(" A comment 1 ");
        this.xmlWriter.emptyElement("elem2");
        this.xmlWriter.emptyElement("elem3");
        this.xmlWriter.emptyElement("elem4");
        this.xmlWriter.emptyElement("elem5");
        this.xmlWriter.startElement("elem6");
        this.xmlWriter.newline();
        this.xmlWriter.comment(" A comment 2 ");
        this.xmlWriter.newline();
        this.xmlWriter.comment(" A comment 3 ");
        this.xmlWriter.newline();
        this.xmlWriter.newline();
        this.xmlWriter.comment(" A comment 4 ");
        this.xmlWriter.emptyElement("elem7");
        this.xmlWriter.endElement();
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                + "<elem1><!-- A comment 1 -->" + NEWLINE
                + "    <elem2/>" + NEWLINE
                + "    <elem3/>" + NEWLINE
                + "    <elem4/>" + NEWLINE
                + "    <elem5/>" + NEWLINE
                + "    <elem6>" + NEWLINE
                + "        <!-- A comment 2 -->" + NEWLINE
                + "        <!-- A comment 3 -->" + NEWLINE
                + "        " + NEWLINE
                + "        <!-- A comment 4 -->" + NEWLINE
                + "        <elem7/>" + NEWLINE
                + "    </elem6>" + NEWLINE
                + "</elem1>" + NEWLINE);
    }

    @Test
    @DisplayName("Write doctype with no public ID")
    void testDoctypeNoPublicId() throws Exception {
        this.xmlWriter.startDocument();
        this.xmlWriter.doctype("elem1", null, "/foo/bar.dtd");
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                + "<!DOCTYPE elem1 SYSTEM \"/foo/bar.dtd\">" + NEWLINE + NEWLINE + NEWLINE);
    }

    @Test
    @DisplayName("Write doctype with public ID")
    void testDoctypeWithPublicId() throws Exception {
        this.xmlWriter.startDocument();
        this.xmlWriter.doctype("elem1", "FOO", "/foo/bar.dtd");
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<!DOCTYPE elem1 PUBLIC \"FOO\" \"/foo/bar.dtd\">" + NEWLINE + NEWLINE + NEWLINE);
    }

    @Test
    @DisplayName("Write doctype with entity and notation declarations")
    void testDoctypeWithEntityAndNotationDecls() throws Exception {
        final List<XmlWriter.Entity> entities = new LinkedList<>();
        entities.add(new XmlWriter.Entity("ent1", "v1"));
        entities.add(new XmlWriter.Entity("ent2", null, "bar.xml", null));
        entities.add(new XmlWriter.Entity("ent3", "BAR", "foo.xml", null));
        entities.add(new XmlWriter.Entity("ent4", null, "joe.txt", "Txt"));

        final List<XmlWriter.Notation> notations = new LinkedList<>();
        notations.add(new XmlWriter.Notation("not1", null, "bar.xml"));
        notations.add(new XmlWriter.Notation("not2", "BAR", "foo.xml"));
        notations.add(new XmlWriter.Notation("not3", "BAR", null));

        this.xmlWriter.startDocument();
        this.xmlWriter.doctype("elem1", "FOO", "/foo/bar.dtd", entities, notations);
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<!DOCTYPE elem1 PUBLIC \"FOO\" \"/foo/bar.dtd\" [" + NEWLINE
                                                        + "    <!ENTITY ent1 \"v1\">" + NEWLINE
                                                        + "    <!ENTITY ent2 SYSTEM \"bar.xml\">" + NEWLINE
                                                        + "    <!ENTITY ent3 PUBLIC \"BAR\" \"foo.xml\">" + NEWLINE
                                                        + "    <!ENTITY ent4 SYSTEM \"joe.txt\" NDATA Txt>" + NEWLINE
                                                        + "    <!NOTATION not1 SYSTEM \"bar.xml\">" + NEWLINE
                                                        + "    <!NOTATION not2 PUBLIC \"BAR\" \"foo.xml\">" + NEWLINE
                                                        + "    <!NOTATION not3 PUBLIC \"BAR\">" + NEWLINE
                                                        + "]>" + NEWLINE + NEWLINE + NEWLINE);
    }

    @Test
    @DisplayName("Write processing instruction")
    void testPI() throws Exception {
        this.xmlWriter.startDocument();
        this.xmlWriter.processingInstruction("foo", "bar=\"joe\"");
        this.xmlWriter.startElement("elem1");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                + "<?foo bar=\"joe\"?><elem1/>" + NEWLINE);
    }

    @Test
    @DisplayName("Elements with namespaces and generated prefixes")
    void testElementNamespaceGeneratedPrefix() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem0");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem1");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem2");
        this.xmlWriter.emptyElement("http://www.adobe.com/test2", "elem3");
        this.xmlWriter.emptyElement("http://www.adobe.com/test3", "elem4");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem0>" + NEWLINE
                                                        + "    <__NS1:elem1 xmlns:__NS1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                                                        + "    <__NS1:elem2 xmlns:__NS1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                                                        + "    <__NS2:elem3 xmlns:__NS2=\"http://www.adobe.com/test2\"/>" + NEWLINE
                                                        + "    <__NS3:elem4 xmlns:__NS3=\"http://www.adobe.com/test3\"/>" + NEWLINE
                                                        + "</elem0>" + NEWLINE);
    }

    @Test
    @DisplayName("Elements with namespaces and prefixes")
    void testElementNamespacePrefix() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.addNSPrefix("t1", "http://www.adobe.com/test1");
        this.xmlWriter.addNSPrefix("t2", "http://www.adobe.com/test2");
        this.xmlWriter.addNSPrefix("", "http://www.adobe.com/test3");
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem0");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem1");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem2");
        this.xmlWriter.emptyElement("http://www.adobe.com/test2", "elem3");
        this.xmlWriter.emptyElement("http://www.adobe.com/test3", "elem4");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem0>" + NEWLINE
                                                        + "    <t1:elem1 xmlns:t1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                                                        + "    <t1:elem2 xmlns:t1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                                                        + "    <t2:elem3 xmlns:t2=\"http://www.adobe.com/test2\"/>" + NEWLINE
                                                        + "    <elem4 xmlns=\"http://www.adobe.com/test3\"/>" + NEWLINE
                                                        + "</elem0>" + NEWLINE);
    }

    @Test
    @DisplayName("Element with namespaces, specified and generated prefixes")
    void testElementNamespaceMixedPrefixes() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem0");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem1", "t1:elem1");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem2");
        this.xmlWriter.emptyElement("http://www.adobe.com/test2", "elem3");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem0>" + NEWLINE
                                                        + "    <t1:elem1 xmlns:t1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                                                        + "    <t1:elem2 xmlns:t1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                                                        + "    <__NS1:elem3 xmlns:__NS1=\"http://www.adobe.com/test2\"/>" + NEWLINE
                                                        + "</elem0>" + NEWLINE);
    }

    @Test
    @DisplayName("Elements with root namespace declarations")
    void testElementNamespaceRootDecls() throws Exception {
        this.xmlWriter.setPrettyPrint(true);

        this.xmlWriter.addNSRootDecl("t1", "http://www.adobe.com/test1");
        this.xmlWriter.addNSRootDecl("t2", "http://www.adobe.com/test2");
        this.xmlWriter.addNSRootDecl("", "http://www.adobe.com/test3");
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem0");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem1");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem2");
        this.xmlWriter.emptyElement("http://www.adobe.com/test2", "elem3");
        this.xmlWriter.emptyElement("http://www.adobe.com/test3", "elem4");
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem0 xmlns=\"http://www.adobe.com/test3\" "
                                                        + "xmlns:t1=\"http://www.adobe.com/test1\" "
                                                        + "xmlns:t2=\"http://www.adobe.com/test2\">" + NEWLINE
                                                        + "    <t1:elem1/>" + NEWLINE
                                                        + "    <t1:elem2/>" + NEWLINE
                                                        + "    <t2:elem3/>" + NEWLINE
                                                        + "    <elem4/>" + NEWLINE
                                                        + "</elem0>" + NEWLINE);
    }

    @Test
    @DisplayName("Attributes with namespaces")
    void testAttributeNamespaces() throws Exception {
        this.xmlWriter.setPrettyPrint(true);
        this.xmlWriter.setAttrPerLine(true);

        final AttributesImpl attrs = new AttributesImpl();
        attrs.addAttribute("", "a1", "", "CDATA", "v1");
        attrs.addAttribute("http://www.adobe.com/at1", "a2", "", "CDATA", "v2");
        attrs.addAttribute("http://www.adobe.com/at3", "a3", "A3:a3", "CDATA", "v3");

        this.xmlWriter.addNSPrefix("t1", "http://www.adobe.com/test1");
        this.xmlWriter.startDocument();
        this.xmlWriter.startElement("elem0");
        this.xmlWriter.emptyElement("http://www.adobe.com/test1", "elem2", attrs);
        this.xmlWriter.endElement();
        this.xmlWriter.endDocument();

        assertThat(this.stringWriter).hasToString("<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                                                        + "<elem0>" + NEWLINE
                                                        + "    <t1:elem2" + NEWLINE
                                                        + "        a1=\"v1\"" + NEWLINE
                                                        + "        __NS1:a2=\"v2\"" + NEWLINE
                                                        + "        A3:a3=\"v3\"" + NEWLINE
                                                        + "        xmlns:A3=\"http://www.adobe.com/at3\"" + NEWLINE
                                                        + "        xmlns:__NS1=\"http://www.adobe.com/at1\"" + NEWLINE
                                                        + "        xmlns:t1=\"http://www.adobe.com/test1\"" + NEWLINE
                                                        + "    />" + NEWLINE
                                                        + "</elem0>" + NEWLINE);
    }

    @Test
    @DisplayName("Use as parsing filter without namespaces")
    void testFilterWithoutNamespaces() throws Exception {
        final String xml = "<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                         + "<elem1 version=\"1\">" + NEWLINE
                         + "    <elem2 src=\"foo\">This is a test document</elem2>" + NEWLINE
                         + "    <elem3 dst=\"bar\"/>" + NEWLINE
                         + "    <elem4/>" + NEWLINE
                         + "</elem1>" + NEWLINE;

        final XMLReader xmlReader = newXmlReader(false, false, false, false);
        final StringWriter writer = new StringWriter();
        final StringReader reader = new StringReader(xml);
        final XmlWriter xmlWriterFilter = new XmlWriter(xmlReader, writer);

        xmlWriterFilter.parse(reader);

        assertThat(writer).hasToString(xml);
    }

    @Test
    @DisplayName("Use as parsing filter with namespaces")
    void testFilterWithNamespaces() throws Exception {
        final String xml = "<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                         + "<elem1 version=\"1\" xmlns=\"http://www.adobe.com/test\">" + NEWLINE
                         + "    <elem2 src=\"foo\">This is a test document</elem2>" + NEWLINE
                         + "    <t1:elem3 dst=\"bar\" xmlns:t1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                         + "    <elem4/>" + NEWLINE
                         + "</elem1>" + NEWLINE;

        final XMLReader xmlReader = newXmlReader(true, false, false, false);
        final StringWriter writer = new StringWriter();
        final StringReader reader = new StringReader(xml);
        final XmlWriter xmlWriterFilter = new XmlWriter(xmlReader, writer);

        xmlWriterFilter.parse(reader);

        assertThat(writer).hasToString(xml);
    }

    @Test
    @DisplayName("Use as parsing filter with namespaces, DTD validation, CDATA, and comments")
    void testFilterComplexDocument() throws Exception {
        final String xml = "<?xml version=\"1.0\" standalone=\"no\"?>" + NEWLINE + NEWLINE
                         + "<!DOCTYPE elem1 SYSTEM \"XmlWriterTest.dtd\">" + NEWLINE + NEWLINE
                         + "<elem1 version=\"1\" xmlns=\"http://www.adobe.com/test\">" + NEWLINE
                         + "    <elem2 src=\"foo\">This is a test document</elem2>" + NEWLINE
                         + "    <t1:elem3 dst=\"bar\" xmlns:t1=\"http://www.adobe.com/test1\"/>" + NEWLINE
                         + "    <!-- Next comes a CDATA section -->" + NEWLINE
                         + "    <elem4><![CDATA[final boolean v = a < 3;]]></elem4>" + NEWLINE
                         + "</elem1>" + NEWLINE;

        final XMLReader xmlReader = newXmlReader(true, true, false, false);
        final StringWriter writer = new StringWriter();
        final StringReader reader = new StringReader(xml);
        final XmlWriter xmlWriterFilter = new XmlWriter(xmlReader, writer);

        xmlWriterFilter.setProperty("http://xml.org/sax/properties/lexical-handler", xmlWriterFilter);
        xmlWriterFilter.setEntityResolver(new EntityResolver() {
            @Override
            public InputSource resolveEntity(final String publicId,
                                             final String systemId) {
                return new InputSource(getClass().getResourceAsStream("/XmlWriterTest.dtd"));
            }
        });
        xmlWriterFilter.setStandalone(false);
        xmlWriterFilter.parse(reader);

        assertThat(writer).hasToString(xml);
    }

    @Test
    @DisplayName("Use as parsing filter with schema validation")
    void testFilterSchemaValidation() throws Exception {
        final String xml = "<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                         + "<elem1 version=\"1\">" + NEWLINE
                         + "    <elem2 src=\"foo\">This is a test document</elem2>" + NEWLINE
                         + "    <elem3 dst=\"bar\"/>" + NEWLINE
                         + "    <!-- Next comes a CDATA section -->" + NEWLINE
                         + "    <elem4><![CDATA[final boolean v = a < 3;]]></elem4>" + NEWLINE
                         + "</elem1>" + NEWLINE;

        final XMLReader xmlReader = newXmlReader(true, false, false, true);
        final StringWriter writer = new StringWriter();
        final StringReader reader = new StringReader(xml);
        final XmlWriter xmlWriterFilter = new XmlWriter(xmlReader, writer);

        xmlWriterFilter.setProperty("http://xml.org/sax/properties/lexical-handler", xmlWriterFilter);
        xmlWriterFilter.parse(reader);

        assertThat(writer).hasToString(xml);
    }

    @Test
    @DisplayName("Use as parsing filter with default attributes")
    void testFilterWithDefaultAttributes() throws Exception {
        final String xml = "<?xml version=\"1.0\" standalone=\"yes\"?>" + NEWLINE + NEWLINE
                         + "<elem1 version=\"1\">" + NEWLINE
                         + "    <elem2 src=\"foo\">This is a test document</elem2>" + NEWLINE
                         + "    <elem3 dst=\"bar\" isGood=\"true\"/>" + NEWLINE
                         + "    <!-- Next comes a CDATA section -->" + NEWLINE
                         + "    <elem4><![CDATA[final boolean v = a < 3;]]></elem4>" + NEWLINE
                         + "</elem1>" + NEWLINE;

        final XMLReader xmlReader = newXmlReader(true, false, false, true);
        final StringWriter writer = new StringWriter();
        final StringReader reader = new StringReader(xml);
        final XmlWriter xmlWriterFilter = new XmlWriter(xmlReader, writer);

        xmlWriterFilter.setSpecifiedAttributes(false);
        xmlWriterFilter.setProperty("http://xml.org/sax/properties/lexical-handler", xmlWriterFilter);
        xmlWriterFilter.parse(reader);

        assertThat(writer).hasToString(xml);
    }

    private XMLReader newXmlReader(final boolean namespaceAware, final boolean validating, final boolean xincludeAware,
                                   final boolean useSchema) throws SAXException, ParserConfigurationException {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(namespaceAware);
        factory.setValidating(validating);
        factory.setXIncludeAware(xincludeAware);

        if (useSchema) {
            final SchemaFactory schemaFactory =
                    SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            final URL url = getClass().getResource("/XmlWriterTest.xsd");
            final Schema schema = schemaFactory.newSchema(url);
            factory.setSchema(schema);
        }

        return factory.newSAXParser().getXMLReader();
    }
}
