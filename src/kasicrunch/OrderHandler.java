/*
 * File:    OrderHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Checkout and order history. Prices, club discount, delivery fee
 *          and food cost are always calculated here on the server from the
 *          database, never trusted from the browser, and stock is checked in
 *          one transaction.
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderHandler extends ApiHandler {

    /** Parameter: most different menu items allowed in one order. */
    private static final int MAX_LINES = 30;
    private static final String[] PAYMENT_METHODS = {"Cash on delivery", "Card on delivery"};

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "POST /api/orders" -> placeOrder(exchange);
            case "GET /api/orders/mine" -> myOrders(exchange);
            default -> throw noRoute();
        }
    }

    /** Parses "12:2,5:1" (item id : quantity) into an ordered map, merging repeats. */
    static Map<Long, Integer> parseItems(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Your cart is empty. Please add something from the menu before checking out.");
        }
        Map<Long, Integer> items = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String[] pieces = part.trim().split(":");
            if (pieces.length != 2) {
                throw ApiException.badRequest("We could not read your cart. Please refresh the page and try again.");
            }
            long productId = Validator.intInRange(pieces[0], "menu item number", 1, Integer.MAX_VALUE);
            int quantity = Validator.intInRange(pieces[1], "quantity", 1, Pricing.MAX_QUANTITY_PER_ITEM);
            int merged = items.getOrDefault(productId, 0) + quantity;
            if (merged > Pricing.MAX_QUANTITY_PER_ITEM) {
                throw ApiException.badRequest("Please order at most " + Pricing.MAX_QUANTITY_PER_ITEM + " of the same item.");
            }
            items.put(productId, merged);
        }
        if (items.size() > MAX_LINES) {
            throw ApiException.badRequest("Please order at most " + MAX_LINES + " different items at a time.");
        }
        return items;
    }

    private void placeOrder(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        Map<String, String> form = Http.body(exchange);
        Map<Long, Integer> items = parseItems(form.get("items"));
        String address = Validator.text(form, "address", "delivery address", 5, 200);
        String phone = Validator.phone(form, "phone");
        String payment = Validator.oneOf(form, "paymentMethod", "payment method", PAYMENT_METHODS);

        try (Connection connection = Database.connect()) {
            connection.setAutoCommit(false);
            try {
                double subtotal = 0;
                double foodCost = 0;
                List<Object[]> lines = new ArrayList<>(); // {productId, quantity, unitPrice, unitCost}
                try (PreparedStatement query = connection.prepareStatement(
                        "SELECT name, price, cost, stock FROM products WHERE id = ? FOR UPDATE")) {
                    for (Map.Entry<Long, Integer> item : items.entrySet()) {
                        query.setLong(1, item.getKey());
                        try (ResultSet row = query.executeQuery()) {
                            if (!row.next()) {
                                throw ApiException.badRequest("One of the items in your cart is no longer on the menu. Please remove it and try again.");
                            }
                            String name = row.getString(1);
                            double price = row.getDouble(2);
                            double cost = row.getDouble(3);
                            int stock = row.getInt(4);
                            if (item.getValue() > stock) {
                                throw ApiException.badRequest("Sorry, only " + stock + " × " + name + " left today. Please lower the quantity.");
                            }
                            subtotal += price * item.getValue();
                            foodCost += cost * item.getValue();
                            lines.add(new Object[]{item.getKey(), item.getValue(), price, cost});
                        }
                    }
                }

                subtotal = Pricing.round2(subtotal);
                Auth.Club club = Auth.activeClub(connection, user.id());
                double discount = Pricing.discount(subtotal, club == null ? 0 : club.discountRate());
                double delivery = Pricing.deliveryFee(subtotal, club != null && club.freeDelivery());
                double total = Pricing.orderTotal(subtotal, discount, delivery);

                long orderId;
                String orderSql = "INSERT INTO orders (user_id, subtotal, discount, delivery_fee, total, food_cost, payment_method, address, phone)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement insert = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, user.id());
                    insert.setDouble(2, subtotal);
                    insert.setDouble(3, discount);
                    insert.setDouble(4, delivery);
                    insert.setDouble(5, total);
                    insert.setDouble(6, Pricing.round2(foodCost));
                    insert.setString(7, payment);
                    insert.setString(8, address);
                    insert.setString(9, phone);
                    insert.executeUpdate();
                    orderId = Database.generatedId(insert);
                }

                try (PreparedStatement item = connection.prepareStatement(
                        "INSERT INTO order_items (order_id, product_id, quantity, unit_price, unit_cost) VALUES (?,?,?,?,?)");
                     PreparedStatement stock = connection.prepareStatement("UPDATE products SET stock = stock - ? WHERE id = ?")) {
                    for (Object[] line : lines) {
                        item.setLong(1, orderId);
                        item.setLong(2, (Long) line[0]);
                        item.setInt(3, (Integer) line[1]);
                        item.setDouble(4, (Double) line[2]);
                        item.setDouble(5, (Double) line[3]);
                        item.executeUpdate();
                        stock.setInt(1, (Integer) line[1]);
                        stock.setLong(2, (Long) line[0]);
                        stock.executeUpdate();
                    }
                }
                connection.commit();

                String freeReason = delivery > 0 ? null
                        : (club != null && club.freeDelivery() ? club.name() + " member" : "Order over R" + (int) Pricing.FREE_DELIVERY_THRESHOLD);
                Http.json(exchange, 201, Json.map(
                        "orderId", orderId,
                        "subtotal", subtotal,
                        "discount", discount,
                        "deliveryFee", delivery,
                        "total", total,
                        "freeDeliveryReason", freeReason,
                        "message", "Thank you! Order #" + orderId + " is in — we're getting it crunchy."));
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        }
    }

    private void myOrders(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        List<Map<String, Object>> orders = new ArrayList<>();
        Map<Long, List<Map<String, Object>>> itemsByOrder = new LinkedHashMap<>();
        try (Connection connection = Database.connect()) {
            String orderSql = "SELECT id, subtotal, discount, delivery_fee, total, payment_method, status, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')"
                    + " FROM orders WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 50";
            try (PreparedStatement query = connection.prepareStatement(orderSql)) {
                query.setLong(1, user.id());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        List<Map<String, Object>> items = new ArrayList<>();
                        itemsByOrder.put(row.getLong(1), items);
                        orders.add(Json.map("id", row.getLong(1), "subtotal", row.getDouble(2), "discount", row.getDouble(3),
                                "deliveryFee", row.getDouble(4), "total", row.getDouble(5), "paymentMethod", row.getString(6),
                                "status", row.getString(7), "createdAt", row.getString(8), "items", items));
                    }
                }
            }
            String itemSql = "SELECT oi.order_id, p.name, p.emoji, oi.quantity FROM order_items oi"
                    + " JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id WHERE o.user_id = ?";
            try (PreparedStatement query = connection.prepareStatement(itemSql)) {
                query.setLong(1, user.id());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        List<Map<String, Object>> items = itemsByOrder.get(row.getLong(1));
                        if (items != null) {
                            items.add(Json.map("name", row.getString(2), "emoji", row.getString(3), "quantity", row.getInt(4)));
                        }
                    }
                }
            }
        }
        Http.json(exchange, 200, Json.map("orders", orders));
    }
}
