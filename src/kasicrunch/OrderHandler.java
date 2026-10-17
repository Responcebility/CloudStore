/*
 * File:    OrderHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Checkout and order history. Prices, the delivery fee (per km,
 *          minus what the customer's Crunch Pass covers) and food cost are
 *          always calculated here on the server, never trusted from the
 *          browser, and stock is checked in one transaction.
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderHandler extends ApiHandler {

    /** Parameter: most different menu items allowed in one order. */
    private static final int MAX_LINES = 30;

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
            throw ApiException.badRequest("Your basket is empty. Please add something from the menu before checking out.");
        }
        Map<Long, Integer> items = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String[] pieces = part.trim().split(":");
            if (pieces.length != 2) {
                throw ApiException.badRequest("We could not read your basket. Please refresh the page and try again.");
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
        String fulfilment = Validator.oneOf(form, "fulfilment", "delivery or collection option", "delivery", "collection");
        boolean delivery = "delivery".equals(fulfilment);
        String address = null;
        double distance = 0;
        if (delivery) {
            address = Validator.text(form, "address", "delivery address", 5, 200);
            distance = Validator.decimalInRange(form.get("distanceKm"), "delivery distance", 0.1, Pricing.MAX_DELIVERY_KM, "km");
        }
        String phone = Validator.phone(form, "phone");
        String payment = Validator.oneOf(form, "paymentMethod", "payment method", "Cash", "Card");

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
                                throw ApiException.badRequest("One of the items in your basket is no longer on the menu. Please remove it and try again.");
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
                Auth.Pass pass = delivery ? Auth.activePass(connection, user.id()) : null;
                int passKm = pass == null ? 0 : pass.maxKm();
                double deliveryFee = delivery ? Pricing.deliveryFee(distance, passKm) : 0;
                double total = Pricing.orderTotal(subtotal, deliveryFee);

                long orderId;
                String orderSql = "INSERT INTO orders (user_id, fulfilment, distance_km, pass_km, subtotal, delivery_fee, total, food_cost,"
                        + " payment_method, address, phone) VALUES (?,?,?,?,?,?,?,?,?,?,?)";
                try (PreparedStatement insert = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, user.id());
                    insert.setString(2, fulfilment);
                    insert.setDouble(3, distance);
                    insert.setInt(4, passKm);
                    insert.setDouble(5, subtotal);
                    insert.setDouble(6, deliveryFee);
                    insert.setDouble(7, total);
                    insert.setDouble(8, Pricing.round2(foodCost));
                    insert.setString(9, payment);
                    if (address == null) insert.setNull(10, Types.VARCHAR); else insert.setString(10, address);
                    insert.setString(11, phone);
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

                String deliveryNote = !delivery ? "Collection is free."
                        : deliveryFee == 0 ? "Free delivery with your Crunch Pass."
                        : passKm > 0 ? String.format("Your Crunch Pass covers %d km; you paid for the extra %.1f km.", passKm, distance - passKm)
                        : null;
                Http.json(exchange, 201, Json.map(
                        "orderId", orderId,
                        "subtotal", subtotal,
                        "deliveryFee", deliveryFee,
                        "total", total,
                        "deliveryNote", deliveryNote,
                        "message", "Thank you! Order #" + orderId + " is in, and we're getting it crunchy."));
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
            String orderSql = "SELECT id, fulfilment, distance_km, subtotal, delivery_fee, total, payment_method, status,"
                    + " DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') FROM orders WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 50";
            try (PreparedStatement query = connection.prepareStatement(orderSql)) {
                query.setLong(1, user.id());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        List<Map<String, Object>> items = new ArrayList<>();
                        itemsByOrder.put(row.getLong(1), items);
                        orders.add(Json.map("id", row.getLong(1), "fulfilment", row.getString(2), "distanceKm", row.getDouble(3),
                                "subtotal", row.getDouble(4), "deliveryFee", row.getDouble(5), "total", row.getDouble(6),
                                "paymentMethod", row.getString(7), "status", row.getString(8), "createdAt", row.getString(9), "items", items));
                    }
                }
            }
            String itemSql = "SELECT oi.order_id, p.name, oi.quantity FROM order_items oi"
                    + " JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id WHERE o.user_id = ?";
            try (PreparedStatement query = connection.prepareStatement(itemSql)) {
                query.setLong(1, user.id());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        List<Map<String, Object>> items = itemsByOrder.get(row.getLong(1));
                        if (items != null) items.add(Json.map("name", row.getString(2), "quantity", row.getInt(3)));
                    }
                }
            }
        }
        Http.json(exchange, 200, Json.map("orders", orders));
    }
}
