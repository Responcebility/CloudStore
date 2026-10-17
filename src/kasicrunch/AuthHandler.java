/*
 * File:    AuthHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Register, log in, log out and "who am I" endpoints under /api/auth.
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

public final class AuthHandler extends ApiHandler {

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "POST /api/auth/register" -> register(exchange);
            case "POST /api/auth/login" -> login(exchange);
            case "POST /api/auth/logout" -> logout(exchange);
            case "GET /api/auth/me" -> me(exchange);
            default -> throw noRoute();
        }
    }

    private void register(HttpExchange exchange) throws Exception {
        Map<String, String> form = Http.body(exchange);
        String fullName = Validator.text(form, "fullName", "full name", 2, 80);
        String email = Validator.email(form, "email");
        String password = Validator.password(form, "password");

        long userId;
        try (Connection connection = Database.connect()) {
            try (PreparedStatement check = connection.prepareStatement("SELECT id FROM users WHERE email = ?")) {
                check.setString(1, email);
                try (ResultSet existing = check.executeQuery()) {
                    if (existing.next()) {
                        throw ApiException.badRequest("An account with that email already exists. Please log in instead.");
                    }
                }
            }
            userId = Database.insertUser(connection, fullName, email, password, "customer");
        }
        Auth.startSession(exchange, userId);
        Http.json(exchange, 201, Json.map("user", new Auth.User(userId, fullName, email, "customer").toJson()));
    }

    private void login(HttpExchange exchange) throws Exception {
        Map<String, String> form = Http.body(exchange);
        String email = form.getOrDefault("email", "").trim().toLowerCase();
        String password = form.getOrDefault("password", "");
        if (email.isEmpty() || password.isEmpty()) {
            throw ApiException.badRequest("Please enter your email and password.");
        }

        try (Connection connection = Database.connect();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT id, full_name, email, role, password_hash FROM users WHERE email = ?")) {
            query.setString(1, email);
            try (ResultSet row = query.executeQuery()) {
                // The same message for an unknown email or a wrong password, so
                // the form does not reveal which emails have accounts.
                if (!row.next() || !PasswordHasher.verify(password, row.getString(5))) {
                    throw new ApiException(401, "That email and password do not match our records.");
                }
                Auth.User user = new Auth.User(row.getLong(1), row.getString(2), row.getString(3), row.getString(4));
                Auth.startSession(exchange, user.id());
                Http.json(exchange, 200, Json.map("user", user.toJson()));
            }
        }
    }

    private void logout(HttpExchange exchange) throws Exception {
        Auth.endSession(exchange);
        Http.json(exchange, 200, Json.map("ok", true));
    }

    private void me(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.current(exchange);
        if (user == null) {
            Http.json(exchange, 200, Json.map("user", null));
            return;
        }
        try (Connection connection = Database.connect()) {
            Map<String, Object> json = user.toJson();
            Auth.Pass pass = Auth.activePass(connection, user.id());
            json.put("pass", pass == null ? null : pass.toJson());
            Http.json(exchange, 200, Json.map("user", json));
        }
    }
}
