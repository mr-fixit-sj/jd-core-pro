package org.jd.core.test.java17;
import java.time.DayOfWeek;
import java.util.*;
import java.util.function.*;
public class SwitchExpressions {
    enum Color { RED, GREEN, BLUE }
    public static String s1(int i) { return switch (i) { case 1 -> "one"; case 2, 3 -> "few"; default -> "many"; }; }
    public static long s2(int i) { return switch (i) { case 0 -> 1L; case 1 -> i * 10L; default -> -1; }; }
    public static double s3(int i) { return switch (i) { case 0 -> 0.5; default -> i; }; }
    public static char s4(int i) { return switch (i) { case 0 -> 'a'; case 1 -> 'b'; default -> 'z'; }; }
    public static boolean s5(int i) { return switch (i) { case 0, 2, 4 -> true; default -> false; }; }
    public static int s6(String s) {
        return switch (s) {
            case "Aa", "BB" -> 1;   // same hashCode
            case "C" -> 2;
            default -> s.length();
        };
    }
    public static int s7(int i) {
        return switch (i) {
            case 1 -> {
                if (i > 0) {
                    yield 100;
                }
                yield 200;
            }
            case 2 -> {
                int t = 0;
                for (int k = 0; k < 5; k++) t += k;
                yield t;
            }
            default -> {
                System.out.print("");
                yield -i;
            }
        };
    }
    public static String s8(int i) {
        return "v=" + switch (i % 3) { case 0 -> "zero"; case 1 -> "one"; default -> "two"; } + "!";
    }
    public static int s9(int i, int j) {
        return Math.max(switch (i) { case 0 -> 10; default -> 20; }, switch (j) { case 0 -> 15; default -> 5; });
    }
    public static String s10(int i) {
        if (switch (i) { case 1, 2 -> i > 1; default -> false; }) {
            return "yes";
        }
        return "no";
    }
    public static int s11(Color c) { return switch (c) { case RED -> 1; case GREEN -> 2; case BLUE -> 3; }; }
    public static String s12(int i) {
        Supplier<String> sup = () -> switch (i) { case 0 -> "lambda0"; default -> "lambdaN"; };
        return sup.get();
    }
    public static int s13(int i) {
        int r = 0;
        switch (i) {
            case 1 -> r = 1;
            case 2 -> { r = 2; r *= 3; }
            case 3 -> throw new IllegalArgumentException("three");
            default -> { }
        }
        return r;
    }
    public static String s14(Integer boxed) {
        return switch (boxed) { case 1 -> "I1"; default -> "I?"; };
    }
    public static int s15(int i) {
        return i > 5 ? switch (i) { case 6 -> 60; default -> 70; } : -1;
    }
    public static int s17(int i) {
        int k = switch (i) { case 0 -> throw new IllegalStateException(); default -> i + 1; };
        return k * 2;
    }
    public static String s18(DayOfWeek d) {
        return switch (d) {
            case SATURDAY, SUNDAY -> "weekend";
            default -> "weekday";
        };
    }
}
