package org.bbnors.lab;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;

/** Minimal JSON writer (no dependency) for exports and benchmark results. */
public final class Json {

    private Json() {
    }

    public static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        write(sb, v, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object v, int indent) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            sb.append('"');
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            sb.append('"');
        } else if (v instanceof Double d) {
            sb.append(d.isNaN() || d.isInfinite() ? "null" : trim(d));
        } else if (v instanceof Float f) {
            sb.append(trim(f.doubleValue()));
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(sb, indent + 1);
                write(sb, String.valueOf(e.getKey()), indent + 1);
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
                sb.append(++i < m.size() ? ",\n" : "\n");
            }
            pad(sb, indent);
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            int i = 0;
            for (Object o : c) {
                write(sb, o, indent + 1);
                if (++i < c.size()) sb.append(", ");
            }
            sb.append(']');
        } else if (v instanceof double[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(trim(a[i]));
            }
            sb.append(']');
        } else {
            write(sb, v.toString(), indent);
        }
    }

    private static String trim(double d) {
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return Long.toString((long) d);
        return String.format(Locale.ROOT, "%.6g", d);
    }

    private static void pad(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
    }
}
