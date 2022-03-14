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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;


class XmlAttributesTest {

    @Test
    @DisplayName("Default construction has no attributes")
    void testDefaultCtor() {
        final XmlAttributes attrs = new XmlAttributes();

        assertThat(attrs.getLength()).isZero();
    }

    @Test
    @DisplayName("Construct with attributes")
    void testAttrCtor() {
        final XmlAttributes attrs = new XmlAttributes("attr1", "value1", "attr2", "value2");

        assertThat(attrs.getLength()).isEqualTo(2);
        assertThat(attrs.getValue("attr1")).isEqualTo("value1");
        assertThat(attrs.getValue("attr2")).isEqualTo("value2");
    }

    @Test
    @DisplayName("Construct with odd number of attributes name/value pairs throws")
    void testBadAttrCtor() {
        assertThatIllegalArgumentException().isThrownBy(() -> new XmlAttributes("attr1", "value1", "attr2"));
    }

    @Test
    @DisplayName("Add attributes with string values")
    void testAddString() {
        final XmlAttributes attrs = new XmlAttributes().addAttribute("attr1", "value1").addAttribute("attr2", "value2");

        assertThat(attrs.getLength()).isEqualTo(2);
        assertThat(attrs.getValue("attr1")).isEqualTo("value1");
        assertThat(attrs.getValue("attr2")).isEqualTo("value2");
    }

    @Test
    @DisplayName("Add attributes with integer values")
    void testAddIntegers() {
        final XmlAttributes attrs = new XmlAttributes().addAttribute("attr1", 1).addAttribute("attr2", 2);

        assertThat(attrs.getLength()).isEqualTo(2);
        assertThat(attrs.getValue("attr1")).isEqualTo("1");
        assertThat(attrs.getValue("attr2")).isEqualTo("2");
    }
}
