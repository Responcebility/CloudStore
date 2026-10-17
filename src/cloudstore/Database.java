/*
 * File:    Database.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Connects to MySQL, creates the CloudStore schema on first start
 *          and seeds demo data (fictional vendors, products and orders) so
 *          the shop and the dashboard charts have something to show.
 */
package cloudstore;

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
          role ENUM('customer','vendor','admin') NOT NULL DEFAULT 'customer',
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
          audience ENUM('vendor','customer') NOT NULL,
          price_monthly DECIMAL(10,2) NOT NULL,
          commission_rate DECIMAL(5,4) NULL,
          description VARCHAR(255) NOT NULL,
          features TEXT NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS vendors (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(100) NOT NULL,
          location VARCHAR(100) NOT NULL,
          description VARCHAR(255) NOT NULL,
          owner_user_id BIGINT NULL,
          plan_id INT NOT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY (owner_user_id) REFERENCES users(id) ON DELETE SET NULL,
          FOREIGN KEY (plan_id) REFERENCES plans(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS products (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          vendor_id BIGINT NOT NULL,
          name VARCHAR(100) NOT NULL,
          description VARCHAR(300) NOT NULL,
          category VARCHAR(40) NOT NULL,
          price DECIMAL(10,2) NOT NULL,
          stock INT NOT NULL,
          emoji VARCHAR(16) NOT NULL,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          FOREIGN KEY (vendor_id) REFERENCES vendors(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS orders (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          user_id BIGINT NOT NULL,
          subtotal DECIMAL(10,2) NOT NULL,
          delivery_fee DECIMAL(10,2) NOT NULL,
          total DECIMAL(10,2) NOT NULL,
          commission DECIMAL(10,2) NOT NULL,
          payment_method VARCHAR(30) NOT NULL,
          address VARCHAR(200) NOT NULL,
          phone VARCHAR(15) NOT NULL,
          status ENUM('Placed','Out for delivery','Delivered','Cancelled') NOT NULL DEFAULT 'Placed',
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
          commission_rate DECIMAL(5,4) NOT NULL,
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
            long adminId = insertUser(connection, "CloudStore Admin", config.adminEmail(), config.adminPassword(), "admin");
            seedVendorsAndProducts(connection);
            seedDemoOrders(connection);
            connection.commit();
            System.out.println("Seeded demo data. Admin login: " + config.adminEmail() + " (user #" + adminId + ")");
        }
    }

    private static void seedPlans(Connection connection) throws SQLException {
        Object[][] plans = {
            {"starter", "Starter", "vendor", 0.00, 0.10, "List your first products for free and pay only when you sell.",
                "Up to 10 products|10% commission per sale|Shop page on CloudStore|Cash and card on delivery"},
            {"growth", "Growth", "vendor", 149.00, 0.06, "For growing community businesses that sell every week.",
                "Up to 100 products|6% commission per sale|Featured on the home page|Sales summary each month"},
            {"pro", "Pro", "vendor", 349.00, 0.03, "For established vendors who want the lowest commission.",
                "Unlimited products|3% commission per sale|Priority placement in search|Full sales analytics"},
            {"plus", "CloudStore Plus", "customer", 49.00, null, "Free delivery on every order for regular shoppers.",
                "Free delivery on all orders|Early access to weekly specials|Support local vendors every month"},
        };
        String sql = "INSERT INTO plans (code, name, audience, price_monthly, commission_rate, description, features) VALUES (?,?,?,?,?,?,?)";
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

    private static void seedVendorsAndProducts(Connection connection) throws SQLException {
        // Fictional demo vendors from Tshwane-area communities.
        Object[][] vendors = {
            {"Mama Grace's Kitchen", "Soshanguve", "Home-cooked meals and baked goods made fresh every morning.", "growth"},
            {"Thabo's Fresh Produce", "Mabopane", "Vegetables and fruit sourced from small local farms.", "starter"},
            {"Ubuntu Crafts Collective", "Pretoria CBD", "Handmade crafts, beadwork and gifts by local artisans.", "pro"},
            {"Kasi Tech Hub", "Soshanguve", "Affordable phone accessories and refurbished gadgets.", "growth"},
            {"Lerato's Beauty Bar", "Ga-Rankuwa", "Natural hair and skin care products made in small batches.", "starter"},
            {"Campus Corner Stationery", "Pretoria North", "Everything a student needs for lectures and exams.", "starter"},
        };
        List<Long> vendorIds = new ArrayList<>();
        String sql = "INSERT INTO vendors (name, location, description, plan_id) VALUES (?,?,?,(SELECT id FROM plans WHERE code = ?))";
        try (PreparedStatement insert = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (Object[] vendor : vendors) {
                for (int i = 0; i < vendor.length; i++) {
                    insert.setObject(i + 1, vendor[i]);
                }
                insert.executeUpdate();
                vendorIds.add(generatedId(insert));
            }
        }

        Object[][] products = {
            {0, "Beef Stew & Pap", "Slow-cooked beef stew with soft pap and chakalaka.", "Meals", 65.00, 40, "🍲"},
            {0, "Vetkoek Pack (6)", "Six golden vetkoek, perfect with mince or jam.", "Meals", 35.00, 60, "🥯"},
            {0, "Chicken Curry Bunny Chow", "Quarter loaf filled with mild chicken curry.", "Meals", 55.00, 35, "🍛"},
            {1, "Fresh Veggie Box", "Seasonal vegetables for a family of four for one week.", "Groceries", 180.00, 25, "🥬"},
            {1, "Fruit Bowl Pack", "Apples, bananas, oranges and seasonal fruit.", "Groceries", 95.00, 30, "🍎"},
            {1, "Free-Range Eggs (18)", "Eighteen free-range eggs from a farm in Hammanskraal.", "Groceries", 62.00, 50, "🥚"},
            {2, "Beaded Necklace", "Handmade Ndebele-inspired beaded necklace.", "Crafts", 220.00, 15, "📿"},
            {2, "Woven Basket", "Hand-woven storage basket made from natural grass.", "Crafts", 340.00, 10, "🧺"},
            {2, "Hand-Painted Mug", "Ceramic mug painted with township street scenes.", "Crafts", 120.00, 20, "☕"},
            {3, "Fast Phone Charger", "20W USB-C fast charger with a 1 m cable.", "Electronics", 149.00, 40, "🔌"},
            {3, "Wireless Earbuds", "Bluetooth earbuds with charging case and 12-hour battery.", "Electronics", 399.00, 18, "🎧"},
            {3, "Power Bank 10000mAh", "Keeps your phone going through load shedding.", "Electronics", 289.00, 22, "🔋"},
            {4, "Shea Butter Hair Cream", "Moisturising cream for natural hair, 250 ml.", "Beauty", 89.00, 35, "🧴"},
            {4, "Rooibos Face Scrub", "Gentle exfoliating scrub with rooibos extract.", "Beauty", 75.00, 30, "🌿"},
            {4, "Marula Body Oil", "Light body oil made with South African marula.", "Beauty", 110.00, 25, "💧"},
            {5, "A4 Exam Pad Bundle", "Five A4 ruled pads for notes and exam prep.", "Stationery", 79.00, 70, "📒"},
            {5, "Scientific Calculator", "Approved scientific calculator for maths modules.", "Stationery", 249.00, 20, "🧮"},
            {5, "Highlighter Set (6)", "Six bright highlighters for study notes.", "Stationery", 45.00, 80, "🖍️"},
        };
        String productSql = "INSERT INTO products (vendor_id, name, description, category, price, stock, emoji) VALUES (?,?,?,?,?,?,?)";
        try (PreparedStatement insert = connection.prepareStatement(productSql)) {
            for (Object[] product : products) {
                insert.setLong(1, vendorIds.get((Integer) product[0]));
                for (int i = 1; i < product.length; i++) {
                    insert.setObject(i + 1, product[i]);
                }
                insert.executeUpdate();
            }
        }
    }

    /**
     * Fictional customers and about 90 orders over the last 60 days, generated
     * with a fixed seed so every fresh install shows the same demo charts.
     * No real personal data is used.
     */
    private static void seedDemoOrders(Connection connection) throws SQLException {
        String[] names = {"Demo Customer One", "Demo Customer Two", "Demo Customer Three",
                "Demo Customer Four", "Demo Customer Five", "Demo Customer Six"};
        List<Long> customerIds = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            customerIds.add(insertUser(connection, names[i], "demo" + (i + 1) + "@example.com", "Demo@2026", "customer"));
        }

        List<long[]> productRows = new ArrayList<>(); // {id, priceCents, commissionBasisPoints}
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT p.id, p.price, pl.commission_rate FROM products p JOIN vendors v ON v.id = p.vendor_id JOIN plans pl ON pl.id = v.plan_id")) {
            while (rows.next()) {
                productRows.add(new long[]{rows.getLong(1), Math.round(rows.getDouble(2) * 100), Math.round(rows.getDouble(3) * 10000)});
            }
        }

        Random random = new Random(2026);
        String orderSql = "INSERT INTO orders (user_id, subtotal, delivery_fee, total, commission, payment_method, address, phone, status, created_at)"
                + " VALUES (?,?,?,?,?,?,?,?,?, NOW() - INTERVAL ? DAY - INTERVAL ? MINUTE)";
        String itemSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price, commission_rate) VALUES (?,?,?,?,?)";
        try (PreparedStatement orderInsert = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement itemInsert = connection.prepareStatement(itemSql)) {
            for (int n = 0; n < 90; n++) {
                int daysAgo = random.nextInt(60);
                int itemCount = 1 + random.nextInt(3);
                double subtotal = 0;
                double commission = 0;
                List<long[]> chosen = new ArrayList<>();
                for (int i = 0; i < itemCount; i++) {
                    long[] product = productRows.get(random.nextInt(productRows.size()));
                    int quantity = 1 + random.nextInt(3);
                    double lineTotal = product[1] / 100.0 * quantity;
                    subtotal += lineTotal;
                    commission += lineTotal * (product[2] / 10000.0);
                    chosen.add(new long[]{product[0], quantity, product[1], product[2]});
                }
                subtotal = Pricing.round2(subtotal);
                double delivery = Pricing.deliveryFee(subtotal, false);
                String status = daysAgo > 2 ? "Delivered" : (daysAgo > 0 ? "Out for delivery" : "Placed");

                orderInsert.setLong(1, customerIds.get(random.nextInt(customerIds.size())));
                orderInsert.setDouble(2, subtotal);
                orderInsert.setDouble(3, delivery);
                orderInsert.setDouble(4, Pricing.orderTotal(subtotal, delivery));
                orderInsert.setDouble(5, Pricing.round2(commission));
                orderInsert.setString(6, random.nextBoolean() ? "Cash on delivery" : "Card on delivery");
                orderInsert.setString(7, "Demo address, Tshwane");
                orderInsert.setString(8, "0710000000");
                orderInsert.setString(9, status);
                orderInsert.setInt(10, daysAgo);
                orderInsert.setInt(11, random.nextInt(600));
                orderInsert.executeUpdate();
                long orderId = generatedId(orderInsert);

                for (long[] item : chosen) {
                    itemInsert.setLong(1, orderId);
                    itemInsert.setLong(2, item[0]);
                    itemInsert.setLong(3, item[1]);
                    itemInsert.setDouble(4, item[2] / 100.0);
                    itemInsert.setDouble(5, item[3] / 10000.0);
                    itemInsert.executeUpdate();
                }
            }
        }

        // Half the demo customers are CloudStore Plus members.
        String subSql = "INSERT INTO subscriptions (user_id, plan_id) VALUES (?, (SELECT id FROM plans WHERE code = 'plus'))";
        try (PreparedStatement insert = connection.prepareStatement(subSql)) {
            for (int i = 0; i < 3; i++) {
                insert.setLong(1, customerIds.get(i));
                insert.executeUpdate();
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
