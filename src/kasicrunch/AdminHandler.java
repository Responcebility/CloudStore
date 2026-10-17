/*
 * File:    AdminHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The admin dashboard: sales and profit statistics (mean, median,
 *          range, percentages, gross margin), daily-to-monthly conversions,
 *          chart data, recent orders with status updates, and messages.
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
    private static final String[] STATUSES = {"Placed", "Preparing", "Out for delivery", "Delivered", "Cancelled"};

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
            double revenue = 0, foodSales = 0, discounts = 0, deliveryFees = 0, foodCost = 0;
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery(
                         "SELECT total, subtotal, discount, delivery_fee, food_cost FROM orders WHERE status <> 'Cancelled'")) {
                while (row.next()) {
                    orderTotals.add(row.getDouble(1));
                    revenue += row.getDouble(1);
                    foodSales += row.getDouble(2);
                    discounts += row.getDouble(3);
                    deliveryFees += row.getDouble(4);
                    foodCost += row.getDouble(5);
                }
            }
            // Food sales after club discounts, minus what the food cost us.
            double netFoodSales = foodSales - discounts;
            double clubIncome = scalarDouble(connection, "SELECT COALESCE(SUM(p.price_monthly), 0) FROM subscriptions s"
                    + " JOIN plans p ON p.id = s.plan_id WHERE s.status = 'active'");

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
                         SELECT p.name, p.emoji, SUM(oi.quantity), SUM(oi.quantity * oi.unit_price) FROM order_items oi
                         JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id
                         WHERE o.status <> 'Cancelled' GROUP BY p.id, p.name, p.emoji ORDER BY 3 DESC LIMIT 5""")) {
                while (row.next()) {
                    topItems.add(Json.map("name", row.getString(1), "emoji", row.getString(2),
                            "units", row.getLong(3), "sales", Pricing.round2(row.getDouble(4))));
                }
            }

            List<Map<String, Object>> clubs = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("""
                         SELECT p.name, COUNT(s.id) FROM plans p
                         LEFT JOIN subscriptions s ON s.plan_id = p.id AND s.status = 'active'
                         GROUP BY p.id, p.name ORDER BY p.price_monthly""")) {
                while (row.next()) {
                    clubs.add(Json.map("club", row.getString(1), "members", row.getLong(2)));
                }
            }

            // Order values grouped into bands, to see where most orders fall.
            int[] bands = new int[3];
            for (double total : orderTotals) {
                if (total < 100) bands[0]++;
                else if (total <= Pricing.FREE_DELIVERY_THRESHOLD) bands[1]++;
                else bands[2]++;
            }
            List<Map<String, Object>> orderBands = List.of(
                    Json.map("band", "Under R100", "count", bands[0]),
                    Json.map("band", "R100 – R250", "count", bands[1]),
                    Json.map("band", "Over R250", "count", bands[2]));

            Http.json(exchange, 200, Json.map(
                    "totals", Json.map(
                            "orders", orderTotals.size(),
                            "revenue", Pricing.round2(revenue),
                            "foodSales", Pricing.round2(foodSales),
                            "discounts", Pricing.round2(discounts),
                            "deliveryFees", Pricing.round2(deliveryFees),
                            "foodCost", Pricing.round2(foodCost),
                            "grossProfit", Pricing.grossProfit(netFoodSales, foodCost),
                            "grossMargin", Pricing.marginPercent(netFoodSales, foodCost),
                            "clubIncomeMonthly", Pricing.round2(clubIncome),
                            "customers", Database.count(connection, "SELECT COUNT(*) FROM users WHERE role = 'customer'"),
                            "menuItems", Database.count(connection, "SELECT COUNT(*) FROM products"),
                            "messages", Database.count(connection, "SELECT COUNT(*) FROM contact_messages")),
                    "orderValue", Json.map(
                            "mean", Statistics.mean(orderTotals),
                            "median", Statistics.median(orderTotals),
                            "range", Statistics.range(orderTotals),
                            "min", orderTotals.isEmpty() ? 0.0 : orderTotals.stream().mapToDouble(Double::doubleValue).min().getAsDouble(),
                            "max", orderTotals.isEmpty() ? 0.0 : orderTotals.stream().mapToDouble(Double::doubleValue).max().getAsDouble(),
                            "freeDeliveryShare", Statistics.percentage(bands[2], orderTotals.size())),
                    "conversions", Json.map(
                            "ordersPerDay", ordersPerDay,
                            "ordersPerMonth", Statistics.dailyToMonthly(ordersPerDay),
                            "revenuePerDay", revenuePerDay,
                            "revenuePerMonth", Statistics.dailyToMonthly(revenuePerDay)),
                    "trend", trend,
                    "byCategory", byCategory,
                    "topItems", topItems,
                    "clubs", clubs,
                    "orderBands", orderBands));
        }
    }

    private void orders(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> orders = new ArrayList<>();
        String sql = "SELECT o.id, u.full_name, o.total, o.payment_method, o.status, DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i')"
                + " FROM orders o JOIN users u ON u.id = o.user_id ORDER BY o.created_at DESC, o.id DESC LIMIT 25";
        try (Connection connection = Database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(sql)) {
            while (row.next()) {
                orders.add(Json.map("id", row.getLong(1), "customer", row.getString(2), "total", row.getDouble(3),
                        "paymentMethod", row.getString(4), "status", row.getString(5), "createdAt", row.getString(6)));
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
