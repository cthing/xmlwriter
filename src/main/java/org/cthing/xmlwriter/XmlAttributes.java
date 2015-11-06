/*
 * Copyright 2015 C Thing Software
 * All rights reserved.
 */
package org.cthing.xmlwriter;

import javax.xml.XMLConstants;

import org.xml.sax.helpers.AttributesImpl;


/**
 * Convenience class for working with SAX attributes. Simplifies attribute
 * setting and adds a fluent interface.
 */
public class XmlAttributes extends AttributesImpl {

    private static final String EMPTY = "";

    /**
     * Creates the attributes list with an initial attribute.
     *
     * @param args  Name followed by value for each attribute to be added.
     */
    public XmlAttributes(final String... args) {
        if ((args.length % 2) != 0) {
            throw new IllegalArgumentException("An even number of argument must be specified (i.e. name, value)");
        }

        int i = 0;
        while (i < args.length) {
            final String name = args[i++];
            final String value = args[i++];
            addAttribute(XMLConstants.DEFAULT_NS_PREFIX, EMPTY, name, "CDATA", value);
        }
    }

    /**
     * Adds an attribute to the list of attributes.
     *
     * @param name  Name for the attribute
     * @param value  Value for the attribute
     * @return This class instance.
     */
    public XmlAttributes addAttribute(final String name, final String value) {
        addAttribute(XMLConstants.DEFAULT_NS_PREFIX, EMPTY, name, "CDATA", value);
        return this;
    }

    /**
     * Adds an integer attribute to the list of attributes.
     *
     * @param name  Name for the attribute
     * @param value  Value for the attribute
     * @return This class instance.
     */
    public XmlAttributes addAttribute(final String name, final int value) {
        return addAttribute(name, Integer.toString(value));
    }
}
