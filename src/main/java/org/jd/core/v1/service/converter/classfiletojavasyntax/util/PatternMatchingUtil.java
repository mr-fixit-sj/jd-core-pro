/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.service.converter.classfiletojavasyntax.util;

import org.jd.core.v1.model.classfile.Method;
import org.jd.core.v1.model.classfile.attribute.AttributeCode;
import org.jd.core.v1.model.classfile.attribute.AttributeLocalVariableTable;
import org.jd.core.v1.model.classfile.attribute.LocalVariable;

import java.util.BitSet;

/**
 * Java 16+ type patterns ("expression instanceof Type variable") are compiled by javac to:
 * <pre>
 *   [expression; ASTORE tmp;]          (only if the expression is not a local variable)
 *   ALOAD x; INSTANCEOF Type; IFEQ/IFNE ...
 *   ...
 *   ALOAD x; CHECKCAST Type; ASTORE variable    (on the path where the pattern matches)
 * </pre>
 * The stores are part of the condition, not statements.
 */
public class PatternMatchingUtil {
    public static final int JAVA_16_MAJOR_VERSION = 60;

    /**
     * @param instanceOfOffset offset of an INSTANCEOF instruction
     * @param operandIndex     index of the local variable loaded before the INSTANCEOF instruction
     * @return the offset of the binding sequence (ALOAD, CHECKCAST, ASTORE) or -1
     */
    public static int searchBindingOffset(byte[] code, int instanceOfOffset, int operandIndex) {
        int ifOffset = instanceOfOffset + 3;

        if (ifOffset + 2 >= code.length) {
            return -1;
        }

        int bindingOffset;

        switch (code[ifOffset] & 255) {
            case 153: // IFEQ: the pattern matches on the fall-through path
                bindingOffset = ifOffset + 3;
                break;
            case 154: // IFNE: the pattern matches on the branch path
                bindingOffset = ifOffset + (short)(((code[ifOffset+1] & 255) << 8) | (code[ifOffset+2] & 255));
                break;
            default:
                return -1;
        }

        if ((bindingOffset < 0) || (bindingOffset + 5 > code.length)) {
            return -1;
        }

        // ALOAD x
        int offset = bindingOffset;
        int opcode = code[offset] & 255;

        if (opcode == 25) { // ALOAD
            if ((code[offset+1] & 255) != operandIndex) {
                return -1;
            }
            offset += 2;
        } else if ((opcode >= 42) && (opcode <= 45)) { // ALOAD_0 ... ALOAD_3
            if (opcode - 42 != operandIndex) {
                return -1;
            }
            offset++;
        } else {
            return -1;
        }

        // CHECKCAST Type
        if ((offset + 4 > code.length) || ((code[offset] & 255) != 192) || (code[offset+1] != code[instanceOfOffset+1]) || (code[offset+2] != code[instanceOfOffset+2])) {
            return -1;
        }

        // ASTORE variable
        opcode = code[offset + 3] & 255;

        if ((opcode == 58) || ((opcode >= 75) && (opcode <= 78))) {
            return bindingOffset;
        }

        return -1;
    }

    /**
     * @return the offset of the ASTORE instruction of the binding sequence starting at 'bindingOffset'
     */
    public static int getBindingStoreOffset(byte[] code, int bindingOffset) {
        return ByteCodeUtil.getNextInstructionOffset(code, bindingOffset) + 3;
    }

    /**
     * @return true if the variable stored by the binding sequence is a fresh variable, assigned nowhere else
     */
    public static boolean isBindingVariable(Method method, byte[] code, int bindingOffset) {
        int storeOffset = getBindingStoreOffset(code, bindingOffset);
        int index = ByteCodeUtil.getStoredLocalVariableIndex(code, storeOffset);
        int startPc = ByteCodeUtil.getNextInstructionOffset(code, storeOffset);
        AttributeCode attributeCode = method.getAttribute("Code");
        AttributeLocalVariableTable localVariableTable = attributeCode.getAttribute("LocalVariableTable");

        if (localVariableTable == null) {
            return true;
        }

        for (LocalVariable localVariable : localVariableTable.getLocalVariableTable()) {
            if ((localVariable.getIndex() == index) && (localVariable.getStartPc() == startPc)) {
                int endPc = startPc + localVariable.getLength();

                for (int offset = 0; offset < code.length; offset = ByteCodeUtil.getNextInstructionOffset(code, offset)) {
                    if ((offset >= startPc) && (offset < endPc) && (ByteCodeUtil.getStoredLocalVariableIndex(code, offset) == index)) {
                        return false;
                    }
                }

                return true;
            }
        }

        return false;
    }

