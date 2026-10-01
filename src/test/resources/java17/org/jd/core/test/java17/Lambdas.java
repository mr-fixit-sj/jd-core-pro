package org.jd.core.test.java17;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;
public class Lambdas {
    int field = 7;
    public static String t1(int i) {
        List<Integer> list = new ArrayList<>(List.of(3, 1, 2));
        String prefix = "p" + i;
        int k = i * 2;
        Function<Integer, String> f = x -> prefix + (x + k);
        StringBuilder sb = new StringBuilder();
        for (Integer x : list) sb.append(f.apply(x)).append(",");
        return sb.toString();
    }
    public static String t2(int i) {
        int[] counter = { 0 };
        Runnable r = () -> counter[0] += i;
        r.run(); r.run();
        return "" + counter[0];
    }
    public static String t3(String s) {
        Supplier<String> sup = () -> { String t = s.trim(); return t.isEmpty() ? "empty" : t.toUpperCase(); };
        BiFunction<String, Integer, String> bf = (a, n) -> a.repeat(Math.max(0, n));
        return sup.get() + bf.apply(s, 2);
    }
    public static String t4(int i) {
        Lambdas l = new Lambdas();
        IntSupplier s = () -> l.field + i;
        return "" + s.getAsInt();
    }
    public static String t5(int i) {
        var map = new TreeMap<String, Integer>();
        for (int k = 0; k < 3; k++) {
            final int kk = k;
            map.computeIfAbsent("k" + k, key -> kk + i + key.hashCode());
        }
        return map.toString();
    }
}
