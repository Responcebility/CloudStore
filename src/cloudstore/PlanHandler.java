/*
 * File:    PlanHandler.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The subscription side of the revenue model: vendor plans
 *          (Starter, Growth, Pro) that open or upgrade a shop, and the
 *          CloudStore Plus plan that gives shoppers free delivery.
 */
package cloudstore;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PlanHandler extends ApiHandler {

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "GET /api/plans" -> plans(exchange);
            case "POST /api/subscriptions" -> subscribe(exchange);
            case "POST /api/subscriptions/cancel" -> cancelPlus(exchange);
            case "GET /api/subscriptions/mine" -> mine(exchange);
            default -> throw noRoute();
        }
    }

    private void plans(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> plans = new ArrayList<>();
        try (Connection connection = Database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT code, name, audience, price_monthly, commission_rate, description, features FROM plans ORDER BY audience DESC, price_monthly")) {
            while (row.next()) {
                double rate = row.getDouble(5);
                plans.add(Json.map("code", row.getString(1), "name", row.getString(2), "audience", row.getString(3),
                        "priceMonthly", row.getDouble(4), "commissionRate", row.wasNull() ? null : rate,
                        "description", row.getString(6), "features", List.of(row.getString(7).split("\\|"))));
            }
        }
        Http.json(exchange, 200, Json.map("plans", plans));
    }

    private void subscribe(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        Map<String, String> form = Http.body(exchange);
        String code = Validator.oneOf(form, "planCode", "plan", "starter", "growth", "pro", "plus");

        try (Connection connection = Database.connect()) {
            connection.setAutoCommit(false);
            try {
                int planId = planId(connection, code);
                String message;
                if ("plus".equals(code)) {
                    if (Auth.hasPlus(connection, user.id())) {
                        throw ApiException.badRequest("You are already a CloudStore Plus member. Enjoy your free delivery!");
                    }
                    insertSubscription(connection, user.id(), planId);
                    message = "Welcome to CloudStore Plus! Delivery is now free on all your orders.";
                } else {
                    if (user.isAdmin()) {
                        throw ApiException.badRequest("The admin account cannot open a shop. Please use a separate account to sell.");
                    }
                    message = subscribeVendor(connection, user, planId, code, form);
                }
                connection.commit();
                Http.json(exchange, 201, Json.map("message", message));
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    /** Opens a shop on the chosen plan, or moves an existing shop to that plan. */
    private String subscribeVendor(Connection connection, Auth.User user, int planId, String code, Map<String, String> form) throws Exception {
        Long vendorId = null;
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM vendors WHERE owner_user_id = ?")) {
            query.setLong(1, user.id());
            try (ResultSet row = query.executeQuery()) {
                if (row.next()) vendorId = row.getLong(1);
            }
        }

        // Only one vendor plan is active at a time.
        try (PreparedStatement cancel = connection.prepareStatement(
                "UPDATE subscriptions s JOIN plans p ON p.id = s.plan_id SET s.status = 'cancelled'"
                        + " WHERE s.user_id = ? AND s.status = 'active' AND p.audience = 'vendor'")) {
            cancel.setLong(1, user.id());
            cancel.executeUpdate();
        }
        insertSubscription(connection, user.id(), planId);

        if (vendorId != null) {
            try (PreparedStatement update = connection.prepareStatement("UPDATE vendors SET plan_id = ? WHERE id = ?")) {
                update.setInt(1, planId);
                update.setLong(2, vendorId);
                update.executeUpdate();
            }
            return "Your shop is now on the " + capitalise(code) + " plan.";
        }

        String shopName = Validator.text(form, "shopName", "shop name", 2, 100);
        String location = Validator.text(form, "location", "shop location", 2, 100);
        String description = Validator.text(form, "description", "short shop description", 5, 255);
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vendors (name, location, description, owner_user_id, plan_id) VALUES (?,?,?,?,?)")) {
            insert.setString(1, shopName);
            insert.setString(2, location);
            insert.setString(3, description);
            insert.setLong(4, user.id());
            insert.setInt(5, planId);
            insert.executeUpdate();
        }
        try (PreparedStatement role = connection.prepareStatement("UPDATE users SET role = 'vendor' WHERE id = ?")) {
            role.setLong(1, user.id());
            role.executeUpdate();
        }
        return shopName + " is open on the " + capitalise(code) + " plan. Add your first products from My Account.";
    }

    private void cancelPlus(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        try (Connection connection = Database.connect();
             PreparedStatement cancel = connection.prepareStatement(
                     "UPDATE subscriptions s JOIN plans p ON p.id = s.plan_id SET s.status = 'cancelled'"
                             + " WHERE s.user_id = ? AND s.status = 'active' AND p.code = 'plus'")) {
            cancel.setLong(1, user.id());
            if (cancel.executeUpdate() == 0) {
                throw ApiException.badRequest("You do not have an active CloudStore Plus membership.");
            }
        }
        Http.json(exchange, 200, Json.map("message", "Your CloudStore Plus membership has been cancelled."));
    }

    private void mine(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        List<Map<String, Object>> subscriptions = new ArrayList<>();
        String sql = "SELECT p.code, p.name, p.audience, p.price_monthly, DATE_FORMAT(s.started_at, '%Y-%m-%d') FROM subscriptions s"
                + " JOIN plans p ON p.id = s.plan_id WHERE s.user_id = ? AND s.status = 'active' ORDER BY s.started_at DESC";
        try (Connection connection = Database.connect(); PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, user.id());
            try (ResultSet row = query.executeQuery()) {
                while (row.next()) {
                    subscriptions.add(Json.map("code", row.getString(1), "name", row.getString(2), "audience", row.getString(3),
                            "priceMonthly", row.getDouble(4), "startedAt", row.getString(5)));
                }
            }
        }
        Http.json(exchange, 200, Json.map("subscriptions", subscriptions));
    }

    private static int planId(Connection connection, String code) throws Exception {
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM plans WHERE code = ?")) {
            query.setString(1, code);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw ApiException.notFound("That plan does not exist.");
                return row.getInt(1);
            }
        }
    }

    private static void insertSubscription(Connection connection, long userId, int planId) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("INSERT INTO subscriptions (user_id, plan_id) VALUES (?,?)")) {
            insert.setLong(1, userId);
            insert.setInt(2, planId);
            insert.executeUpdate();
        }
    }

    private static String capitalise(String code) {
        return Character.toUpperCase(code.charAt(0)) + code.substring(1);
    }
}
