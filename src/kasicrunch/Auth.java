/*
 * File:    Auth.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Session handling. A random token is stored in an HttpOnly cookie
 *          and in the sessions table; each request looks the token up to find
 *          the logged-in user and their role (customer or admin).
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public final class Auth {

    public static final String COOKIE = "kc_session";
    public static final int SESSION_DAYS = 7;

    private Auth() {
    }

    public record User(long id, String fullName, String email, String role) {
        public boolean isAdmin() { return "admin".equals(role); }

        public Map<String, Object> toJson() {
            return Json.map("id", id, "fullName", fullName, "email", email, "role", role);
        }
    }

    /** The customer's active Crunch Pass: free delivery up to maxKm until endsAt. */
    public record Pass(long id, int maxKm, double price, String endsAt) {
        public Map<String, Object> toJson() {
            return Json.map("maxKm", maxKm, "price", price, "endsAt", endsAt);
        }
    }

    /** The logged-in user, or null for a visitor. */
    public static User current(HttpExchange exchange) throws SQLException {
        String token = Http.cookie(exchange, COOKIE);
        if (token == null || !token.matches("[0-9a-f]{64}")) {
            return null;
        }
        String sql = "SELECT u.id, u.full_name, u.email, u.role FROM sessions s JOIN users u ON u.id = s.user_id"
                + " WHERE s.token = ? AND s.expires_at > NOW()";
        try (Connection connection = Database.connect(); PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, token);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                return new User(row.getLong(1), row.getString(2), row.getString(3), row.getString(4));
            }
        }
    }

    public static User require(HttpExchange exchange) throws SQLException {
        User user = current(exchange);
        if (user == null) throw ApiException.unauthorized();
        return user;
    }

    public static User requireAdmin(HttpExchange exchange) throws SQLException {
        User user = require(exchange);
        if (!user.isAdmin()) throw ApiException.forbidden();
        return user;
    }

    /** Creates a session and sets its cookie on the response. */
    public static void startSession(HttpExchange exchange, long userId) throws SQLException {
        String token = PasswordHasher.newToken();
        String sql = "INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, NOW() + INTERVAL " + SESSION_DAYS + " DAY)";
        try (Connection connection = Database.connect(); PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, token);
            insert.setLong(2, userId);
            insert.executeUpdate();
        }
        Http.setSessionCookie(exchange, COOKIE, token, SESSION_DAYS * 24 * 60 * 60);
    }

    public static void endSession(HttpExchange exchange) throws SQLException {
        String token = Http.cookie(exchange, COOKIE);
        if (token != null) {
            try (Connection connection = Database.connect();
                 PreparedStatement delete = connection.prepareStatement("DELETE FROM sessions WHERE token = ?")) {
                delete.setString(1, token);
                delete.executeUpdate();
            }
        }
        Http.setSessionCookie(exchange, COOKIE, "", 0);
    }

    /** The user's current Crunch Pass, or null if they don't have one (or it has run out). */
    public static Pass activePass(Connection connection, long userId) throws SQLException {
        String sql = "SELECT id, max_km, price, DATE_FORMAT(ends_at, '%Y-%m-%d') FROM passes"
                + " WHERE user_id = ? AND status = 'active' AND ends_at > NOW() ORDER BY starts_at DESC, id DESC LIMIT 1";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, userId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) return null;
                return new Pass(row.getLong(1), row.getInt(2), row.getDouble(3), row.getString(4));
            }
        }
    }
}
