/*
 * File:    ProductHandler.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The catalogue: list and search products, list categories and
 *          vendors, and let vendors (or the admin) add new products.
 */
package cloudstore;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ProductHandler extends ApiHandler {

    /** Parameter: categories a product may belong to. */
    public static final String[] CATEGORIES = {"Meals", "Groceries", "Crafts", "Electronics", "Beauty", "Stationery"};

    private static final String PRODUCT_SELECT = """
            SELECT p.id, p.name, p.description, p.category, p.price, p.stock, p.emoji,
                   v.id, v.name, v.location, pl.code
            FROM products p
            JOIN vendors v ON v.id = p.vendor_id
            JOIN plans pl ON pl.id = v.plan_id
            """;

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "GET /api/products" -> list(exchange);
            case "POST /api/products" -> create(exchange);
            case "GET /api/products/categories" -> Http.json(exchange, 200, Json.map("categories", List.of(CATEGORIES)));
            case "GET /api/vendors" -> vendors(exchange);
            default -> throw noRoute();
        }
    }

    private void list(HttpExchange exchange) throws Exception {
        Map<String, String> query = Http.query(exchange);
        String search = query.getOrDefault("search", "").trim();
        String category = query.getOrDefault("category", "").trim();
        if (search.length() > 60) {
            throw ApiException.badRequest("Please use a shorter search of up to 60 characters.");
        }

        StringBuilder sql = new StringBuilder(PRODUCT_SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (!search.isEmpty()) {
            sql.append(" AND (p.name LIKE ? OR p.description LIKE ? OR p.category LIKE ? OR v.name LIKE ?)");
            String like = "%" + search.replace("%", "\\%").replace("_", "\\_") + "%";
            for (int i = 0; i < 4; i++) params.add(like);
        }
        if (!category.isEmpty()) {
            sql.append(" AND p.category = ?");
            params.add(category);
        }
        if (query.containsKey("vendor")) {
            sql.append(" AND v.id = ?");
            params.add(Validator.intInRange(query.get("vendor"), "vendor number", 1, Integer.MAX_VALUE));
        }
        // Vendors on paid plans are featured first, as promised on the Revenue Model page.
        sql.append(" ORDER BY (pl.code IN ('growth','pro')) DESC, p.created_at DESC, p.id");

        try (Connection connection = Database.connect(); PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                statement.setObject(i + 1, params.get(i));
            }
            Http.json(exchange, 200, Json.map("products", readProducts(statement)));
        }
    }

    public static List<Map<String, Object>> readProducts(PreparedStatement statement) throws SQLException {
        List<Map<String, Object>> products = new ArrayList<>();
        try (ResultSet row = statement.executeQuery()) {
            while (row.next()) {
                String plan = row.getString(11);
                products.add(Json.map(
                        "id", row.getLong(1),
                        "name", row.getString(2),
                        "description", row.getString(3),
                        "category", row.getString(4),
                        "price", row.getDouble(5),
                        "stock", row.getInt(6),
                        "emoji", row.getString(7),
                        "vendorId", row.getLong(8),
                        "vendorName", row.getString(9),
                        "vendorLocation", row.getString(10),
                        "featured", "growth".equals(plan) || "pro".equals(plan)));
            }
        }
        return products;
    }

    public static List<Map<String, Object>> search(Connection connection, String term, int limit) throws SQLException {
        String sql = PRODUCT_SELECT + " WHERE p.name LIKE ? OR p.description LIKE ? OR p.category LIKE ? ORDER BY p.price LIMIT " + limit;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String like = "%" + term + "%";
            for (int i = 1; i <= 3; i++) statement.setString(i, like);
            return readProducts(statement);
        }
    }

    private void vendors(HttpExchange exchange) throws Exception {
        String sql = """
                SELECT v.id, v.name, v.location, v.description, pl.name, COUNT(p.id)
                FROM vendors v JOIN plans pl ON pl.id = v.plan_id
                LEFT JOIN products p ON p.vendor_id = v.id
                GROUP BY v.id, v.name, v.location, v.description, pl.name
                ORDER BY v.name""";
        List<Map<String, Object>> vendors = new ArrayList<>();
        try (Connection connection = Database.connect(); Statement statement = connection.createStatement();
             ResultSet row = statement.executeQuery(sql)) {
            while (row.next()) {
                vendors.add(Json.map("id", row.getLong(1), "name", row.getString(2), "location", row.getString(3),
                        "description", row.getString(4), "plan", row.getString(5), "productCount", row.getLong(6)));
            }
        }
        Http.json(exchange, 200, Json.map("vendors", vendors));
    }

    /** Vendors add to their own shop; the admin may add to any shop. */
    private void create(HttpExchange exchange) throws Exception {
        Auth.User user = Auth.require(exchange);
        if (!user.isVendor() && !user.isAdmin()) {
            throw new ApiException(403, "Only vendors can add products. Choose a vendor plan on the Revenue Model page to open a shop.");
        }
        Map<String, String> form = Http.body(exchange);
        String name = Validator.text(form, "name", "product name", 2, 100);
        String description = Validator.text(form, "description", "description", 5, 300);
        String category = Validator.oneOf(form, "category", "category", CATEGORIES);
        double price = Validator.amount(form, "price", "price", 1, 50_000);
        int stock = Validator.intInRange(form.get("stock"), "stock quantity", 0, 10_000);
        String emoji = form.getOrDefault("emoji", "").trim();
        if (emoji.isEmpty() || emoji.length() > 8) emoji = "🛍️";

        try (Connection connection = Database.connect()) {
            long vendorId;
            String planCode;
            if (user.isAdmin()) {
                vendorId = Validator.intInRange(form.get("vendorId"), "vendor", 1, Integer.MAX_VALUE);
                planCode = vendorPlan(connection, "SELECT pl.code FROM vendors v JOIN plans pl ON pl.id = v.plan_id WHERE v.id = ?", vendorId);
            } else {
                vendorId = ownVendorId(connection, user.id());
                planCode = vendorPlan(connection, "SELECT pl.code FROM vendors v JOIN plans pl ON pl.id = v.plan_id WHERE v.id = ?", vendorId);
            }
            enforcePlanLimit(connection, vendorId, planCode);

            String sql = "INSERT INTO products (vendor_id, name, description, category, price, stock, emoji) VALUES (?,?,?,?,?,?,?)";
            try (PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                insert.setLong(1, vendorId);
                insert.setString(2, name);
                insert.setString(3, description);
                insert.setString(4, category);
                insert.setDouble(5, price);
                insert.setInt(6, stock);
                insert.setString(7, emoji);
                insert.executeUpdate();
                Http.json(exchange, 201, Json.map("id", Database.generatedId(insert), "message", name + " was added to the shop."));
            }
        }
    }

    public static long ownVendorId(Connection connection, long userId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT id FROM vendors WHERE owner_user_id = ?")) {
            query.setLong(1, userId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw ApiException.notFound("We could not find your shop. Please choose a vendor plan first.");
                return row.getLong(1);
            }
        }
    }

    private static String vendorPlan(Connection connection, String sql, long vendorId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setLong(1, vendorId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw ApiException.notFound("That vendor does not exist.");
                return row.getString(1);
            }
        }
    }

    /** Starter shops may list 10 products, Growth 100, Pro unlimited. */
    private static void enforcePlanLimit(Connection connection, long vendorId, String planCode) throws SQLException {
        int limit = switch (planCode) {
            case "starter" -> 10;
            case "growth" -> 100;
            default -> Integer.MAX_VALUE;
        };
        try (PreparedStatement query = connection.prepareStatement("SELECT COUNT(*) FROM products WHERE vendor_id = ?")) {
            query.setLong(1, vendorId);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                if (row.getLong(1) >= limit) {
                    throw ApiException.badRequest("This shop has reached its plan limit of " + limit
                            + " products. Upgrade the plan on the Revenue Model page to add more.");
                }
            }
        }
    }
}
