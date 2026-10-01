/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1.service.converter.classfiletojavasyntax.util;

import org.jd.core.v1.model.javasyntax.expression.BinaryOperatorExpression;
import org.jd.core.v1.model.javasyntax.expression.Expression;
import org.jd.core.v1.model.javasyntax.expression.MethodInvocationExpression;
import org.jd.core.v1.model.javasyntax.expression.NullExpression;
import org.jd.core.v1.model.javasyntax.statement.*;
import org.jd.core.v1.model.javasyntax.type.ObjectType;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.expression.ClassFileLocalVariableReferenceExpression;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.expression.ClassFileMethodInvocationExpression;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.statement.ClassFileBreakContinueStatement;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.javasyntax.statement.ClassFileTryStatement;
import org.jd.core.v1.service.converter.classfiletojavasyntax.model.localvariable.AbstractLocalVariable;
import org.jd.core.v1.service.converter.classfiletojavasyntax.visitor.SearchLocalVariableReferenceVisitor;
import org.jd.core.v1.util.DefaultList;

import java.util.List;

public class TryWithResourcesStatementMaker {

    public static Statement make(
            LocalVariableMaker localVariableMaker, Statements statements, Statements tryStatements,
            DefaultList<TryStatement.CatchClause> catchClauses, Statements finallyStatements) {
        int size = statements.size();

        if ((finallyStatements == null) && (size >= 1) && checkThrowable(catchClauses)) {
            return parsePatternJdk11(localVariableMaker, statements, tryStatements, catchClauses.getFirst());
        }

        if ((size < 2) || (finallyStatements == null) || (finallyStatements.size() != 1) || !checkThrowable(catchClauses)) {
            return null;
        }

        Statement statement = statements.get(size - 2);

        if (!statement.isExpressionStatement()) {
            return null;
        }

        Expression expression = statement.getExpression();

        if (!expression.isBinaryOperatorExpression()) {
            return null;
        }

        Expression boe = expression;

        expression = boe.getLeftExpression();

        if (!expression.isLocalVariableReferenceExpression()) {
            return null;
        }

        AbstractLocalVariable lv1 = ((ClassFileLocalVariableReferenceExpression) expression).getLocalVariable();

        statement = statements.get(size - 1);

        if (!statement.isExpressionStatement()) {
            return null;
        }

        expression = statement.getExpression();

        if (!expression.isBinaryOperatorExpression()) {
            return null;
        }

        expression = expression.getLeftExpression();

        if (!expression.isLocalVariableReferenceExpression()) {
            return null;
        }

        AbstractLocalVariable lv2 = ((ClassFileLocalVariableReferenceExpression) expression).getLocalVariable();

        statement = finallyStatements.getFirst();

        if (statement.isIfStatement() && (lv1 == getLocalVariable(statement.getCondition()))) {
            statement = statement.getStatements().getFirst();

            if (statement.isIfElseStatement()) {
                return parsePatternAddSuppressed(localVariableMaker, statements, tryStatements, finallyStatements, boe, lv1, lv2, statement);
            }
            if (statement.isExpressionStatement()) {
                return parsePatternCloseResource(localVariableMaker, statements, tryStatements, finallyStatements, boe, lv1, lv2, statement);
            }
        }

        if (statement.isExpressionStatement()) {
            return parsePatternCloseResource(localVariableMaker, statements, tryStatements, finallyStatements, boe, lv1, lv2, statement);
        }

        return null;
    }

    protected static Statement parsePatternAddSuppressed(
            LocalVariableMaker localVariableMaker, Statements statements, Statements tryStatements,
            Statements finallyStatements, Expression boe, AbstractLocalVariable lv1, AbstractLocalVariable lv2,
            Statement statement) {
        if (!statement.isIfElseStatement()) {
            return null;
        }

        Statement ies = statement;

        statement = ies.getStatements().getFirst();

        if (!statement.isTryStatement()) {
            return null;
        }

        Statement ts = statement;

        statement = ies.getElseStatements().getFirst();

        if (!statement.isExpressionStatement()) {
            return null;
        }

        Expression expression = statement.getExpression();

        if (!expression.isMethodInvocationExpression()) {
            return null;
        }

        MethodInvocationExpression mie = (MethodInvocationExpression) expression;

        if ((ts.getFinallyStatements() != null) || (lv2 != getLocalVariable(ies.getCondition())) ||
                !checkThrowable(ts.getCatchClauses()) || !checkCloseInvocation(mie, lv1)) {
            return null;
        }

        statement = ts.getTryStatements().getFirst();

        if (!statement.isExpressionStatement()) {
            return null;
        }

        expression = statement.getExpression();

        if (!expression.isMethodInvocationExpression()) {
            return null;
        }

        mie = (MethodInvocationExpression) expression;

        if (!checkCloseInvocation(mie, lv1)) {
            return null;
        }

        statement = ts.getCatchClauses().getFirst().getStatements().getFirst();

        if (!statement.isExpressionStatement()) {
            return null;
        }

        expression = statement.getExpression();

        if (!expression.isMethodInvocationExpression()) {
            return null;
        }

        mie = (MethodInvocationExpression) expression;

        if (!mie.getName().equals("addSuppressed") || !mie.getDescriptor().equals("(Ljava/lang/Throwable;)V")) {
            return null;
        }

        expression = mie.getExpression();

        if (!expression.isLocalVariableReferenceExpression()) {
            return null;
        }
        if (((ClassFileLocalVariableReferenceExpression) expression).getLocalVariable() != lv2) {
            return null;
        }

        return newTryStatement(localVariableMaker, statements, tryStatements, finallyStatements, boe, lv1, lv2);
    }

