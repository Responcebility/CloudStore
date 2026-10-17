/*
 * File:    Database.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Connects to MySQL, creates the Kasi-Crunch schema on first start
 *          and seeds the menu and fictional demo orders and passes so the
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
        CREATE TABLE IF NOT EXISTS products (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          name VARCHAR(100) NOT NULL,
          description VARCHAR(300) NOT NULL,
          category VARCHAR(40) NOT NULL,
          price DECIMAL(10,2) NOT NULL,
          cost DECIMAL(10,2) NOT NULL,
          stock INT NOT NULL,
          image VARCHAR(120) NOT NULL,
          popular TINYINT(1) NOT NULL DEFAULT 0,
          spicy TINYINT(1) NOT NULL DEFAULT 0,
          created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS passes (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          user_id BIGINT NOT NULL,
          max_km INT NOT NULL,
          price DECIMAL(10,2) NOT NULL,
          starts_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
          ends_at DATETIME NOT NULL,
          status ENUM('active','cancelled') NOT NULL DEFAULT 'active',
          FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4""",
        """
        CREATE TABLE IF NOT EXISTS orders (
          id BIGINT AUTO_INCREMENT PRIMARY KEY,
          user_id BIGINT NOT NULL,
          fulfilment ENUM('delivery','collection') NOT NULL,
          distance_km DECIMAL(5,2) NOT NULL DEFAULT 0,
          pass_km INT NOT NULL DEFAULT 0,
          subtotal DECIMAL(10,2) NOT NULL,
          delivery_fee DECIMAL(10,2) NOT NULL,
          total DECIMAL(10,2) NOT NULL,
          food_cost DECIMAL(10,2) NOT NULL,
          payment_method VARCHAR(30) NOT NULL,
          address VARCHAR(200) NULL,
          phone VARCHAR(15) NOT NULL,
          status ENUM('Placed','Preparing','Ready','Out for delivery','Delivered','Collected','Cancelled') NOT NULL DEFAULT 'Placed',
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
            if (count(connection, "SELECT COUNT(*) FROM products") > 0) {
                return;
            }
            connection.setAutoCommit(false);
            long adminId = insertUser(connection, "Kasi-Crunch Admin", config.adminEmail(), config.adminPassword(), "admin");
            seedMenu(connection);
            seedDemoOrders(connection);
            connection.commit();
            System.out.println("Seeded demo data. Admin login: " + config.adminEmail() + " (user #" + adminId + ")");
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
        // Photos (Unsplash, free licence) are stored in public/img/menu/<image>.jpg.
        String classic = "photo-1568901346375-23c9450c58cd";
        String doubleBurger = "photo-1572802419224-296b0aeee0d9";
        String spicyBurger = "photo-1606755962773-d324e0a13086";
        String chicken = "photo-1626082927389-6cd097cdc6ec";
        String wings = "photo-1527477396000-e27163b481c2";
        String fries = "photo-1573080496219-bb080dd4f877";
        String mac = "photo-1667499989723-c4ab9549d63c";
        String chickenPie = "photo-1581986675622-41eccf27129d";
        String steakPie = "photo-1546024664-9226c2d3819b";
        String classicCombo = "photo-1561758033-d89a9ad46330";
        String doubleCombo = "photo-1767065703448-711ccce86798";
        String spicyCombo = "photo-1606149059549-6042addafc5a";
        String crunchBox = "photo-1580217593608-61931cefc821";
        String bucket = "photo-1426869981800-95ebf51ce900";
        String wingsCombo = "photo-1578874557108-9fc2cfb1121e";
        String loadedChips = "photo-1639744210631-209fce3e256c";
        String wrap = "photo-1626700051175-6818013e1d4f";
        String can = "photo-1696739696220-8d2e27465662";
        String colaPour = "photo-1629186235045-80d4147d90dc";
        String guava = "photo-1752550901046-ad49e6063dd7";
        String roll = "photo-1539252554453-80ab65ce3586";
        String iceCream = "photo-1560008581-09826d1de69e";
        String shake = "photo-1572490122747-3968b75cc699";
        String lemonade = "photo-1513558003720-343f3a99d97b";
        String cola = "photo-1629654613528-5d0a2e4166de";
        String water = "photo-1548839140-29a749e1cf4d";

        // {name, description, category, price, food cost, daily stock, image, popular, spicy}
        Object[][] menu = {
            {"The Kasi Classic", "Crispy chicken, slaw, pickles and our signature sauce in a toasted bun.", "Burgers", 82, 34, 120, classic, 1, 0},
            {"Double Trouble", "Two flame-grilled patties, melted cheese, caramelised onion and smoky sauce.", "Burgers", 109, 46, 80, doubleBurger, 0, 0},
            {"Spicy Crunch Burger", "Golden crunchy chicken, fresh lettuce and a proper peri-peri kick.", "Burgers", 89, 37, 100, spicyBurger, 1, 1},
            {"Kasi Classic Combo", "The Kasi Classic with fries and a cool drink.", "Meal Combos", 119, 50, 100, classicCombo, 1, 0},
            {"Double Trouble Combo", "Double Trouble with fries and a cool drink.", "Meal Combos", 149, 63, 70, doubleCombo, 0, 0},
            {"Spicy Crunch Combo", "A spicy chicken burger with fries and a cool drink.", "Meal Combos", 129, 54, 80, spicyCombo, 0, 1},
            {"Kasi Crunch Box Meal", "Crispy chicken pieces, fries, creamy slaw, a soft roll and a cool drink.", "Meal Combos", 139, 58, 90, crunchBox, 1, 0},
            {"Street Wings Combo", "Six house-sauced wings with fries and a cool drink.", "Meal Combos", 119, 49, 80, wingsCombo, 0, 0},
            {"3-Piece Crunch Chicken", "Three pieces of our signature chicken: crunch outside, juicy inside.", "Chicken", 69, 28, 120, chicken, 1, 0},
            {"Family Bucket (8 pieces)", "Eight golden pieces to share with the whole family.", "Chicken", 189, 82, 50, bucket, 0, 0},
            {"Street Wings (6)", "Six smoky wings tossed in our house sauce.", "Chicken", 76, 31, 100, wings, 1, 1},
            {"Hand-Cut Fries", "Golden, lightly seasoned fries, cooked until crisp.", "Sides", 29, 9, 200, fries, 0, 0},
            {"Loaded Township Chips", "Skin-on chips with cheese sauce, jalapeños and house seasoning.", "Sides", 64, 24, 90, loadedChips, 0, 1},
            {"Mac & Cheese Cup", "Creamy, cheesy macaroni, made fresh.", "Sides", 52, 19, 70, mac, 0, 0},
            {"Golden Chicken Pie", "Flaky pastry filled with savoury chicken and gently spiced gravy.", "Pies", 32, 12, 80, chickenPie, 0, 0},
            {"Pepper Steak Pie", "Crisp, golden pastry with a savoury, peppery steak filling.", "Pies", 36, 14, 80, steakPie, 0, 0},
            {"Braai Chicken Roll", "Seasoned chicken, crunchy slaw and tangy house sauce in a soft roll.", "Rolls", 59, 24, 80, roll, 0, 0},
            {"Chilli Crunch Wrap", "Crispy chicken, fresh greens and a lively chilli dressing in a warm tortilla.", "Rolls", 62, 25, 80, wrap, 0, 1},
            {"Vanilla Ice Cream · Small", "A small cup of smooth vanilla ice cream.", "Ice Cream", 22, 7, 100, iceCream, 0, 0},
            {"Vanilla Ice Cream · Regular", "A regular cup of smooth vanilla ice cream.", "Ice Cream", 32, 10, 100, iceCream, 0, 0},
            {"Vanilla Ice Cream · Large", "A large cup of smooth vanilla ice cream.", "Ice Cream", 42, 13, 100, iceCream, 0, 0},
            {"Choc-O-Block Shake", "Thick chocolate shake, blended fresh.", "Drinks", 48, 17, 80, shake, 0, 0},
            {"House Lemonade", "Fresh, zesty lemonade: the perfect sidekick to the heat.", "Drinks", 32, 9, 80, lemonade, 0, 0},
            {"Guava Cooler", "A chilled, fruity guava drink.", "Drinks", 28, 8, 80, guava, 0, 0},
            {"Coke", "A chilled Coke.", "Drinks", 25, 12, 200, cola, 0, 0},
            {"Sprite", "A chilled Sprite.", "Drinks", 25, 12, 200, can, 0, 0},
            {"Pepsi", "A chilled Pepsi.", "Drinks", 25, 12, 200, colaPour, 0, 0},
            {"Still Water", "A bottle of refreshing still water.", "Drinks", 20, 6, 200, water, 0, 0},
        };
        String sql = "INSERT INTO products (name, description, category, price, cost, stock, image, popular, spicy) VALUES (?,?,?,?,?,?,?,?,?)";
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
     * Fictional customers, passes and about 130 orders over the last 60 days,
     * generated with a fixed seed so every fresh install shows the same demo
     * charts. No real personal data is used.
     */
    private static void seedDemoOrders(Connection connection) throws SQLException {
        List<Long> customerIds = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            customerIds.add(insertUser(connection, "Demo Customer " + i, "demo" + i + "@example.com", "Demo@2026", "customer"));
        }

        // Demo customers 1-3 hold Crunch Passes for 5, 8 and 3 km.
        int[] passKm = {5, 8, 3, 0, 0, 0, 0, 0};
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO passes (user_id, max_km, price, starts_at, ends_at) VALUES (?,?,?, NOW() - INTERVAL 10 DAY, NOW() + INTERVAL 20 DAY)")) {
            for (int i = 0; i < 3; i++) {
                insert.setLong(1, customerIds.get(i));
                insert.setInt(2, passKm[i]);
                insert.setDouble(3, Pricing.passPrice(passKm[i]));
                insert.executeUpdate();
            }
        }

        List<double[]> menu = new ArrayList<>(); // {id, price, cost}
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id, price, cost FROM products")) {
            while (rows.next()) {
                menu.add(new double[]{rows.getLong(1), rows.getDouble(2), rows.getDouble(3)});
            }
        }

        Random random = new Random(2026);
        String orderSql = "INSERT INTO orders (user_id, fulfilment, distance_km, pass_km, subtotal, delivery_fee, total, food_cost,"
                + " payment_method, address, phone, status, created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?, NOW() - INTERVAL ? DAY - INTERVAL ? MINUTE)";
        String itemSql = "INSERT INTO order_items (order_id, product_id, quantity, unit_price, unit_cost) VALUES (?,?,?,?,?)";
        try (PreparedStatement orderInsert = connection.prepareStatement(orderSql, Statement.RETURN_GENERATED_KEYS);
             PreparedStatement itemInsert = connection.prepareStatement(itemSql)) {
            for (int n = 0; n < 130; n++) {
                int daysAgo = random.nextInt(60);
                int customer = random.nextInt(customerIds.size());
                boolean delivery = random.nextInt(4) != 0; // about 3 in 4 orders are delivered
                double distance = delivery ? Pricing.round2(1 + random.nextInt(110) / 10.0) : 0;
                int pass = delivery ? passKm[customer] : 0;

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
                double fee = delivery ? Pricing.deliveryFee(distance, pass) : 0;
                String status = daysAgo > 1 ? (delivery ? "Delivered" : "Collected") : (daysAgo == 1 ? (delivery ? "Out for delivery" : "Ready") : "Preparing");

                orderInsert.setLong(1, customerIds.get(customer));
                orderInsert.setString(2, delivery ? "delivery" : "collection");
                orderInsert.setDouble(3, distance);
                orderInsert.setInt(4, pass);
                orderInsert.setDouble(5, subtotal);
                orderInsert.setDouble(6, fee);
                orderInsert.setDouble(7, Pricing.orderTotal(subtotal, fee));
                orderInsert.setDouble(8, Pricing.round2(cost));
                orderInsert.setString(9, random.nextBoolean() ? "Cash" : "Card");
                orderInsert.setString(10, delivery ? "Demo address, Soshanguve" : null);
                orderInsert.setString(11, "0710000000");
                orderInsert.setString(12, status);
                orderInsert.setInt(13, daysAgo);
                orderInsert.setInt(14, random.nextInt(600));
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
