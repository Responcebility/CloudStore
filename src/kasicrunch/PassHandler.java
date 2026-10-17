/*
 * File:    PassHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The Crunch Pass, the subscription side of the revenue model. The
 *          customer picks the furthest distance they want free delivery for
 *          (1-20 km); that choice sets the price for the month:
 *              price = R49 + R15 x maxKm
 *          A pass lasts 30 days. Choosing a new distance starts a new month
 *          at the new price.
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

public final class PassHandler extends ApiHandler {

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "GET /api/pass/quote" -> quote(exchange);
            case "POST /api/pass" -> buy(exchange);
            case "POST /api/pass/cancel" -> cancel(exchange);
            default -> throw noRoute();
        }
    }

    private static int readKm(String raw) {
        return Validator.intInRange(raw, "maximum distance (km)", Pricing.PASS_MIN_KM, Pricing.PASS_MAX_KM);
    }

    /** The price for a chosen distance, so the page can show it as the slider moves. */
    private void quote(HttpExchange exchange) throws Exception {
        int maxKm = readKm(Http.query(exchange).get("maxKm"));
        Http.json(exchange, 200, Json.map("maxKm", maxKm, "price", Pricing.passPrice(maxKm)));
    }

    private void buy(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        if (user.isAdmin()) {
            throw ApiException.badRequest("The admin account cannot buy a Crunch Pass. Please use a customer account.");
        }
        int maxKm = readKm(Http.body(exchange).get("maxKm"));
        double price = Pricing.passPrice(maxKm);

        try (Connection connection = Database.connect()) {
            connection.setAutoCommit(false);
            try {
                Auth.Pass current = Auth.activePass(connection, user.id());
                if (current != null && current.maxKm() == maxKm) {
                    throw ApiException.badRequest("You already have a " + maxKm + " km Crunch Pass until " + current.endsAt() + ".");
                }
                // One pass at a time: a new distance replaces the old pass.
                try (PreparedStatement end = connection.prepareStatement(
                        "UPDATE passes SET status = 'cancelled' WHERE user_id = ? AND status = 'active'")) {
                    end.setLong(1, user.id());
                    end.executeUpdate();
                }
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO passes (user_id, max_km, price, ends_at) VALUES (?, ?, ?, NOW() + INTERVAL " + Pricing.PASS_DAYS + " DAY)")) {
                    insert.setLong(1, user.id());
                    insert.setInt(2, maxKm);
                    insert.setDouble(3, price);
                    insert.executeUpdate();
                }
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
        Http.json(exchange, 201, Json.map("message", String.format(
                "Your Crunch Pass is active: free delivery up to %d km for the next %d days (R%.2f).", maxKm, Pricing.PASS_DAYS, price)));
    }

    private void cancel(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        try (Connection connection = Database.connect();
             PreparedStatement end = connection.prepareStatement(
                     "UPDATE passes SET status = 'cancelled' WHERE user_id = ? AND status = 'active' AND ends_at > NOW()")) {
            end.setLong(1, user.id());
            if (end.executeUpdate() == 0) {
                throw ApiException.badRequest("You don't have an active Crunch Pass.");
            }
        }
        Http.json(exchange, 200, Json.map("message", "Your Crunch Pass has been cancelled."));
    }
}