    /**
     * javac 11+:
     * <pre>
     * r = init;
     * try {
     *     ...
     *     r.close();                     // or "if (r != null) r.close();", also before each 'return', 'break', ...
     * } catch (Throwable t) {
     *     if (r != null) {               // optional
     *         try {
     *             r.close();
     *         } catch (Throwable t2) {
     *             t.addSuppressed(t2);
     *         }
     *     }
     *     throw t;
     * }
     * </pre>
     */
    protected static Statement parsePatternJdk11(
            LocalVariableMaker localVariableMaker, Statements statements, Statements tryStatements, TryStatement.CatchClause catchClause) {
        // Resource
        Statement statement = statements.getLast();
        Expression boe = statement.getExpression();

        if (!statement.isExpressionStatement() || !boe.isBinaryOperatorExpression() || !"=".equals(boe.getOperator()) ||
                !boe.getLeftExpression().isLocalVariableReferenceExpression() || !(boe.getLeftExpression() instanceof ClassFileLocalVariableReferenceExpression)) {
            return null;
        }

        AbstractLocalVariable resource = ((ClassFileLocalVariableReferenceExpression) boe.getLeftExpression()).getLocalVariable();

        if (!resource.getType().isObjectType()) {
            return null;
        }

        // Catch clause
        if (!(catchClause instanceof ClassFileTryStatement.CatchClause)) {
            return null;
        }

        AbstractLocalVariable throwable = ((ClassFileTryStatement.CatchClause) catchClause).getLocalVariable();
        BaseStatement catchStatements = catchClause.getStatements();

        if ((catchStatements == null) || (catchStatements.size() != 2)) {
            return null;
        }

        statement = catchStatements.getLast();

        if (!statement.isThrowStatement() || (getLocalVariableOf(statement.getExpression()) != throwable)) {
            return null;
        }

        statement = catchStatements.getFirst();

        if (statement.isIfStatement()) {
            if ((getLocalVariable(statement.getCondition()) != resource) || (statement.getStatements().size() != 1)) {
                return null;
            }
            statement = statement.getStatements().getFirst();
        }

        if (!checkCloseAndAddSuppressed(statement, resource, throwable)) {
            return null;
        }

        // Remove the 'close()' invocations added by javac
        if (!removeCloseInvocations(tryStatements, resource)) {
            return null;
        }

        statements.removeLast();
        resource.setDeclared(true);

        DefaultList<TryStatement.Resource> resources = new DefaultList<>();
        Expression init = boe.getRightExpression();
        SearchLocalVariableReferenceVisitor visitor = new SearchLocalVariableReferenceVisitor();

        visitor.init(resource.getIndex());
        tryStatements.accept(visitor);

        if ((init instanceof ClassFileLocalVariableReferenceExpression) && !visitor.containsReference()) {
            // Java 9+: "try (variable) { ... }", javac copies the variable into a synthetic one
            localVariableMaker.removeLocalVariable(resource);
            resources.add(new TryStatement.Resource(null, null, init));
        } else {
            resources.add(new ClassFileTryStatement.ClassFileResource((ObjectType) resource.getType(), resource, init));
        }

        return new ClassFileTryStatement(resources, tryStatements, null, null, false, false);
    }

