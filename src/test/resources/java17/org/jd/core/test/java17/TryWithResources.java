package org.jd.core.test.java17;
import java.util.*;
public class TryWithResources {
    static class Res implements AutoCloseable {
        final String n; final List<String> log; final boolean failClose;
        Res(String n, List<String> log, boolean failClose) { this.n = n; this.log = log; this.failClose = failClose; log.add("open " + n); }
        public void close() { log.add("close " + n); if (failClose) throw new IllegalStateException("close " + n); }
    }
    static String run(List<String> log, Throwable t) {
        return log + (t == null ? "" : " ! " + t.getMessage() + Arrays.toString(Arrays.stream(t.getSuppressed()).map(Throwable::getMessage).toArray()));
    }
    public static String t1(int i) {
        List<String> log = new ArrayList<>();
        try (Res r = new Res("a", log, i == 2)) {
            log.add("body " + r.n);
            if (i == 1) throw new RuntimeException("body");
        } catch (RuntimeException e) {
            return run(log, e);
        }
        return run(log, null);
    }
    public static String t2(int i) {
        List<String> log = new ArrayList<>();
        try {
            return f2(i, log) + run(log, null);
        } catch (RuntimeException e) { return run(log, e); }
    }
    static int f2(int i, List<String> log) {
        try (Res a = new Res("a", log, i == 3); Res b = new Res("b", log, i == 4)) {
            if (i == 0) return 10;
            if (i == 1) throw new RuntimeException("body1");
            log.add("mid");
            return i * 2;
        }
    }
    public static String t3(int i) {
        List<String> log = new ArrayList<>();
        int n = 0;
        for (int k = 0; k < 4; k++) {
            try (Res r = new Res("r" + k, log, false)) {
                if (k == i) break;
                if (k % 2 == 1) continue;
                n += k;
            }
        }
        return n + run(log, null);
    }
    public static String t4(int i) {
        List<String> log = new ArrayList<>();
        Res maybe = i > 2 ? null : new Res("m", log, false);
        try (maybe) {
            log.add("in " + (maybe == null));
        }
        return run(log, null);
    }
    public static String t5(int i) {
        List<String> log = new ArrayList<>();
        try (Res r = i == 0 ? null : new Res("x", log, false)) {
            log.add("null? " + (r == null));
        } finally {
            log.add("finally");
        }
        return run(log, null);
    }
    public static String t6(int i) {
        List<String> log = new ArrayList<>();
        try {
            try (Res r = new Res("o", log, i == 1)) {
                try (Res s = new Res("i", log, i == 2)) {
                    if (i == 3) throw new IllegalArgumentException("deep");
                    log.add("deep body");
                }
            }
        } catch (RuntimeException e) {
            return run(log, e);
        }
        return run(log, null);
    }
}
