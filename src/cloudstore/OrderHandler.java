/*
 * File:    OrderHandler.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Checkout and order history. Prices, delivery fee and commission
 *          are always calculated here on the server from the database, never
 *          trusted from the browser, and stock is checked in one transaction.
 */
package cloudstore;

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

    /** Parameter: most different products allowed in one order. */
    private static final int MAX_LINES = 30;
    private static final String[] PAYMENT_METHODS = {"Cash on delivery", "Card on delivery"};

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "POST /api/orders" -> placeOrder(exchange);
            case "GET /api/orders/mine" -> myOrders(exchange);
            case "GET /api/orders/shop" -> shopSummary(exchange);
            default -> throw noRoute();
        }
    }

    /** Parses "12:2,5:1" (product id : quantity) into an ordered map, merging repeats. */
    static Map<Long, Integer> parseItems(String raw) {
        if (raw == null || raw.isBlank()) {
            throw ApiException.badRequest("Your cart is empty. Please add a product before checking out.");
        }
        Map<Long, Integer> items = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String[] pieces = part.trim().split(":");
            if (pieces.length != 2) {
                throw ApiException.badRequest("We could not read your cart. Please refresh the page and try again.");
            }
            long productId = Validator.intInRange(pieces[0], "product number", 1, Integer.MAX_VALUE);
            int quantity = Validator.intInRange(pieces[1], "quantity", 1, Pricing.MAX_QUANTITY_PER_ITEM);
            int merged = items.getOrDefault(productId, 0) + quantity;
            if (merged > Pricing.MAX_QUANTITY_PER_ITEM) {
                throw ApiException.badRequest("Please order at most " + Pricing.MAX_QUANTITY_PER_ITEM + " of the same product.");
            }
            items.put(productId, merged);
        }
        if (items.size() > MAX_LINES) {
            throw ApiException.badRequest("Please order at most " + MAX_LINES + " different products at a time.");
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
                double commission = 0;
                List<Object[]> lines = new ArrayList<>(); // {productId, quantity, unitPrice, rate, name}
                String lock = "SELECT p.name, p.price, p.stock, pl.commission_rate FROM products p"
                        + " JOIN vendors v ON v.id = p.vendor_id JOIN plans pl ON pl.id = v.plan_id WHERE p.id = ? FOR UPDATE";
                try (PreparedStatement query = connection.prepareStatement(lock)) {
                    for (Map.Entry<Long, Integer> item : items.entrySet()) {
                        query.setLong(1, item.getKey());
                        try (ResultSet row = query.executeQuery()) {
                            if (!row.next()) {
                                throw ApiException.badRequest("One of the products in your cart is no longer available. Please remove it and try again.");
                            }
                            String name = row.getString(1);
                            double price = row.getDouble(2);
                            int stock = row.getInt(3);
                            double rate = row.getDouble(4);
                            if (item.getValue() > stock) {
                                throw ApiException.badRequest("Sorry, only " + stock + " of " + name + " left in stock. Please lower the quantity.");
                            }
                            double lineTotal = price * item.getValue();
                            subtotal += lineTotal;
                            commission += lineTotal * rate;
                            lines.add(new Object[]{item.getKey(), item.getValue(), price, rate, name});
                        }
                    }
                }

                subtotal = Pricing.round2(subtotal);
                boolean plus = Auth.hasPlus(connection, user.id());
                double delivery = Pricing.deliveryFee(subtotal, plus);
                double total = Pricing.orderTotal(subtotal, delivery);

                long orderId;
                String orderSql = "INSERT INTO orders (user_id, subtotal, delivery_fee, total, commission, payment_method, address, phone)"
                        + " VALUES (?,?,?,?,?,?,?,?)";
                try (PreparedStatement insert = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setLong(1, user.id());
                    insert.setDouble(2, subtotal);
                    insert.setDouble(3, delivery);
                    insert.setDouble(4, total);
                    insert.setDouble(5, Pricing.round2(commission));
                    insert.setString(6, payment);
                    insert.setString(7, address);
                    insert.setString(8, phone);
                    insert.executeUpdate();
                    orderId = Database.generatedId(insert);
                }

                try (PreparedStatement item = connection.prepareStatement(
                        "INSERT INTO order_items (order_id, product_id, quantity, unit_price, commission_rate) VALUES (?,?,?,?,?)");
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

                Http.json(exchange, 201, Json.map(
                        "orderId", orderId,
                        "subtotal", subtotal,
                        "deliveryFee", delivery,
                        "total", total,
                        "freeDeliveryReason", delivery == 0 ? (plus ? "CloudStore Plus member" : "Order over R" + (int) Pricing.FREE_DELIVERY_THRESHOLD) : null,
                        "message", "Thank you! Order #" + orderId + " has been placed."));
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
            String orderSql = "SELECT id, subtotal, delivery_fee, total, payment_method, status, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')"
                    + " FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 50";
            try (PreparedStatement query = connection.prepareStatement(orderSql)) {
                query.setLong(1, user.id());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        List<Map<String, Object>> items = new ArrayList<>();
                        itemsByOrder.put(row.getLong(1), items);
                        orders.add(Json.map("id", row.getLong(1), "subtotal", row.getDouble(2), "deliveryFee", row.getDouble(3),
                                "total", row.getDouble(4), "paymentMethod", row.getString(5), "status", row.getString(6),
                                "createdAt", row.getString(7), "items", items));
                    }
                }
            }
            String itemSql = "SELECT oi.order_id, p.name, p.emoji, oi.quantity, oi.unit_price FROM order_items oi"
                    + " JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id WHERE o.user_id = ?";
            try (PreparedStatement query = connection.prepareStatement(itemSql)) {
                query.setLong(1, user.id());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        List<Map<String, Object>> items = itemsByOrder.get(row.getLong(1));
                        if (items != null) {
                            items.add(Json.map("name", row.getString(2), "emoji", row.getString(3),
                                    "quantity", row.getInt(4), "unitPrice", row.getDouble(5)));
                        }
                    }
                }
            }
        }
        Http.json(exchange, 200, Json.map("orders", orders));
    }

    /** A vendor's own shop: products, units sold, sales and payout after commission. */
    private void shopSummary(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        if (!user.isVendor()) throw ApiException.forbidden();
        try (Connection connection = Database.connect()) {
            long vendorId = ProductHandler.ownVendorId(connection, user.id());
            Map<String, Object> shop;
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT v.name, v.location, pl.name, pl.commission_rate FROM vendors v JOIN plans pl ON pl.id = v.plan_id WHERE v.id = ?")) {
                query.setLong(1, vendorId);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    shop = Json.map("name", row.getString(1), "location", row.getString(2), "plan", row.getString(3),
                            "commissionRate", row.getDouble(4));
                }
            }
            List<Map<String, Object>> products = new ArrayList<>();
            double sales = 0;
            double payout = 0;
            String sql = """
                    SELECT p.id, p.name, p.emoji, p.price, p.stock,
                           COALESCE(SUM(oi.quantity), 0), COALESCE(SUM(oi.quantity * oi.unit_price), 0),
                           COALESCE(SUM(oi.quantity * oi.unit_price * (1 - oi.commission_rate)), 0)
                    FROM products p LEFT JOIN order_items oi ON oi.product_id = p.id
                    WHERE p.vendor_id = ? GROUP BY p.id, p.name, p.emoji, p.price, p.stock ORDER BY p.name""";
            try (PreparedStatement query = connection.prepareStatement(sql)) {
                query.setLong(1, vendorId);
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        sales += row.getDouble(7);
                        payout += row.getDouble(8);
                        products.add(Json.map("id", row.getLong(1), "name", row.getString(2), "emoji", row.getString(3),
                                "price", row.getDouble(4), "stock", row.getInt(5), "unitsSold", row.getLong(6),
                                "sales", Pricing.round2(row.getDouble(7))));
                    }
                }
            }
            shop.put("totalSales", Pricing.round2(sales));
            shop.put("totalPayout", Pricing.round2(payout));
            shop.put("products", products);
            Http.json(exchange, 200, Json.map("shop", shop));
        }
    }
}
