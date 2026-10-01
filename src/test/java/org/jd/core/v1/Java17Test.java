/*
 * Copyright (c) 2008, 2019 Emmanuel Dupuy.
 * This project is distributed under the GPLv3 license.
 * This is a Copyleft license that gives the user the right to use,
 * copy and modify the code freely for non-commercial purposes.
 */

package org.jd.core.v1;

import junit.framework.TestCase;
import org.jd.core.v1.api.loader.Loader;
import org.jd.core.v1.compiler.CompilerUtil;
import org.jd.core.v1.compiler.JavaSourceFileObject;
import org.jd.core.v1.loader.ZipLoader;
import org.jd.core.v1.printer.PlainTextPrinter;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Java 14 to 17 language features: switch expressions, type patterns, records, sealed classes and try-with-resources
 * compiled by javac 11+.
 *
 * The classes of "zip/data-java-jdk-17.0*.zip" are compiled by javac 17 from "java17/org/jd/core/test/java17", with
 * and without debug information ("-g", "-g:none"). On Java 17+, the decompiled sources are also recompiled and the
 * public static methods of both versions are invoked with the same arguments: the results must be identical.
 */
public class Java17Test extends TestCase {
    protected static final String PACKAGE = "org/jd/core/test/java17/";
    protected static final String[] CLASSES = { "Patterns", "SwitchExpressions", "Records", "TryWithResources", "Lambdas" };

    protected static final boolean JAVA_17 = getJavaVersion() >= 17;

    @Test
    public void testPatterns() throws Exception {
        String source = decompile("/zip/data-java-jdk-17.0.zip", "Patterns");

        assertTrue(source.contains("if (o instanceof String s)"));
        assertTrue(source.contains("return (o instanceof String s && !s.isEmpty());"));
        assertTrue(source.contains("if (!(o instanceof Integer i))"));
        assertTrue(source.contains("return (o instanceof String s) ? s.length() : 0;"));
        assertTrue(source.contains("return (!(o instanceof String s) || s.isEmpty());"));
        assertTrue(source.contains("while (it.hasNext() && it.next() instanceof Integer i && i > 0)"));
        assertTrue(source.contains("if (k > 0 && o instanceof CharSequence cs && cs.length() > k)"));
        assertTrue(source.contains("return lo + \"..\" + hi + \" \" + ((lo == hi)) + \" \" + b + (lo + hi) + \"c\";"));
    }

    @Test
    public void testSwitchExpressions() throws Exception {
        String source = decompile("/zip/data-java-jdk-17.0.zip", "SwitchExpressions");

        assertFalse(source.contains("Byte code:"));
        assertTrue(source.contains("return switch (i) {"));
        assertTrue(source.contains("case 2, 3 -> \"few\";"));
        assertTrue(source.contains("case \"Aa\", \"BB\" -> 1;"));
        assertTrue(source.contains("default -> s.length();"));
        assertTrue(source.contains("yield 100;"));
        assertTrue(source.contains("case 0 -> 'a';"));
        assertTrue(source.contains("case 0, 2, 4 -> true;"));
        assertTrue(source.contains("case RED -> 1;"));
        assertFalse(source.contains("IncompatibleClassChangeError"));
        assertTrue(source.contains("case SATURDAY, SUNDAY -> \"weekend\";"));
        assertTrue(source.contains("case 0 -> throw new IllegalStateException();") || source.contains("throw new IllegalStateException();"));
    }

    @Test
    public void testRecords() throws Exception {
        String source = decompile("/zip/data-java-jdk-17.0.zip", "Records");

        assertTrue(source.contains("public record Point(int x, int y) {"));
        assertTrue(source.contains("public record Norm(int a, int b) {"));
        assertTrue(source.contains("public Norm {"));
        assertTrue(source.contains("public Explicit(String s, long n) {"));
        assertTrue(source.contains("public record Box<T extends Comparable<T>>(T value, List<T> more) {"));
        assertTrue(source.contains("public record Var(String label, int... values) {"));
        assertTrue(source.contains("public record Empty() {}"));
        assertTrue(source.contains("public static sealed interface Expr permits Num, Add, Neg {}"));
        assertTrue(source.contains("record Local(int k, String s) {"));
        assertFalse(source.contains("ObjectMethods"));
        assertFalse(source.contains("extends Record"));
        assertFalse(source.contains("Byte code:"));
    }

    @Test
    public void testTryWithResources() throws Exception {
        String source = decompile("/zip/data-java-jdk-17.0.zip", "TryWithResources");

        assertFalse(source.contains("addSuppressed"));
        assertTrue(source.contains("try (Res r = new Res(\"a\", log, (i == 2))) {"));
        assertTrue(source.contains("try(Res a = new Res(\"a\", log, (i == 3)); Res b = new Res(\"b\", log, (i == 4))) {"));
        assertTrue(source.contains("try (maybe) {"));
        assertFalse(source.contains("Byte code:"));
    }

