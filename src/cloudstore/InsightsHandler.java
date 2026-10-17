/*
 * File:    InsightsHandler.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Public figures for the website: the pricing parameters the cart
 *          uses, and community impact numbers built only from totals (no
 *          personal data).
 */
package cloudstore;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class InsightsHandler extends ApiHandler {

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "GET /api/pricing" -> Http.json(exchange, 200, Json.map(
                    "deliveryFee", Pricing.DELIVERY_FEE,
                    "freeDeliveryThreshold", Pricing.FREE_DELIVERY_THRESHOLD,
                    "defaultCommissionRate", Pricing.DEFAULT_COMMISSION_RATE,
                    "maxQuantityPerItem", Pricing.MAX_QUANTITY_PER_ITEM));
            case "GET /api/insights" -> insights(exchange);
            default -> throw noRoute();
        }
    }

    private void insights(HttpExchange exchange) throws Exception {
        try (Connection connection = Database.connect()) {
            long products = Database.count(connection, "SELECT COUNT(*) FROM products");
            long vendors = Database.count(connection, "SELECT COUNT(*) FROM vendors");
            long communities = Database.count(connection, "SELECT COUNT(DISTINCT location) FROM vendors");
            long delivered = Database.count(connection, "SELECT COUNT(*) FROM orders WHERE status = 'Delivered'");

            List<Map<String, Object>> categories = new ArrayList<>();
            double totalSales = 0;
            try (Statement statement = connection.createStatement();
                 ResultSet row = statement.executeQuery("""
                         SELECT p.category, SUM(oi.quantity * oi.unit_price) FROM order_items oi
                         JOIN products p ON p.id = oi.product_id JOIN orders o ON o.id = oi.order_id
                         WHERE o.status <> 'Cancelled' GROUP BY p.category ORDER BY 2 DESC""")) {
                while (row.next()) {
                    totalSales += row.getDouble(2);
                    categories.add(Json.map("category", row.getString(1), "sales", row.getDouble(2)));
                }
            }
            // Share of sales per category as a percentage (parts of a whole, so a pie chart fits).
            for (Map<String, Object> category : categories) {
                category.put("share", Statistics.percentage((Double) category.get("sales"), totalSales));
                category.remove("sales");
            }
            Http.json(exchange, 200, Json.map("products", products, "vendors", vendors, "communities", communities,
                    "ordersDelivered", delivered, "categoryShare", categories));
        }
    }
}
