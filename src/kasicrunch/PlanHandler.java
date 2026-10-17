/*
 * File:    PlanHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The subscription side of the revenue model: the Crunch Club and
 *          Family Club monthly plans. A shopper has at most one active club.
 */
package kasicrunch;

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
            case "POST /api/subscriptions/cancel" -> cancel(exchange);
            default -> throw noRoute();
        }
    }

    private void plans(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> plans = new ArrayList<>();
        try (Connection connection = Database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(
                     "SELECT code, name, price_monthly, discount_rate, free_delivery, description, features FROM plans ORDER BY price_monthly")) {
            while (row.next()) {
                plans.add(Json.map("code", row.getString(1), "name", row.getString(2), "priceMonthly", row.getDouble(3),
                        "discountRate", row.getDouble(4), "freeDelivery", row.getBoolean(5),
                        "description", row.getString(6), "features", List.of(row.getString(7).split("\\|"))));
            }
        }
        Http.json(exchange, 200, Json.map("plans", plans));
    }

    private void subscribe(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        if (user.isAdmin()) {
            throw ApiException.badRequest("The admin account cannot join a club. Please use a customer account.");
        }
        Map<String, String> form = Http.body(exchange);
        String code = Validator.oneOf(form, "planCode", "club", "crunch-club", "family-club");

        try (Connection connection = Database.connect()) {
            connection.setAutoCommit(false);
            try {
                Auth.Club current = Auth.activeClub(connection, user.id());
                if (current != null && current.code().equals(code)) {
                    throw ApiException.badRequest("You are already in the " + current.name() + ". Enjoy!");
                }
                // Only one club at a time: switching ends the old one.
                try (PreparedStatement end = connection.prepareStatement(
                        "UPDATE subscriptions SET status = 'cancelled' WHERE user_id = ? AND status = 'active'")) {
                    end.setLong(1, user.id());
                    end.executeUpdate();
                }
                String name;
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO subscriptions (user_id, plan_id) SELECT ?, id FROM plans WHERE code = ?");
                     PreparedStatement lookup = connection.prepareStatement("SELECT name FROM plans WHERE code = ?")) {
                    insert.setLong(1, user.id());
                    insert.setString(2, code);
                    insert.executeUpdate();
                    lookup.setString(1, code);
                    try (ResultSet row = lookup.executeQuery()) {
                        row.next();
                        name = row.getString(1);
                    }
                }
                connection.commit();
                Http.json(exchange, 201, Json.map("message", "Welcome to the " + name + "! Your benefits apply from your next order."));
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void cancel(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        try (Connection connection = Database.connect();
             PreparedStatement end = connection.prepareStatement(
                     "UPDATE subscriptions SET status = 'cancelled' WHERE user_id = ? AND status = 'active'")) {
            end.setLong(1, user.id());
            if (end.executeUpdate() == 0) {
                throw ApiException.badRequest("You are not in a club at the moment.");
            }
        }
        Http.json(exchange, 200, Json.map("message", "Your club membership has been cancelled."));
    }
}