    @Test
    public void testLambdasWithoutDebugInformation() throws Exception {
        String source = decompile("/zip/data-java-jdk-17.0-no-debug-info.zip", "Lambdas");

        assertFalse(source.contains("paramList"));
        assertTrue(source.contains("(paramString2, paramInteger) ->"));
    }

    @Test
    public void testRecompileAndCompareBehavior() throws Exception {
        if (JAVA_17) {
            checkBehavior("/zip/data-java-jdk-17.0.zip", CLASSES);
        }
    }

    @Test
    public void testRecompileAndCompareBehaviorWithoutDebugInformation() throws Exception {
        if (JAVA_17) {
            // Without local variable type table, generic types of local variables are lost: Lambdas does not recompile
            checkBehavior("/zip/data-java-jdk-17.0-no-debug-info.zip", "Patterns", "SwitchExpressions", "Records", "TryWithResources");
        }
    }

    protected void checkBehavior(String zip, String... classNames) throws Exception {
        for (String className : classNames) {
            String source = decompile(zip, className);
            assertTrue(className + " does not recompile", CompilerUtil.compile("17", new JavaSourceFileObject(PACKAGE + className, source)));
        }

        Loader loader = newLoader(zip);
        ClassLoader original = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                try {
                    byte[] data = loader.load(name.replace('.', '/'));
                    if (data == null) {
                        throw new ClassNotFoundException(name);
                    }
                    return defineClass(name, data, 0, data.length);
                } catch (ClassNotFoundException e) {
                    throw e;
                } catch (Exception e) {
                    throw new ClassNotFoundException(name, e);
                }
            }
        };
        ClassLoader recompiled = new URLClassLoader(new URL[] { new File("build/test-recompiled").toURI().toURL() }, getClass().getClassLoader());

        for (String className : classNames) {
            String name = PACKAGE.replace('/', '.') + className;
            List<String> expected = invokeAll(original.loadClass(name));
            List<String> actual = invokeAll(recompiled.loadClass(name));

            assertFalse(expected.isEmpty());
            assertEquals(className, expected, actual);
        }
    }

    protected static List<String> invokeAll(Class<?> type) throws Exception {
        List<String> results = new ArrayList<>();
        Method[] methods = type.getDeclaredMethods();

        Arrays.sort(methods, Comparator.comparing(Method::toString));

        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
                for (Object[] arguments : arguments(method.getParameterTypes(), method.getParameterTypes().length)) {
                    String result;

                    try {
                        result = String.valueOf(method.invoke(null, arguments));
                    } catch (InvocationTargetException e) {
                        result = "exception " + e.getCause().getClass().getName();
                    }

                    results.add(method.getName() + Arrays.deepToString(arguments) + " = " + result);
                }
            }
        }

        return results;
    }

    protected static List<Object[]> arguments(Class<?>[] types, int count) {
        List<Object[]> list = new ArrayList<>();

        if (count == 0) {
            list.add(new Object[0]);
        } else {
            for (Object[] prefix : arguments(types, count - 1)) {
                for (Object value : values(types[count - 1])) {
                    Object[] arguments = Arrays.copyOf(prefix, count);
                    arguments[count - 1] = value;
                    list.add(arguments);
                }
            }
        }

        return list;
    }

    protected static Object[] values(Class<?> type) {
        if ((type == int.class) || (type == Integer.class)) {
            return new Object[] { -1, 0, 1, 2, 3, 4, 5, 6, 7, 10 };
        } else if (type == boolean.class) {
            return new Object[] { true, false };
        } else if (type == String.class) {
            return new Object[] { "", "a", "x", "y", "z", "Aa", "BB", "C", "hello", "123" };
        } else if (type == Object.class) {
            return new Object[] { null, "s", "", 1, 0, -3, 2.5, new StringBuilder("sb") };
        } else if (type == List.class) {
            return new Object[] { Arrays.asList(1, 2, -3, "s"), Collections.emptyList() };
        } else if (type.isEnum()) {
            return Arrays.copyOf(type.getEnumConstants(), type.getEnumConstants().length + 1);
        }

        return new Object[] { null };
    }

    protected String decompile(String zip, String className) throws Exception {
        PlainTextPrinter printer = new PlainTextPrinter();

        new ClassFileToJavaSourceDecompiler().decompile(newLoader(zip), printer, PACKAGE + className, Collections.singletonMap("realignLineNumbers", Boolean.FALSE));

        return printer.toString();
    }

    protected Loader newLoader(String zip) throws Exception {
        try (InputStream is = this.getClass().getResourceAsStream(zip)) {
            return new ZipLoader(is);
        }
    }

    protected static int getJavaVersion() {
        String version = System.getProperty("java.version");

        if (version.startsWith("1.")) {
            return Integer.parseInt(version.substring(2, 3));
        }

        int index = version.indexOf('.');

        return Integer.parseInt((index == -1) ? version.replaceAll("[^0-9].*", "") : version.substring(0, index));
    }
}
