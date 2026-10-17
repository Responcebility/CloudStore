/*
 * File:    Http.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Helpers for reading requests (query strings, form bodies, cookies)
 *          and writing JSON responses with sensible security headers.
 */
package cloudstore;

import com.sun.net.httpserver.HttpExchange;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public final class Http {

    /** Bodies larger than this are rejected so one request cannot exhaust memory. */
    private static final int MAX_BODY_BYTES = 64 * 1024;

    private Http() {
    }

    /** Parses "a=1&b=two" into a map, URL-decoding keys and values. */
    public static Map<String, String> parseForm(String encoded) {
        Map<String, String> result = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) {
            return result;
        }
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) continue;
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            result.put(URLDecoder.decode(key, StandardCharsets.UTF_8), URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
        return result;
    }

    public static Map<String, String> query(HttpExchange exchange) {
        return parseForm(exchange.getRequestURI().getRawQuery());
    }

    /** Reads an application/x-www-form-urlencoded request body. */
    public static Map<String, String> body(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
                if (buffer.size() > MAX_BODY_BYTES) {
                    throw new ApiException(413, "That request is too large.");
                }
            }
            return parseForm(buffer.toString(StandardCharsets.UTF_8));
        }
    }

    public static String cookie(HttpExchange exchange, String name) {
        String header = exchange.getRequestHeaders().getFirst("Cookie");
        if (header == null) return null;
        for (String part : header.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
    }

    /** HttpOnly keeps the session token away from page scripts. */
    public static void setSessionCookie(HttpExchange exchange, String token, int maxAgeSeconds) {
        exchange.getResponseHeaders().add("Set-Cookie",
                "cs_session=" + token + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAgeSeconds);
    }

    public static void addSecurityHeaders(HttpExchange exchange) {
        var headers = exchange.getResponseHeaders();
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
    }

    public static void json(HttpExchange exchange, int status, Object payload) throws IOException {
        byte[] bytes = Json.write(payload).getBytes(StandardCharsets.UTF_8);
        addSecurityHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    public static void error(HttpExchange exchange, int status, String message) throws IOException {
        json(exchange, status, Json.map("error", message));
    }
}