    /**
     * "try { r.close(); } catch (Throwable t2) { t.addSuppressed(t2); }"
     */
    protected static boolean checkCloseAndAddSuppressed(Statement statement, AbstractLocalVariable resource, AbstractLocalVariable throwable) {
        if (!statement.isTryStatement() || (statement.getFinallyStatements() != null) || !checkThrowable(statement.getCatchClauses())) {
            return false;
        }

        BaseStatement tryStatements = statement.getTryStatements();

        if ((tryStatements.size() != 1) || !isCloseInvocation(tryStatements.getFirst(), resource)) {
            return false;
        }

        BaseStatement catchStatements = statement.getCatchClauses().getFirst().getStatements();

        if ((catchStatements == null) || (catchStatements.size() != 1) || !catchStatements.getFirst().isExpressionStatement()) {
            return false;
        }

        Expression expression = catchStatements.getFirst().getExpression();

        if (!expression.isMethodInvocationExpression()) {
            return false;
        }

        MethodInvocationExpression mie = (MethodInvocationExpression) expression;

        return mie.getName().equals("addSuppressed") && mie.getDescriptor().equals("(Ljava/lang/Throwable;)V") &&
                (getLocalVariableOf(mie.getExpression()) == throwable);
    }

    /**
     * "r.close();" or "if (r != null) r.close();"
     */
    protected static boolean isCloseInvocation(Statement statement, AbstractLocalVariable resource) {
        if (statement.isIfStatement()) {
            if ((getLocalVariable(statement.getCondition()) != resource) || (statement.getStatements().size() != 1)) {
                return false;
            }
            statement = statement.getStatements().getFirst();
        }

        if (!statement.isExpressionStatement() || !statement.getExpression().isMethodInvocationExpression()) {
            return false;
        }

        return checkCloseInvocation((MethodInvocationExpression) statement.getExpression(), resource);
    }

    /**
     * Remove the invocations of 'close()' javac adds before the jumps out of the block and at the end of the paths
     * completing the block normally. Nothing is removed if the block does not match this pattern.
     *
     * @return false if the pattern does not match
     */
    protected static boolean removeCloseInvocations(Statements tryStatements, AbstractLocalVariable resource) {
        DefaultList<Statements> lists = new DefaultList<>();
        DefaultList<Statement> invocations = new DefaultList<>();

        searchCloseInvocations(tryStatements, resource, true, lists, invocations);

        if (invocations.isEmpty() && !tryStatements.isEmpty() && !cannotCompleteNormally(tryStatements.getLast())) {
            return false;
        }

        for (int i = 0, len = lists.size(); i < len; i++) {
            lists.get(i).remove(invocations.get(i));
        }

        return true;
    }

    /**
     * @param tail true if the end of 'baseStatements' is the end of the try-with-resources block
     */
    protected static void searchCloseInvocations(BaseStatement baseStatements, AbstractLocalVariable resource, boolean tail,
                                                 DefaultList<Statements> lists, DefaultList<Statement> invocations) {
        if ((baseStatements == null) || !baseStatements.isStatements()) {
            return;
        }

        Statements statements = (Statements) baseStatements;
        int size = statements.size();

        for (int i = 0; i < size; i++) {
            Statement statement = statements.get(i);

            if (isCloseInvocation(statement, resource)) {
                // Skip the invocations of 'close()' on the other resources
                int j = i + 1;

                while ((j < size) && isCloseInvocation(statements.get(j))) {
                    j++;
                }

                if ((j == size) ? tail : isJump(statements.get(j))) {
                    lists.add(statements);
                    invocations.add(statement);
                }
            } else {
                boolean last = tail && (i == size - 1);

                if (statement.isIfStatement() || statement.isIfElseStatement()) {
                    searchCloseInvocations(statement.getStatements(), resource, last, lists, invocations);
                    searchCloseInvocations(statement.getElseStatements(), resource, last, lists, invocations);
                } else if (statement.isWhileStatement() || statement.isForStatement() || (statement instanceof DoWhileStatement) || (statement instanceof ForEachStatement)) {
                    searchCloseInvocations(statement.getStatements(), resource, false, lists, invocations);
                } else if (statement.isSwitchStatement()) {
                    for (SwitchStatement.Block block : ((SwitchStatement) statement).getBlocks()) {
                        searchCloseInvocations(block.getStatements(), resource, false, lists, invocations);
                    }
                } else if (statement.isTryStatement()) {
                    searchCloseInvocations(statement.getTryStatements(), resource, false, lists, invocations);
                    if (statement.getCatchClauses() != null) {
                        for (TryStatement.CatchClause cc : statement.getCatchClauses()) {
                            searchCloseInvocations(cc.getStatements(), resource, false, lists, invocations);
                        }
                    }
                }
            }
        }
    }

    protected static boolean isJump(Statement statement) {
        return statement.isReturnStatement() || statement.isReturnExpressionStatement() || statement.isBreakStatement() ||
                statement.isContinueStatement() || statement.isThrowStatement() || (statement instanceof ClassFileBreakContinueStatement);
    }

