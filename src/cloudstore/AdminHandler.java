/*
 * File:    AdminHandler.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The admin dashboard: sales statistics (mean, median, range,
 *          percentages), daily-to-monthly conversions, chart data, recent
 *          orders with status updates, and contact messages.
 */
package cloudstore;

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
            double revenue = 0, merchandise = 0, commission = 0, deliveryFees = 0;
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery(
                         "SELECT total, subtotal, commission, delivery_fee FROM orders WHERE status <> 'Cancelled'")) {
                while (row.next()) {
                    orderTotals.add(row.getDouble(1));
                    revenue += row.getDouble(1);
                    merchandise += row.getDouble(2);
                    commission += row.getDouble(3);
                    deliveryFees += row.getDouble(4);
                }
            }

            // Monthly recurring revenue from subscriptions: every shop's plan plus Plus members.
            double vendorPlanIncome = sum(connection, "SELECT COALESCE(SUM(pl.price_monthly), 0) FROM vendors v JOIN plans pl ON pl.id = v.plan_id");
            double plusIncome = sum(connection, "SELECT COALESCE(SUM(p.price_monthly), 0) FROM subscriptions s JOIN plans p ON p.id = s.plan_id"
                    + " WHERE s.status = 'active' AND p.code = 'plus'");

            // Daily trend for the last 30 days, with empty days filled in as zero.
            Map<String, double[]> daily = new LinkedHashMap<>();
            LocalDate today = LocalDate.parse(scalar(connection, "SELECT DATE_FORMAT(CURDATE(), '%Y-%m-%d')"));
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
                         SELECT p.category, SUM(oi.quantity * oi.unit_price), SUM(oi.quantity) FROM order_items oi
                         JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id
                         WHERE o.status <> 'Cancelled' GROUP BY p.category ORDER BY 2 DESC""")) {
                while (row.next()) {
                    byCategory.add(Json.map("category", row.getString(1), "sales", Pricing.round2(row.getDouble(2)),
                            "units", row.getLong(3), "share", Statistics.percentage(row.getDouble(2), merchandise)));
                }
            }

            List<Map<String, Object>> topProducts = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("""
                         SELECT p.name, p.emoji, SUM(oi.quantity), SUM(oi.quantity * oi.unit_price) FROM order_items oi
                         JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id
                         WHERE o.status <> 'Cancelled' GROUP BY p.id, p.name, p.emoji ORDER BY 3 DESC LIMIT 5""")) {
                while (row.next()) {
                    topProducts.add(Json.map("name", row.getString(1), "emoji", row.getString(2),
                            "units", row.getLong(3), "sales", Pricing.round2(row.getDouble(4))));
                }
            }

            List<Map<String, Object>> plans = new ArrayList<>();
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("""
                         SELECT pl.name, COUNT(v.id) FROM plans pl LEFT JOIN vendors v ON v.plan_id = pl.id
                         WHERE pl.audience = 'vendor' GROUP BY pl.id, pl.name ORDER BY pl.price_monthly""")) {
                while (row.next()) {
                    plans.add(Json.map("plan", row.getString(1), "count", row.getLong(2)));
                }
            }
            plans.add(Json.map("plan", "CloudStore Plus", "count", Database.count(connection,
                    "SELECT COUNT(*) FROM subscriptions s JOIN plans p ON p.id = s.plan_id WHERE s.status = 'active' AND p.code = 'plus'")));

            // Order values grouped into bands, to see where most orders fall.
            int[] bands = new int[4];
            for (double total : orderTotals) {
                if (total < 100) bands[0]++;
                else if (total < 250) bands[1]++;
                else if (total <= Pricing.FREE_DELIVERY_THRESHOLD) bands[2]++;
                else bands[3]++;
            }
            List<Map<String, Object>> orderBands = List.of(
                    Json.map("band", "Under R100", "count", bands[0]),
                    Json.map("band", "R100 – R249", "count", bands[1]),
                    Json.map("band", "R250 – R500", "count", bands[2]),
                    Json.map("band", "Over R500 (free delivery)", "count", bands[3]));

            Http.json(exchange, 200, Json.map(
                    "totals", Json.map(
                            "orders", orderTotals.size(),
                            "revenue", Pricing.round2(revenue),
                            "merchandiseSales", Pricing.round2(merchandise),
                            "commission", Pricing.round2(commission),
                            "deliveryFees", Pricing.round2(deliveryFees),
                            "customers", Database.count(connection, "SELECT COUNT(*) FROM users WHERE role = 'customer'"),
                            "vendors", Database.count(connection, "SELECT COUNT(*) FROM vendors"),
                            "products", Database.count(connection, "SELECT COUNT(*) FROM products"),
                            "messages", Database.count(connection, "SELECT COUNT(*) FROM contact_messages")),
                    "orderValue", Json.map(
                            "mean", Statistics.mean(orderTotals),
                            "median", Statistics.median(orderTotals),
                            "range", Statistics.range(orderTotals),
                            "min", orderTotals.isEmpty() ? 0.0 : orderTotals.stream().mapToDouble(Double::doubleValue).min().getAsDouble(),
                            "max", orderTotals.isEmpty() ? 0.0 : orderTotals.stream().mapToDouble(Double::doubleValue).max().getAsDouble(),
                            "freeDeliveryShare", Statistics.percentage(bands[3], orderTotals.size())),
                    "platformIncome", Json.map(
                            "commission", Pricing.round2(commission),
                            "deliveryFees", Pricing.round2(deliveryFees),
                            "vendorPlansMonthly", Pricing.round2(vendorPlanIncome),
                            "plusMonthly", Pricing.round2(plusIncome),
                            "commissionShareOfSales", Statistics.percentage(commission, merchandise)),
                    "conversions", Json.map(
                            "ordersPerDay", ordersPerDay,
                            "ordersPerMonth", Statistics.dailyToMonthly(ordersPerDay),
                            "revenuePerDay", revenuePerDay,
                            "revenuePerMonth", Statistics.dailyToMonthly(revenuePerDay)),
                    "trend", trend,
                    "byCategory", byCategory,
                    "topProducts", topProducts,
                    "plans", plans,
                    "orderBands", orderBands));
        }
    }

    private void orders(HttpExchange exchange) throws Exception {
        List<Map<String, Object>> orders = new ArrayList<>();
        String sql = "SELECT o.id, u.full_name, o.total, o.payment_method, o.status, DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i')"
                + " FROM orders o JOIN users u ON u.id = o.user_id ORDER BY o.created_at DESC LIMIT 25";
        try (Connection connection = Database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(sql)) {
            while (row.next()) {
                orders.add(Json.map("id", row.getLong(1), "customer", row.getString(2), "total", row.getDouble(3),
                        "paymentMethod", row.getString(4), "status", row.getString(5), "createdAt", row.getString(6)));
            }
        }
        Http.json(exchange, 200, Json.map("orders", orders));
    }

    private void updateStatus(HttpExchange exchange) throws Exception {
        Map<String, String> form = Http.body(exchange);
        int orderId = Validator.intInRange(form.get("id"), "order number", 1, Integer.MAX_VALUE);
        String status = Validator.oneOf(form, "status", "status", "Placed", "Out for delivery", "Delivered", "Cancelled");
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
                     + " FROM contact_messages ORDER BY created_at DESC LIMIT 50")) {
            while (row.next()) {
                messages.add(Json.map("id", row.getLong(1), "name", row.getString(2), "email", row.getString(3),
                        "subject", row.getString(4), "message", row.getString(5), "createdAt", row.getString(6)));
            }
        }
        Http.json(exchange, 200, Json.map("messages", messages));
    }

    private static double sum(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getDouble(1);
        }
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet row = statement.executeQuery(sql)) {
            row.next();
            return row.getString(1);
        }
    }
}
