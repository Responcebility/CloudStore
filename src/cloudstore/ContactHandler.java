/*
 * File:    ContactHandler.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Saves messages from the Contact page so the team can reply.
 */
package cloudstore;

import com.sun.net.httpserver.HttpExchange;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

public final class ContactHandler extends ApiHandler {

    @Override
    protected void route(HttpExchange exchange, String method, String path) throws Exception {
        if (!"POST /api/contact".equals(method + " " + path)) {
            throw noRoute();
        }
        Map<String, String> form = Http.body(exchange);
        String name = Validator.text(form, "name", "name", 2, 80);
        String email = Validator.email(form, "email");
        String subject = Validator.text(form, "subject", "subject", 3, 120);
        String message = Validator.text(form, "message", "message", 10, 2000);

        try (Connection connection = Database.connect();
             PreparedStatement insert = connection.prepareStatement(
                     "INSERT INTO contact_messages (name, email, subject, message) VALUES (?,?,?,?)")) {
            insert.setString(1, name);
            insert.setString(2, email);
            insert.setString(3, subject);
            insert.setString(4, message);
            insert.executeUpdate();
        }
        Http.json(exchange, 201, Json.map("message", "Thanks, " + name + "! We received your message and will reply within two working days."));
    }
}
