package org.jd.core.test.java17;
import java.util.*;
public class Records {
    public record Point(int x, int y) {
        public static final Point ORIGIN = new Point(0, 0);
        public static Point of(int v) { return new Point(v, v); }
        public double dist() { return Math.sqrt(x * x + y * y); }
    }
    public record Norm(int a, int b) {
        public Norm {
            if (a > b) { int t = a; a = b; b = t; }
        }
    }
    public record Explicit(String s, long n) {
        public Explicit(String s, long n) {
            this.s = s.trim();
            this.n = Math.abs(n);
        }
    }
    public record Custom(String name, int[] data) {
        public String name() { return name.toUpperCase(); }
        @Override public boolean equals(Object o) {
            return o instanceof Custom c && c.name.equals(name) && Arrays.equals(c.data, data);
        }
        @Override public int hashCode() { return name.hashCode() * 31 + Arrays.hashCode(data); }
    }
    public record Box<T extends Comparable<T>>(T value, List<T> more) {
        public T first() {
            return more.isEmpty() ? value : more.get(0);
        }
    }
    public record Var(String label, int... values) {
        public int sum() { int s = 0; for (int v : values) s += v; return s; }
    }
    public record Empty() {}
    public sealed interface Expr permits Num, Add, Neg {}
    public record Num(int v) implements Expr {}
    public record Add(Expr l, Expr r) implements Expr {}
    public static final class Neg implements Expr { final Expr e; Neg(Expr e) { this.e = e; } }

    static int eval(Expr e) {
        if (e instanceof Num n) return n.v();
        if (e instanceof Add a) return eval(a.l()) + eval(a.r());
        if (e instanceof Neg ng) return -eval(ng.e);
        throw new IllegalStateException();
    }

    public static String t1(int i) {
        Point p = new Point(i, i + 1);
        return p + " " + p.x() + " " + p.y() + " " + p.equals(new Point(i, i + 1)) + " " + (p.hashCode() == new Point(i, i + 1).hashCode()) + " " + Point.ORIGIN + " " + Point.of(i) + " " + p.dist();
    }
    public static String t2(int i) {
        Norm n = new Norm(i, 3);
        return n.toString() + n.a() + n.b();
    }
    public static String t3(String s) {
        try {
            Explicit e = new Explicit(" " + s + " ", -s.length());
            return e.toString();
        } catch (RuntimeException ex) { return ex.getClass().getName(); }
    }
    public static String t4(String s) {
        Custom c = new Custom(s, new int[] { s.length() });
        return c.name() + c.equals(new Custom(s, new int[] { s.length() })) + c.hashCode() + c.data().length;
    }
    public static String t5(int i) {
        Box<Integer> b = new Box<>(i, List.of(3, 7, -1));
        return b.first() + " " + b;
    }
    public static String t6(int i) {
        Var v = new Var("v", i, i, 1);
        Var w = new Var("w");
        return v.sum() + " " + w.sum() + " " + v.values().length + new Empty() + new Empty().equals(new Empty());
    }
    public static String t7(int i) {
        Expr e = new Add(new Num(i), new Neg(new Add(new Num(2), new Num(i * 3))));
        return eval(e) + " " + new Add(new Num(1), new Num(i));
    }
    public static String t8(int i) {
        record Local(int k, String s) {
            Local { s = s + k; }
        }
        Local l = new Local(i, "L");
        return l.toString() + l.s();
    }
}