    protected static boolean cannotCompleteNormally(Statement statement) {
        if (statement.isReturnStatement() || statement.isReturnExpressionStatement() || statement.isThrowStatement()) {
            return true;
        } else if (statement.isIfElseStatement()) {
            return cannotCompleteNormally(statement.getStatements()) && cannotCompleteNormally(statement.getElseStatements());
        }
        return false;
    }

    protected static boolean cannotCompleteNormally(BaseStatement statements) {
        return (statements != null) && (statements.size() > 0) && cannotCompleteNormally(statements.getLast());
    }

    /**
     * "x.close();" or "if (x != null) x.close();" on any local variable
     */
    protected static boolean isCloseInvocation(Statement statement) {
        if (statement.isIfStatement()) {
            if (statement.getStatements().size() != 1) {
                return false;
            }
            statement = statement.getStatements().getFirst();
        }

        if (!statement.isExpressionStatement() || !statement.getExpression().isMethodInvocationExpression()) {
            return false;
        }

        MethodInvocationExpression mie = (MethodInvocationExpression) statement.getExpression();

        return mie.getName().equals("close") && mie.getDescriptor().equals("()V") && mie.getExpression().isLocalVariableReferenceExpression();
    }

    protected static AbstractLocalVariable getLocalVariableOf(Expression expression) {
        if (expression instanceof ClassFileLocalVariableReferenceExpression) {
            return ((ClassFileLocalVariableReferenceExpression) expression).getLocalVariable();
        }
        return null;
    }

    protected static boolean checkThrowable(List<? extends TryStatement.CatchClause> catchClauses) {
        return (catchClauses.size() == 1) && catchClauses.get(0).getType().equals(ObjectType.TYPE_THROWABLE);
    }

    protected static AbstractLocalVariable getLocalVariable(Expression condition) {
        if (!condition.isBinaryOperatorExpression()) {
            return null;
        }

        if (!condition.getOperator().equals("!=") || !condition.getRightExpression().isNullExpression() || !condition.getLeftExpression().isLocalVariableReferenceExpression()) {
            return null;
        }

        return ((ClassFileLocalVariableReferenceExpression) condition.getLeftExpression()).getLocalVariable();
    }

    protected static boolean checkCloseInvocation(MethodInvocationExpression mie, AbstractLocalVariable lv) {
        if (mie.getName().equals("close") && mie.getDescriptor().equals("()V")) {
            Expression expression = mie.getExpression();

            if (expression.isLocalVariableReferenceExpression()) {
                return ((ClassFileLocalVariableReferenceExpression) expression).getLocalVariable() == lv;
            }
        }

        return false;
    }

    protected static Statement parsePatternCloseResource(
            LocalVariableMaker localVariableMaker, Statements statements, Statements tryStatements, Statements finallyStatements,
            Expression boe, AbstractLocalVariable lv1, AbstractLocalVariable lv2, Statement statement) {
        Expression expression = statement.getExpression();

        if (!expression.isMethodInvocationExpression()) {
            return null;
        }

        MethodInvocationExpression mie = (MethodInvocationExpression) expression;

        if (!mie.getName().equals("$closeResource") || !mie.getDescriptor().equals("(Ljava/lang/Throwable;Ljava/lang/AutoCloseable;)V")) {
            return null;
        }

        DefaultList<Expression> parameters = mie.getParameters().getList();
        Expression parameter0 = parameters.getFirst();

        if (!parameter0.isLocalVariableReferenceExpression()) {
            return null;
        }
        if (((ClassFileLocalVariableReferenceExpression)parameter0).getLocalVariable() != lv2) {
            return null;
        }

        Expression parameter1 = parameters.get(1);

        if (!parameter1.isLocalVariableReferenceExpression()) {
            return null;
        }
        if (((ClassFileLocalVariableReferenceExpression)parameter1).getLocalVariable() != lv1) {
            return null;
        }

        return newTryStatement(localVariableMaker, statements, tryStatements, finallyStatements, boe, lv1, lv2);
    }

    protected static ClassFileTryStatement newTryStatement(
            LocalVariableMaker localVariableMaker, Statements statements, Statements tryStatements,
            Statements finallyStatements, Expression boe, AbstractLocalVariable lv1, AbstractLocalVariable lv2) {

        // Remove resource & synthetic local variables
        statements.removeLast();
        statements.removeLast();
        lv1.setDeclared(true);
        localVariableMaker.removeLocalVariable(lv2);

        // Create try-with-resources statement
        DefaultList<TryStatement.Resource> resources = new DefaultList<>();

        resources.add(new ClassFileTryStatement.ClassFileResource((ObjectType) lv1.getType(), lv1, boe.getRightExpression()));

        return new ClassFileTryStatement(resources, tryStatements, null, finallyStatements, false, false);
    }
}
