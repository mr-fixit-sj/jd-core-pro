package org.jd.core.test.java17;

import java.util.*;

public class Patterns {
    public static int len(Object o) {
        if (o instanceof String s) {
            return s.length();
        }
        return -1;
    }
    public static boolean nonEmpty(Object o) {
        return o instanceof String s && !s.isEmpty();
    }
    public static int neg(Object o) {
        if (!(o instanceof Integer i)) {
            return 0;
        }
        return i + 1;
    }
    public static int ternary(Object o) {
        return o instanceof String s ? s.length() : 0;
    }
    public static boolean or(Object o) {
        return !(o instanceof String s) || s.isEmpty();
    }
    public static int loop(List<Object> l) {
        int n = 0;
        Iterator<Object> it = l.iterator();
        while (it.hasNext() && it.next() instanceof Integer i && i > 0) {
            n += i;
        }
        return n;
    }
    public static String ifAnd(Object o, int k) {
        if (k > 0 && o instanceof CharSequence cs && cs.length() > k) {
            return cs.toString();
        }
        return null;
    }
    public static String ifElse(Object o) {
        if (o instanceof Number n) {
            return "number " + n.intValue();
        } else if (o instanceof String s) {
            return "string " + s.trim();
        }
        return "other";
    }
    public static String concat(int lo, int hi, boolean b) {
        return lo + ".." + hi + " " + (lo == hi) + " " + b + (lo + hi) + 'c';
    }
}
