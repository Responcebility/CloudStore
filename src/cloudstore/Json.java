/*
 * File:    Json.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: A small JSON writer so the API can answer the browser without an
 *          external library. Supports maps, lists, strings, numbers,
 *          booleans and null.
 */
package cloudstore;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Json {

    private Json() {
    }

    /** Builds an ordered map from alternating key/value arguments. */
    public static Map<String, Object> map(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Json.map needs key/value pairs");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            result.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return result;
    }

    /** Serialises any supported value to JSON text. */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder();
        append(out, value);
        return out.toString();
    }

    private static void append(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            appendString(out, text);
        } else if (value instanceof Double number) {
            // NaN and Infinity are not valid JSON, so they become null.
            out.append(number.isNaN() || number.isInfinite() ? "null" : number.toString());
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) out.append(',');
                appendString(out, String.valueOf(entry.getKey()));
                out.append(':');
                append(out, entry.getValue());
                first = false;
            }
            out.append('}');
        } else if (value instanceof Collection<?> list) {
            out.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) out.append(',');
                append(out, item);
                first = false;
            }
            out.append(']');
        } else {
            appendString(out, value.toString());
        }
    }

    private static void appendString(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                // Escaping < and > keeps JSON safe even if it ends up inside HTML.
                case '<' -> out.append("\\u003c");
                case '>' -> out.append("\\u003e");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
