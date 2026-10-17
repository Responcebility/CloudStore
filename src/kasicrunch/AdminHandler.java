/*
 * File:    AdminHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The admin dashboard: sales and profit statistics (mean, median,
 *          range, percentages, gross margin), delivery distances and Crunch
 *          Pass figures, daily-to-monthly conversions, chart data, recent
 *          orders with status updates, and contact messages.
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AdminHandler extends ApiHandler {

    private static final int TREND_DAYS = 30;
    private static final String[] STATUSES = {"Placed", "Preparing", "Ready", "Out for delivery", "Delivered", "Collected", "Cancelled"};

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        Auth.requireAdmin(exchange);
        switch (method + " " + path) {
            case "GET /api/admin/stats" -> stats(exchange);
            case "GET /api/admin/orders" -> orders(exchange);
            case "POST /api/admin/orders/status" -> updateStatus(exchange);
            case "GET /api/admin/messages" -> messages(exchange);
            default -> throw noRoute();
        }
    }

    private void stats(HttpExchange exchange) throws Exception {
        try (Connection connection = Database.connect()) {
            List<Double> orderTotals = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            double revenue = 0, foodSales = 0, deliveryFees = 0, foodCost = 0, waivedFees = 0;
            int deliveries = 0, collections = 0;
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery(
                         "SELECT total, subtotal, delivery_fee, food_cost, fulfilment, distance_km, pass_km FROM orders WHERE status <> 'Cancelled'")) {
                while (row.next()) {
                    orderTotals.add(row.getDouble(1));
                    revenue += row.getDouble(1);
                    foodSales += row.getDouble(2);
                    deliveryFees += row.getDouble(3);
                    foodCost += row.getDouble(4);
                    if ("delivery".equals(row.getString(5))) {
                        deliveries++;
                        distances.add(row.getDouble(6));
                        // What the customer would have paid without their pass.
                        waivedFees += Pricing.deliveryFee(row.getDouble(6), 0) - row.getDouble(3);
                    } else {
                        collections++;
                    }
                }
            }

            double passIncome = scalarDouble(connection, "SELECT COALESCE(SUM(price), 0) FROM passes WHERE status = 'active' AND ends_at > NOW()");
            long activePasses = Database.count(connection, "SELECT COUNT(*) FROM passes WHERE status = 'active' AND ends_at > NOW()");
            List<Double> passKms = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("SELECT max_km FROM passes WHERE status = 'active' AND ends_at > NOW()")) {
                while (row.next()) passKms.add((double) row.getInt(1));
            }

            // Daily trend for the last 30 days, with empty days filled in as zero.
            Map<String, double[]> daily = new LinkedHashMap<>();
            LocalDate today = LocalDate.parse(scalarText(connection, "SELECT DATE_FORMAT(CURDATE(), '%Y-%m-%d')"));
            for (int i = TREND_DAYS - 1; i >= 0; i--) {
                daily.put(today.minusDays(i).toString(), new double[]{0, 0});
            }
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("SELECT DATE_FORMAT(created_at, '%Y-%m-%d'), COUNT(*), SUM(total) FROM orders"
                         + " WHERE status <> 'Cancelled' AND created_at >= CURDATE() - INTERVAL " + (TREND_DAYS - 1) + " DAY GROUP BY 1")) {
                while (row.next()) {
                    double[] day = daily.get(row.getString(1));
                    if (day != null) {
                        day[0] = row.getLong(2);
                        day[1] = row.getDouble(3);
                    }
                }
            }
            List<Map<String, Object>> trend = new ArrayList<>();
            double recentOrders = 0, recentRevenue = 0;
            for (Map.Entry<String, double[]> day : daily.entrySet()) {
                recentOrders += day.getValue()[0];
                recentRevenue += day.getValue()[1];
                trend.add(Json.map("date", day.getKey(), "orders", (long) day.getValue()[0], "revenue", Pricing.round2(day.getValue()[1])));
            }
            double ordersPerDay = Pricing.round2(recentOrders / TREND_DAYS);
            double revenuePerDay = Pricing.round2(recentRevenue / TREND_DAYS);

            List<Map<String, Object>> byCategory = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("""
                         SELECT p.category, SUM(oi.quantity * oi.unit_price), SUM(oi.quantity * oi.unit_cost) FROM order_items oi
                         JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id
                         WHERE o.status <> 'Cancelled' GROUP BY p.category ORDER BY 2 DESC""")) {
                while (row.next()) {
                    byCategory.add(Json.map("category", row.getString(1), "sales", Pricing.round2(row.getDouble(2)),
                            "margin", Pricing.marginPercent(row.getDouble(2), row.getDouble(3)),
                            "share", Statistics.percentage(row.getDouble(2), foodSales)));
                }
            }

            List<Map<String, Object>> topItems = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("""
                         SELECT p.name, SUM(oi.quantity), SUM(oi.quantity * oi.unit_price) FROM order_items oi
                         JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id
                         WHERE o.status <> 'Cancelled' GROUP BY p.id, p.name ORDER BY 2 DESC LIMIT 5""")) {
                while (row.next()) {
                    topItems.add(Json.map("name", row.getString(1), "units", row.getLong(2), "sales", Pricing.round2(row.getDouble(3))));
                }
            }

            // Delivery distances grouped into bands.
            int[] distanceBands = new int[4];
            for (double km : distances) {
                if (km <= 3) distanceBands[0]++;
                else if (km <= 6) distanceBands[1]++;
                else if (km <= 9) distanceBands[2]++;
                else distanceBands[3]++;
            }
            List<Map<String, Object>> distanceGroups = List.of(
                    Json.map("band", "0–3 km", "count", distanceBands[0]),
                    Json.map("band", "3–6 km", "count", distanceBands[1]),
                    Json.map("band", "6–9 km", "count", distanceBands[2]),
                    Json.map("band", "Over 9 km", "count", distanceBands[3]));

            int orderCount = orderTotals.size();
            Http.json(exchange, 200, Json.map(
                    "totals", Json.map(
                            "orders", orderCount,
                            "revenue", Pricing.round2(revenue),
                            "foodSales", Pricing.round2(foodSales),
                            "deliveryFees", Pricing.round2(deliveryFees),
                            "foodCost", Pricing.round2(foodCost),
                            "grossProfit", Pricing.grossProfit(foodSales, foodCost),
                            "grossMargin", Pricing.marginPercent(foodSales, foodCost),
                            "customers", Database.count(connection, "SELECT COUNT(*) FROM users WHERE role = 'customer'"),
                            "menuItems", Database.count(connection, "SELECT COUNT(*) FROM products"),
                            "messages", Database.count(connection, "SELECT COUNT(*) FROM contact_messages")),
                    "orderValue", Json.map(
                            "mean", Statistics.mean(orderTotals),
                            "median", Statistics.median(orderTotals),
                            "range", Statistics.range(orderTotals),
                            "min", orderTotals.isEmpty() ? 0.0 : orderTotals.stream().mapToDouble(Double::doubleValue).min().getAsDouble(),
                            "max", orderTotals.isEmpty() ? 0.0 : orderTotals.stream().mapToDouble(Double::doubleValue).max().getAsDouble()),
                    "delivery", Json.map(
                            "deliveries", deliveries,
                            "collections", collections,
                            "deliveryShare", Statistics.percentage(deliveries, orderCount),
                            "meanDistance", Statistics.mean(distances),
                            "medianDistance", Statistics.median(distances),
                            "distanceRange", Statistics.range(distances),
                            "feesWaivedByPasses", Pricing.round2(waivedFees)),
                    "passes", Json.map(
                            "active", activePasses,
                            "incomeThisMonth", Pricing.round2(passIncome),
                            "meanKm", Statistics.mean(passKms)),
                    "conversions", Json.map(
                            "ordersPerDay", ordersPerDay,
                            "ordersPerMonth", Statistics.dailyToMonthly(ordersPerDay),
                            "revenuePerDay", revenuePerDay,
                            "revenuePerMonth", Statistics.dailyToMonthly(revenuePerDay)),
                    "trend", trend,
                    "byCategory", byCategory,
                    "topItems", topItems,
                    "distanceGroups", distanceGroups));
        }
    }

    private void orders(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> orders = new ArrayList<>();
        String sql = "SELECT o.id, u.full_name, o.fulfilment, o.distance_km, o.total, o.status, DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i')"
                + " FROM orders o JOIN users u ON u.id = o.user_id ORDER BY o.created_at DESC, o.id DESC LIMIT 25";
        try (Connection connection = Database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(sql)) {
            while (row.next()) {
                orders.add(Json.map("id", row.getLong(1), "customer", row.getString(2), "fulfilment", row.getString(3),
                        "distanceKm", row.getDouble(4), "total", row.getDouble(5), "status", row.getString(6), "createdAt", row.getString(7)));
            }
        }
        Http.json(exchange, 200, Json.map("orders", orders, "statuses", List.of(STATUSES)));
    }

    private void updateStatus(HttpExchange exchange) throws Exception {
        Map<String, String> form = Http.body(exchange);
        int orderId = Validator.intInRange(form.get("id"), "order number", 1, Integer.MAX_VALUE);
        String status = Validator.oneOf(form, "status", "status", STATUSES);
        try (Connection connection = Database.connect();
             PreparedStatement update = connection.prepareStatement("UPDATE orders SET status = ? WHERE id = ?")) {
            update.setString(1, status);
            update.setInt(2, orderId);
            if (update.executeUpdate() == 0) throw ApiException.notFound("Order #" + orderId + " does not exist.");
        }
        Http.json(exchange, 200, Json.map("message", "Order #" + orderId + " is now marked as " + status + "."));
    }

    private void messages(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> messages = new ArrayList<>();
        try (Connection connection = Database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery("SELECT id, name, email, subject, message, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i')"
                     + " FROM contact_messages ORDER BY created_at DESC, id DESC LIMIT 50")) {
            while (row.next()) {
                messages.add(Json.map("id", row.getLong(1), "name", row.getString(2), "email", row.getString(3),
                        "subject", row.getString(4), "message", row.getString(5), "createdAt", row.getString(6)));
            }
        }
        Http.json(exchange, 200, Json.map("messages", messages));
    }

    private static double scalarDouble(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getDouble(1);
        }
    }

    private static String scalarText(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getString(1);
        }
    }
}