    /**
     * @return true if the operand variable 'index', loaded at 'offset' before an INSTANCEOF instruction, only holds
     *         the operand of a type pattern and can be inlined
     */
    public static boolean isInlinableOperandVariable(Method method, byte[] code, int index, int offset) {
        AttributeCode attributeCode = method.getAttribute("Code");
        AttributeLocalVariableTable localVariableTable = attributeCode.getAttribute("LocalVariableTable");

        if (localVariableTable != null) {
            // Synthetic variable created by javac
            for (LocalVariable localVariable : localVariableTable.getLocalVariableTable()) {
                if ((localVariable.getIndex() == index) && (localVariable.getStartPc() <= offset) && (offset < localVariable.getStartPc() + localVariable.getLength())) {
                    return false;
                }
            }

            return true;
        }

        // No local variable table: the variable must be stored once and only used by the pattern
        int storeCount = 0, loadCount = 0;

        for (int i = 0; i < code.length; i = ByteCodeUtil.getNextInstructionOffset(code, i)) {
            if (ByteCodeUtil.getStoredLocalVariableIndex(code, i) == index) {
                storeCount++;
            } else if (getLoadedLocalVariableIndex(code, i) == index) {
                loadCount++;
            }
        }

        return (storeCount == 1) && (loadCount == 2);
    }

    /**
     * @return the offsets of the stores belonging to type patterns, which must not split the conditions
     */
    public static BitSet searchPatternStores(Method method, byte[] code) {
        BitSet stores = null;
        int previousOffset = -1, previousPreviousOffset = -1;

        for (int offset = 0; offset < code.length; offset = ByteCodeUtil.getNextInstructionOffset(code, offset)) {
            if (((code[offset] & 255) == 193) && (previousOffset != -1)) { // INSTANCEOF
                int previousOpcode = code[previousOffset] & 255;
                int operandIndex = ((previousOpcode == 25) || ((previousOpcode >= 42) && (previousOpcode <= 45))) ? getLoadedLocalVariableIndex(code, previousOffset) : -1;

                if (operandIndex != -1) {
                    int bindingOffset = searchBindingOffset(code, offset, operandIndex);

                    if ((bindingOffset != -1) && isBindingVariable(method, code, bindingOffset)) {
                        if (stores == null) {
                            stores = new BitSet(code.length);
                        }

                        stores.set(getBindingStoreOffset(code, bindingOffset));

                        if ((previousPreviousOffset != -1) && (ByteCodeUtil.getStoredLocalVariableIndex(code, previousPreviousOffset) == operandIndex) &&
                                ((code[previousPreviousOffset] & 255) != 132) && isInlinableOperandVariable(method, code, operandIndex, previousOffset)) {
                            // Synthetic variable holding the operand
                            stores.set(previousPreviousOffset);
                        }
                    }
                }
            }

            previousPreviousOffset = previousOffset;
            previousOffset = offset;
        }

        return stores;
    }

    private static int getLoadedLocalVariableIndex(byte[] code, int offset) {
        int opcode = code[offset] & 255;

        if ((opcode >= 21) && (opcode <= 25)) { // ILOAD ... ALOAD
            return code[offset+1] & 255;
        } else if ((opcode >= 26) && (opcode <= 45)) { // ILOAD_0 ... ALOAD_3
            return (opcode - 26) & 3;
        } else if (opcode == 196) { // WIDE
            int wideOpcode = code[offset+1] & 255;
            if ((wideOpcode >= 21) && (wideOpcode <= 25)) {
                return ((code[offset+2] & 255) << 8) | (code[offset+3] & 255);
            }
        }

        return -1;
    }
}
