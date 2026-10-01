/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.model.javasyntax.expression;

import org.jd.core.v1.model.javasyntax.statement.SwitchStatement;
import org.jd.core.v1.model.javasyntax.type.Type;

import java.util.List;

/**
 * "switch (condition) { case ... -> ...; }" used as an expression (Java 14+). Each block ends with a 'yield' or a
 * 'throw' statement: there is no fall-through.
 */
public class SwitchExpression extends AbstractLineNumberTypeExpression {
    protected Expression condition;
    protected List<SwitchStatement.Block> blocks;

    public SwitchExpression(int lineNumber, Type type, Expression condition, List<SwitchStatement.Block> blocks) {
        super(lineNumber, type);
        this.condition = condition;
        this.blocks = blocks;
    }

    @Override
    public Expression getCondition() {
        return condition;
    }

    public void setCondition(Expression condition) {
        this.condition = condition;
    }

    public List<SwitchStatement.Block> getBlocks() {
        return blocks;
    }

    @Override
    public int getPriority() {
        return 15;
    }

    @Override
    public boolean isSwitchExpression() { return true; }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visit(this);
    }

    @Override
    public String toString() {
        return "SwitchExpression{switch (" + condition + ") " + blocks + "}";
    }
}
