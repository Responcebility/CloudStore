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
            "items", "menu", "food", "something", "cheap", "best", "good", "your", "there", "are", "order", "give");

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
        if (matches(message, "deliver", "delivery", "courier")) {
            return reply(String.format("Delivery is R%.0f, and free when your order is over R%.0f. "
                    + "Crunch Club and Family Club members get free delivery on every order.",
                    Pricing.DELIVERY_FEE, Pricing.FREE_DELIVERY_THRESHOLD));
        }
        if (matches(message, "club", "membership", "subscribe", "subscription", "discount")) {
            return reply("Crunch Club (R49 a month) gives you free delivery on every order. "
                    + "Family Club (R129 a month) adds 10% off every order. See the Revenue Model page to join.");
        }
        if (matches(message, "spicy", "hot", "fire", "peri")) {
            List<Map<String, Object>> spicy = spicyItems(connection);
            Map<String, Object> result = reply("Feeling brave? These ones bring the heat 🌶️:");
            result.put("products", spicy);
            return result;
        }
        if (matches(message, "popular", "best seller", "bestseller", "recommend", "favourite", "favorite")) {
            Map<String, Object> result = reply("Our customers love these:");
            result.put("products", ProductHandler.search(connection, "", 3));
            return result;
        }
        if (matches(message, "cheap", "budget", "student", "saver", "broke")) {
            Map<String, Object> result = reply("On a budget? The Student Saver combo is the best value:");
            result.put("products", ProductHandler.search(connection, "Saver", 1));
            return result;
        }
        if (matches(message, "pay", "payment", "card", "cash")) {
            return reply("You can pay cash or by card when your order is delivered. Online payments are coming next.");
        }
        if (matches(message, "contact", "help", "support", "email", "complain")) {
            return reply("You can reach the Kasi-Crunch team on the Contact page. We reply within two working days.");
        }
        if (matches(message, "who are you", "what is kasi", "about kasi")) {
            return reply("Kasi-Crunch is a township fried-chicken brand: crunch outside, juicy inside. "
                    + "Order online and we deliver around Soshanguve and nearby.");
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
        return reply("Eish, I didn't catch that. Try \"wings\", \"kota\", \"something spicy\", \"delivery\" or \"track my order\".");
    }

    private List<Map<String, Object>> spicyItems(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, description, category, price, stock, emoji, popular, spicy FROM products WHERE spicy = 1 ORDER BY price LIMIT 3")) {
            return ProductHandler.readProducts(statement);
        }
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
                    return reply("You haven't ordered yet. Have a look at the menu — the 2-Piece Crunch is a great start!");
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
