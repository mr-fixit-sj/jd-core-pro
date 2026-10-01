/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.model.classfile.attribute;

/**
 * "Record" attribute (Java 16+).
 */
public class AttributeRecord implements Attribute {
    protected RecordComponent[] components;

    public AttributeRecord(RecordComponent[] components) {
        this.components = components;
    }

    public RecordComponent[] getComponents() {
        return components;
    }
}
