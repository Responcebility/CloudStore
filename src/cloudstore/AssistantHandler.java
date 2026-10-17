/*
 * File:    AssistantHandler.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: CloudBot, the shopping assistant in the corner of every page.
 *          It is rule-based: it matches keywords in the question to an answer,
 *          looks up matching products in the database, and can tell a
 *          logged-in shopper the status of their latest order. A full AI
 *          chatbot is listed in the report as a recommended next integration.
 */
package cloudstore;

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
            "items", "products", "product", "stuff", "something", "cheap", "best", "good", "your", "there", "are", "is");

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        if (!"POST /api/assistant".equals(method + " " + path)) {
            throw noRoute();
        }
        Map<String, String> form = Http.body(exchange);
        String message = Validator.text(form, "message", "question", 1, 300).toLowerCase();

        try (Connection connection = Database.connect()) {
            Map<String, Object> reply = answer(exchange, connection, message);
            Http.json(exchange, 200, reply);
        }
    }

    private Map<String, Object> answer(HttpExchange exchange, Connection connection, String message) throws Exception {
        if (matches(message, "hello", "hi", "hey", "sawubona", "dumela", "molo", "howzit")) {
            return reply("Hi there! I'm CloudBot. Ask me about products, delivery, plans or your latest order.");
        }
        if (matches(message, "track", "my order", "order status", "where is my", "latest order")) {
            return orderStatus(exchange, connection);
        }
        if (matches(message, "deliver", "delivery", "shipping", "courier")) {
            return reply(String.format("Delivery costs R%.2f per order and is free when your order is over R%.0f. "
                    + "CloudStore Plus members (R49 a month) get free delivery on every order.",
                    Pricing.DELIVERY_FEE, Pricing.FREE_DELIVERY_THRESHOLD));
        }
        if (matches(message, "plus", "membership", "subscribe", "subscription")) {
            return reply("CloudStore Plus costs R49 a month and gives you free delivery on all orders. "
                    + "Vendors can choose Starter (free, 10% commission), Growth (R149, 6%) or Pro (R349, 3%). See the Revenue Model page.");
        }
        if (matches(message, "vendor", "open a shop", "start selling", "become a seller", "my shop", "commission")) {
            return reply("Anyone in the community can open a shop. Start on the free Starter plan and pay 10% only when you sell, "
                    + "or upgrade to Growth or Pro for lower commission. Log in, then pick a plan on the Revenue Model page.");
        }
        if (matches(message, "pay", "payment", "card", "cash")) {
            return reply("You can pay cash or by card when your order is delivered. Online payments are a planned next step.");
        }
        if (matches(message, "contact", "help", "support", "email", "phone number")) {
            return reply("You can reach the CloudStore team through the Contact page. We reply within two working days.");
        }
        if (matches(message, "who are you", "what is cloudstore", "about cloudstore", "what do you do")) {
            return reply("CloudStore is an online marketplace that connects township and campus shoppers with local vendors, "
                    + "so small community businesses can sell online without building their own website.");
        }

        // Otherwise treat the question as a product search.
        for (String word : message.split("[^a-z]+")) {
            if (word.length() < 3 || STOP_WORDS.contains(word)) continue;
            String term = word.endsWith("s") && word.length() > 4 ? word.substring(0, word.length() - 1) : word;
            List<Map<String, Object>> products = ProductHandler.search(connection, term, 3);
            if (!products.isEmpty()) {
                Map<String, Object> result = reply("Here is what I found for \"" + word + "\":");
                result.put("products", products);
                return result;
            }
        }
        return reply("Sorry, I didn't catch that. Try asking about a product (like \"calculator\" or \"veggie box\"), "
                + "delivery, plans, payments or your latest order.");
    }

    private Map<String, Object> orderStatus(HttpExchange exchange, Connection connection) throws Exception {
        Auth.User user = Auth.current(exchange);
        if (user == null) {
            return reply("Please log in first, then ask me again and I'll check your latest order.");
        }
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT id, status, total FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 1")) {
            query.setLong(1, user.id());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    return reply("You haven't placed any orders yet. Browse the Shop to get started!");
                }
                return reply(String.format("Your latest order #%d (R%.2f) is: %s.", row.getLong(1), row.getDouble(3), row.getString(2)));
            }
        }
    }

    private static boolean matches(String message, String... keywords) {
        for (String keyword : keywords) {
            // Whole-word match for short keywords, so "hi" does not match "this".
            String pattern = keyword.length() <= 3 ? "(^|.*\\W)" + keyword + "(\\W.*|$)" : ".*" + keyword + ".*";
            if (message.matches(pattern)) return true;
        }
        return false;
    }

    private static Map<String, Object> reply(String text) {
        return Json.map("reply", text);
    }
}
