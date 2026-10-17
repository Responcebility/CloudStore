/*
 * File:    Database.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Connects to MySQL, creates the Kasi-Crunch schema on first start
 *          and seeds the menu, the club plans and fictional demo orders so the
 *          shop and the dashboard charts have something to show.
 */
package kasicrunch;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class Database {

    private static String url;
    private static String user;
    private static String password;

    private Database() {
    }

    public static Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    /** Creates the database and tables if needed, then seeds an empty database. */
    public static void init(Config config) throws SQLException {
        String base = "jdbc:mysql://" + config.dbHost() + ":" + config.dbPort();
        String options = "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8";
        user = config.dbUser();
        password = config.dbPassword();

        try (Connection server = DriverManager.getConnection(base + "/" + options, user, password);
             Statement statement = server.createStatement()) {
            // The name comes from our own config file; backticks guard odd characters.
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + config.dbName().replace("`", "")
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
        url = base + "/" + config.dbName() + options;

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String sql : SCHEMA) {
                statement.executeUpdate(sql);
            }
        }
        seedIfEmpty(config);
    }

    private static final String[] SCHEMA = {
        """
        CREATE TABLE IF NOT EXISTS users (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          full_name VARCHAR(80) NOT NULL,
          email VARCHAR(120) NOT NULL UNIQUE,
          password_hash VARCHAR(255) NOT NULL,
          role ENUM('customer','admin') NOT NULL DEFAULT 'customer',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS sessions (
          token CHAR(64) PRIMARY KEY,
          user_id BIGINT NOT NULL,
          expires_at DATETIME NOT NULL,
          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS plans (
          id INT AUTO_INCREMENT PRIMARY KEY,
          code VARCHAR(20) NOT NULL UNIQUE,
          name VARCHAR(40) NOT NULL,
          price_monthly DECIMAL(10,2) NOT NULL,
          discount_rate DECIMAL(5,4) NOT NULL,
          free_delivery TINYINT(1) NOT NULL,
          description VARCHAR(255) NOT NULL,
          features TEXT NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS products (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(100) NOT NULL,
          description VARCHAR(300) NOT NULL,
          category VARCHAR(40) NOT NULL,
          price DECIMAL(10,2) NOT NULL,
          cost DECIMAL(10,2) NOT NULL,
          stock INT NOT NULL,
          emoji VARCHAR(16) NOT NULL,
          popular TINYINT(1) NOT NULL DEFAULT 0,
          spicy TINYINT(1) NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS orders (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          user_id BIGINT NOT NULL,
          subtotal DECIMAL(10,2) NOT NULL,
          discount DECIMAL(10,2) NOT NULL,
          delivery_fee DECIMAL(10,2) NOT NULL,
          total DECIMAL(10,2) NOT NULL,
          food_cost DECIMAL(10,2) NOT NULL,
          payment_method VARCHAR(30) NOT NULL,
          address VARCHAR(200) NOT NULL,
          phone VARCHAR(15) NOT NULL,
          status ENUM('Placed','Preparing','Out for delivery','Delivered','Cancelled') NOT NULL DEFAULT 'Placed',
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS order_items (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          order_id BIGINT NOT NULL,
          product_id BIGINT NOT NULL,
          quantity INT NOT NULL,
          unit_price DECIMAL(10,2) NOT NULL,
          unit_cost DECIMAL(10,2) NOT NULL,
          FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
          FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS subscriptions (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          user_id BIGINT NOT NULL,
          plan_id INT NOT NULL,
          status ENUM('active','cancelled') NOT NULL DEFAULT 'active',
          started_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
          FOREIGN KEY (plan_id) REFERENCES plans(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS contact_messages (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(80) NOT NULL,
          email VARCHAR(120) NOT NULL,
          subject VARCHAR(120) NOT NULL,
          message TEXT NOT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"""
    };

    private static void seedIfEmpty(Config config) throws SQLException {
        try (Connection connection = connect()) {
            if (count(connection, "SELECT COUNT(*) FROM plans") > 0) {
                return;
            }
            connection.setAutoCommit(false);
            seedPlans(connection);
            long adminId = insertUser(connection, "Kasi-Crunch Admin", config.adminEmail(), config.adminPassword(), "admin");
            seedMenu(connection);
            seedDemoOrders(connection);
            connection.commit();
            System.out.println("Seeded demo data. Admin login: " + config.adminEmail() + " (user #" + adminId + ")");
        }
    }

    private static void seedPlans(Connection connection) throws SQLException {
        Object[][] plans = {
            {"crunch-club", "Crunch Club", 49.00, 0.00, 1, "Free delivery on every order, no minimum.",
                "Free delivery on every order|No minimum order|Cancel any time"},
            {"family-club", "Family Club", 129.00, 0.10, 1, "Free delivery plus 10% off every order — made for families and digs.",
                "Free delivery on every order|10% off every order|Early access to new menu items|Cancel any time"},
        };
        String sql = "INSERT INTO plans (code, name, price_monthly, discount_rate, free_delivery, description, features) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (Object[] plan : plans) {
                for (int i = 0; i < plan.length; i++) {
                    insert.setObject(i + 1, plan[i]);
                }
                insert.executeUpdate();
            }
        }
    }

    public static long insertUser(Connection connection, String name, String email, String plainPassword, String role) throws SQLException {
        String sql = "INSERT INTO users (full_name, email, password_hash, role) VALUES (?,?,?,?)";
        try (PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            insert.setString(1, name);
            insert.setString(2, email);
            insert.setString(3, PasswordHasher.hash(plainPassword));
            insert.setString(4, role);
            insert.executeUpdate();
            return generatedId(insert);
        }
    }

    private static void seedMenu(Connection connection) throws SQLException {
        // {name, description, category, price, cost, daily stock, emoji, popular, spicy}
        Object[][] menu = {
            {"2-Piece Crunch", "Two pieces of our signature crunchy chicken with a small slap chips.", "Chicken", 49.90, 21.50, 120, "🍗", 1, 0},
            {"4-Piece Bucket", "Four golden pieces — crunch outside, juicy inside.", "Chicken", 89.90, 39.00, 80, "🍗", 0, 0},
            {"8-Piece Family Bucket", "Eight pieces to share with the whole family.", "Chicken", 169.90, 76.00, 50, "🍗", 1, 0},
            {"Hot Wings (6)", "Six wings tossed in our peri-peri fire sauce.", "Chicken", 59.90, 24.00, 90, "🌶️", 1, 1},
            {"Crunch Strips (5)", "Five boneless strips with a dipping sauce.", "Chicken", 54.90, 23.00, 90, "🍗", 0, 0},
            {"Crunch Burger", "Crispy fillet, lettuce, tomato and our house mayo on a toasted bun.", "Burgers & Wraps", 59.90, 25.50, 80, "🍔", 1, 0},
            {"Fire Burger", "Crispy fillet with peri-peri sauce, jalapeños and cheese.", "Burgers & Wraps", 64.90, 27.50, 60, "🍔", 0, 1},
            {"Chicken Wrap", "Strips, slaw and sauce rolled in a warm tortilla.", "Burgers & Wraps", 54.90, 22.50, 60, "🌯", 0, 0},
            {"Crunch Kota", "Quarter loaf with chips, chicken strips, polony, cheese and atchar.", "Kasi Specials", 54.90, 23.50, 100, "🥪", 1, 0},
            {"Pap & Wings", "Soft pap, chakalaka and four flame-grilled wings.", "Kasi Specials", 59.90, 24.50, 70, "🍽️", 0, 1},
            {"Student Saver", "2-piece chicken, regular chips and a 330 ml soft drink.", "Combos", 64.90, 28.00, 120, "🥡", 1, 0},
            {"Family Feast", "8 pieces, large chips, 4 rolls, coleslaw and a 2 L soft drink.", "Combos", 249.90, 110.00, 40, "🎉", 0, 0},
            {"Slap Chips (Large)", "Soft, vinegary kasi-style slap chips.", "Sides", 29.90, 9.50, 150, "🍟", 0, 0},
            {"Creamy Coleslaw", "Fresh cabbage and carrot slaw.", "Sides", 19.90, 6.50, 100, "🥗", 0, 0},
            {"Pap & Chakalaka", "Soft pap with spicy chakalaka relish.", "Sides", 24.90, 8.00, 100, "🍲", 0, 1},
            {"Dinner Rolls (4)", "Four soft, warm rolls.", "Sides", 14.90, 4.50, 120, "🥖", 0, 0},
            {"Soft Drink 330 ml", "Ice-cold can, assorted flavours.", "Drinks", 14.90, 7.00, 200, "🥤", 0, 0},
            {"Soft Drink 2 L", "Two litres to share.", "Drinks", 29.90, 15.00, 80, "🥤", 0, 0},
            {"Iced Rooibos 500 ml", "Lightly sweetened rooibos iced tea.", "Drinks", 19.90, 6.00, 80, "🧋", 0, 0},
            {"Soft-Serve Cone", "Creamy vanilla soft-serve.", "Desserts", 12.90, 4.00, 100, "🍦", 0, 0},
            {"Koeksisters (2)", "Two syrupy, plaited koeksisters.", "Desserts", 15.90, 5.50, 60, "🥨", 0, 0},
        };
        String sql = "INSERT INTO products (name, description, category, price, cost, stock, emoji, popular, spicy) VALUES (?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            for (Object[] item : menu) {
                for (int i = 0; i < item.length; i++) {
                    insert.setObject(i + 1, item[i]);
                }
                insert.executeUpdate();
            }
        }
    }

    /**
     * Fictional customers and about 120 orders over the last 60 days, generated
     * with a fixed seed so every fresh install shows the same demo charts.
     * No real personal data is used.
     */
    private static void seedDemoOrders(Connection connection) throws SQLException {
        List<Long> customerIds = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            customerIds.add(insertUser(connection, "Demo Customer " + i, "demo" + i + "@example.com", "Demo@2026", "customer"));
        }

        List<double[]> menu = new ArrayList<>(); // {id, price, cost}
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, price, cost FROM products")) {
            while (rows.next()) {
                menu.add(new double[]{rows.getLong(1), rows.getDouble(2), rows.getDouble(3)});
            }
        }

        // Demo customers 1-2 are Crunch Club members, 3 is in the Family Club.
        String subSql = "INSERT INTO subscriptions (user_id, plan_id, started_at) VALUES (?, (SELECT id FROM plans WHERE code = ?), NOW() - INTERVAL 70 DAY)";
        String[] clubs = {"crunch-club", "crunch-club", "family-club"};
        try (PreparedStatement insert = connection.prepareStatement(subSql)) {
            for (int i = 0; i < clubs.length; i++) {
                insert.setLong(1, customerIds.get(i));
                insert.setString(2, clubs[i]);
                insert.executeUpdate();
            }
        }

        Random random = new Random(2026);
        String orderSql = "INSERT INTO orders (user_id, subtotal, discount, delivery_fee, total, food_cost, payment_method, address, phone, status, created_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?,?, NOW() - INTERVAL ? DAY - INTERVAL ? MINUTE)";
        String itemSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price, unit_cost) VALUES (?,?,?,?,?)";
        try (PreparedStatement orderInsert = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement itemInsert = connection.prepareStatement(itemSql)) {
            for (int n = 0; n < 120; n++) {
                int daysAgo = random.nextInt(60);
                int customer = random.nextInt(customerIds.size());
                int lines = 1 + random.nextInt(3);
                double subtotal = 0;
                double cost = 0;
                List<double[]> chosen = new ArrayList<>();
                for (int i = 0; i < lines; i++) {
                    double[] item = menu.get(random.nextInt(menu.size()));
                    int quantity = 1 + random.nextInt(2);
                    subtotal += item[1] * quantity;
                    cost += item[2] * quantity;
                    chosen.add(new double[]{item[0], quantity, item[1], item[2]});
                }
                subtotal = Pricing.round2(subtotal);
                double discountRate = customer == 2 ? 0.10 : 0.0;
                boolean freeDelivery = customer <= 2;
                double discount = Pricing.discount(subtotal, discountRate);
                double delivery = Pricing.deliveryFee(subtotal, freeDelivery);
                String status = daysAgo > 1 ? "Delivered" : (daysAgo == 1 ? "Out for delivery" : "Preparing");

                orderInsert.setLong(1, customerIds.get(customer));
                orderInsert.setDouble(2, subtotal);
                orderInsert.setDouble(3, discount);
                orderInsert.setDouble(4, delivery);
                orderInsert.setDouble(5, Pricing.orderTotal(subtotal, discount, delivery));
                orderInsert.setDouble(6, Pricing.round2(cost));
                orderInsert.setString(7, random.nextBoolean() ? "Cash on delivery" : "Card on delivery");
                orderInsert.setString(8, "Demo address, Soshanguve");
                orderInsert.setString(9, "0710000000");
                orderInsert.setString(10, status);
                orderInsert.setInt(11, daysAgo);
                orderInsert.setInt(12, random.nextInt(600));
                orderInsert.executeUpdate();
                long orderId = generatedId(orderInsert);

                for (double[] item : chosen) {
                    itemInsert.setLong(1, orderId);
                    itemInsert.setLong(2, (long) item[0]);
                    itemInsert.setInt(3, (int) item[1]);
                    itemInsert.setDouble(4, item[2]);
                    itemInsert.setDouble(5, item[3]);
                    itemInsert.executeUpdate();
                }
            }
        }
    }

    public static long count(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            result.next();
            return result.getLong(1);
        }
    }

    public static long generatedId(PreparedStatement statement) throws SQLException {
        try (ResultSet keys = statement.getGeneratedKeys()) {
            keys.next();
            return keys.getLong(1);
        }
    }
}
