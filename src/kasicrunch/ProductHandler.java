/*
 * File:    ProductHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The menu: list and search items, list categories, and let the
 *          admin add new menu items.
 */
package kasicrunch;

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

    /** Parameter: the menu categories, in the order they are shown. */
    public static final String[] CATEGORIES = {"Burgers", "Meal Combos", "Chicken", "Sides", "Pies", "Rolls", "Ice Cream", "Drinks"};

    /** Photos admins can pick for a new item (files in public/img/menu). */
    public static final String[] IMAGES = {
        "photo-1568901346375-23c9450c58cd", "photo-1572802419224-296b0aeee0d9", "photo-1606755962773-d324e0a13086",
        "photo-1626082927389-6cd097cdc6ec", "photo-1527477396000-e27163b481c2", "photo-1573080496219-bb080dd4f877",
        "photo-1667499989723-c4ab9549d63c", "photo-1788240260780-fc3b79c05b75", "photo-1539252554453-80ab65ce3586",
        "photo-1560008581-09826d1de69e", "photo-1572490122747-3968b75cc699", "photo-1513558003720-343f3a99d97b",
        "photo-1629654613528-5d0a2e4166de", "photo-1548839140-29a749e1cf4d"};

    private static final String PRODUCT_SELECT =
            "SELECT id, name, description, category, price, stock, image, popular, spicy FROM products";
    private static final String MENU_ORDER =
            " ORDER BY FIELD(category, 'Burgers','Meal Combos','Chicken','Sides','Pies','Rolls','Ice Cream','Drinks'), price";

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        switch (method + " " + path) {
            case "GET /api/products" -> list(exchange);
            case "POST /api/products" -> create(exchange);
            case "GET /api/products/categories" -> Http.json(exchange, 200, Json.map("categories", List.of(CATEGORIES)));
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
            sql.append(" AND (name LIKE ? OR description LIKE ? OR category LIKE ?)");
            String like = "%" + search.replace("%", "\\%").replace("_", "\\_") + "%";
            for (int i = 0; i < 3; i++) params.add(like);
        }
        if (!category.isEmpty()) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        if ("1".equals(query.get("popular"))) {
            sql.append(" AND popular = 1");
        }
        sql.append(MENU_ORDER);

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
                products.add(Json.map(
                        "id", row.getLong(1),
                        "name", row.getString(2),
                        "description", row.getString(3),
                        "category", row.getString(4),
                        "price", row.getDouble(5),
                        "stock", row.getInt(6),
                        "image", "img/menu/" + row.getString(7) + ".jpg",
                        "popular", row.getBoolean(8),
                        "spicy", row.getBoolean(9)));
            }
        }
        return products;
    }

    public static List<Map<String, Object>> search(Connection connection, String term, int limit) throws SQLException {
        String sql = PRODUCT_SELECT + " WHERE name LIKE ? OR description LIKE ? OR category LIKE ? ORDER BY popular DESC, price LIMIT " + limit;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            String like = "%" + term + "%";
            for (int i = 1; i <= 3; i++) statement.setString(i, like);
            return readProducts(statement);
        }
    }

    public static List<Map<String, Object>> where(Connection connection, String condition, int limit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(PRODUCT_SELECT + " WHERE " + condition + " ORDER BY price LIMIT " + limit)) {
            return readProducts(statement);
        }
    }

    private void create(HttpExchange exchange) throws Exception {
        Auth.requireAdmin(exchange);
        Map<String, String> form = Http.body(exchange);
        String name = Validator.text(form, "name", "item name", 2, 100);
        String description = Validator.text(form, "description", "description", 5, 300);
        String category = Validator.oneOf(form, "category", "category", CATEGORIES);
        double price = Validator.amount(form, "price", "price", 1, 5_000);
        double cost = Validator.amount(form, "cost", "food cost", 0, 5_000);
        if (cost >= price) {
            throw ApiException.badRequest("The food cost must be lower than the selling price, otherwise every sale makes a loss.");
        }
        int stock = Validator.intInRange(form.get("stock"), "daily stock", 0, 10_000);
        String image = Validator.oneOf(form, "image", "photo", IMAGES);
        boolean spicy = "1".equals(form.get("spicy"));

        String sql = "INSERT INTO products (name, description, category, price, cost, stock, image, spicy) VALUES (?,?,?,?,?,?,?,?)";
        try (Connection connection = Database.connect();
             PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setString(2, description);
            insert.setString(3, category);
            insert.setDouble(4, price);
            insert.setDouble(5, cost);
            insert.setInt(6, stock);
            insert.setString(7, image);
            insert.setBoolean(8, spicy);
            insert.executeUpdate();
            Http.json(exchange, 201, Json.map("id", Database.generatedId(insert), "message", name + " was added to the menu."));
        }
    }
}
