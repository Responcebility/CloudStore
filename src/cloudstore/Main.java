/*
 * File:    Main.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Starts CloudStore: connects to MySQL, prepares the database, then
 *          runs the web server that serves both the website and the API.
 */
package cloudstore;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.Executors;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        Config config = Config.load();

        try {
            Database.init(config);
        } catch (SQLException e) {
            System.err.println("Could not connect to MySQL at " + config.dbHost() + ":" + config.dbPort()
                    + " as '" + config.dbUser() + "'. Check config.properties and that MySQL is running.");
            System.err.println("Details: " + e.getMessage());
            System.exit(1);
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(config.serverPort()), 0);
        server.createContext("/api/auth", new AuthHandler());
        ProductHandler products = new ProductHandler();
        server.createContext("/api/products", products);
        server.createContext("/api/vendors", products);
        server.createContext("/api/orders", new OrderHandler());
        PlanHandler plans = new PlanHandler();
        server.createContext("/api/plans", plans);
        server.createContext("/api/subscriptions", plans);
        server.createContext("/api/contact", new ContactHandler());
        InsightsHandler insights = new InsightsHandler();
        server.createContext("/api/pricing", insights);
        server.createContext("/api/insights", insights);
        server.createContext("/api/admin", new AdminHandler());
        server.createContext("/api/assistant", new AssistantHandler());
        server.createContext("/", new StaticHandler(Path.of("public")));
        server.setExecutor(Executors.newFixedThreadPool(16));
        server.start();

        System.out.println("CloudStore is running at http://localhost:" + config.serverPort());
    }
}
