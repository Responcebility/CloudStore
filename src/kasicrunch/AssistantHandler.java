/*
 * File:    AssistantHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Crunchy, the ordering assistant in the corner of every page. It is
 *          rule-based: it matches keywords in the question to an answer, looks
 *          up matching menu items, and can tell a logged-in customer the
 *          status of their latest order. A full AI chatbot is listed in the
 *          report as a recommended next integration.
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AssistantHandler extends ApiHandler {

    private static final Set<String> STOP_WORDS = Set.of(
            "the", "and", "for", "you", "have", "any", "can", "buy", "want", "need", "looking", "some", "with",
            "what", "where", "how", "much", "does", "cost", "price", "please", "show", "find", "get", "sell", "selling",
            "items", "menu", "food", "something", "cheap", "best", "good", "your", "there", "are", "order", "give", "crunch", "kasi");

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        if (!"POST /api/assistant".equals(method + " " + path)) {
            throw noRoute();
        }
        Map<String, String> form = Http.body(exchange);
        String message = Validator.text(form, "message", "question", 1, 300).toLowerCase();

        try (Connection connection = Database.connect()) {
            Http.json(exchange, 200, answer(exchange, connection, message));
        }
    }

    private Map<String, Object> answer(HttpExchange exchange, Connection connection, String message) throws Exception {
        if (matches(message, "hello", "hi", "hey", "sawubona", "dumela", "molo", "howzit", "sharp")) {
            return reply("Sharp sharp! I'm Crunchy. Ask me about the menu, delivery, the clubs or your latest order.");
        }
        if (matches(message, "track", "my order", "order status", "where is my", "latest order")) {
            return orderStatus(exchange, connection);
        }
        if (matches(message, "pass", "subscribe", "subscription", "membership", "free delivery")) {
            return reply(String.format("The Crunch Pass gives you free delivery for a month. You choose how far (1-%d km) and the price "
                    + "is R%.0f + R%.0f per km, so a 5 km pass is R%.0f. Set yours on the Crunch Pass page.",
                    Pricing.PASS_MAX_KM, Pricing.PASS_BASE_PRICE, Pricing.PASS_RATE_PER_KM, Pricing.passPrice(5)));
        }
        if (matches(message, "deliver", "delivery", "courier", "collect", "collection", "pick up", "pickup")) {
            return reply(String.format("Delivery is R%.0f per km, up to %.0f km away. Collection is always free. "
                    + "With a Crunch Pass, delivery is free within your chosen distance.",
                    Pricing.DELIVERY_RATE_PER_KM, Pricing.MAX_DELIVERY_KM));
        }
        if (matches(message, "spicy", "hot", "fire", "peri")) {
            List<Map<String, Object>> spicy = ProductHandler.where(connection, "spicy = 1", 3);
            Map<String, Object> result = reply("Feeling brave? These ones bring the heat 🌶️:");
            result.put("products", spicy);
            return result;
        }
        if (matches(message, "popular", "best seller", "bestseller", "recommend", "favourite", "favorite")) {
            Map<String, Object> result = reply("Our customers love these:");
            result.put("products", ProductHandler.where(connection, "popular = 1", 3));
            return result;
        }
        if (matches(message, "cheap", "budget", "student", "saver", "broke", "deal")) {
            Map<String, Object> result = reply("On a budget? These are filling and easy on the pocket:");
            result.put("products", ProductHandler.where(connection, "category IN ('Pies','Chicken','Rolls') AND price < 70", 3));
            return result;
        }
        if (matches(message, "pay", "payment", "card", "cash")) {
            return reply("You can pay cash or by card when your order arrives or when you collect it. Online payments are coming next.");
        }
        if (matches(message, "contact", "help", "support", "email", "complain")) {
            return reply("You can reach the Kasi-Crunch team on the Contact page. We reply within two working days.");
        }
        if (matches(message, "who are you", "what is kasi", "about kasi")) {
            return reply("Kasi-Crunch is our own kasi kitchen: crunch outside, juicy inside. "
                    + "Order online for delivery or collection around Soshanguve.");
        }

        // Otherwise treat the question as a menu search.
        for (String word : message.split("[^a-z]+")) {
            if (word.length() < 3 || STOP_WORDS.contains(word)) continue;
            String term = word.endsWith("s") && word.length() > 4 ? word.substring(0, word.length() - 1) : word;
            List<Map<String, Object>> products = ProductHandler.search(connection, term, 3);
            if (!products.isEmpty()) {
                Map<String, Object> result = reply("Here's what I found for \"" + word + "\":");
                result.put("products", products);
                return result;
            }
        }
        return reply("Eish, I didn't catch that. Try \"wings\", \"burger\", \"something spicy\", \"crunch pass\" or \"track my order\".");
    }

    private Map<String, Object> orderStatus(HttpExchange exchange, Connection connection) throws Exception {
        Auth.User user = Auth.current(exchange);
        if (user == null) {
            return reply("Please log in first, then ask me again and I'll check your latest order.");
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id, status, total FROM orders WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 1")) {
            query.setLong(1, user.id());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    return reply("You haven't ordered yet. Have a look at the menu: the Kasi Classic is a great start!");
                }
                return reply(String.format("Your latest order #%d (R%.2f) is: %s.", row.getLong(1), row.getDouble(3), row.getString(2)));
            }
        }
    }

    private static boolean matches(String message, String... keywords) {
        for (String keyword : keywords) {
            // Whole-word match for short keywords, so "hi" does not match "chips".
            String pattern = keyword.length() <= 4 ? "(^|.*\\W)" + keyword + "(\\W.*|$)" : ".*" + keyword + ".*";
            if (message.matches(pattern)) return true;
        }
        return false;
    }

    private static Map<String, Object> reply(String text) {
        return Json.map("reply", text);
    }
}
