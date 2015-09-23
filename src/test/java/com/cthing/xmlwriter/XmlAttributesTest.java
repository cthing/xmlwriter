/*
 * Copyright 2014 C Thing Software
 * All rights reserved.
 */
package com.cthing.xmlwriter;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;


/**
 * Tests the {@link XmlAttributes} class.
 */
public class XmlAttributesTest {

    @Test
    public void testDefaultCtor() {
        final XmlAttributes attrs = new XmlAttributes();
        assertThat(attrs.getLength()).isEqualTo(0);
    }

    @Test
    public void testAttrCtor() {
        final XmlAttributes attrs = new XmlAttributes("attr1", "value1", "attr2", "value2");
        assertThat(attrs.getLength()).isEqualTo(2);
        assertThat(attrs.getValue("attr1")).isEqualTo("value1");
        assertThat(attrs.getValue("attr2")).isEqualTo("value2");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadAttrCtor() {
        new XmlAttributes("attr1", "value1", "attr2");
    }

    @Test
    public void testAddString() {
        final XmlAttributes attrs = new XmlAttributes();
        attrs.addAttribute("attr1", "value1").addAttribute("attr2", "value2");
        assertThat(attrs.getLength()).isEqualTo(2);
        assertThat(attrs.getValue("attr1")).isEqualTo("value1");
        assertThat(attrs.getValue("attr2")).isEqualTo("value2");
    }

    @Test
    public void testAddIntegers() {
        final XmlAttributes attrs = new XmlAttributes();
        attrs.addAttribute("attr1", 1).addAttribute("attr2", 2);
        assertThat(attrs.getLength()).isEqualTo(2);
        assertThat(attrs.getValue("attr1")).isEqualTo("1");
        assertThat(attrs.getValue("attr2")).isEqualTo("2");
    }
}
