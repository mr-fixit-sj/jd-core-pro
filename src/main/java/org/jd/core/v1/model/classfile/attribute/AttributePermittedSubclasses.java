/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.model.classfile.attribute;

/**
 * "PermittedSubclasses" attribute of sealed classes and interfaces (Java 17+).
 */
public class AttributePermittedSubclasses implements Attribute {
    protected String[] classNames;

    public AttributePermittedSubclasses(String[] classNames) {
        this.classNames = classNames;
    }

    /**
     * @return internal names of the permitted subclasses
     */
    public String[] getClassNames() {
        return classNames;
    }
}
