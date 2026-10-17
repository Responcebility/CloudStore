/*
 * File:    ApiHandler.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Base class for every API endpoint. It routes the request and turns
 *          any problem into a clear JSON message, so the server never crashes
 *          and the user never sees a raw error.
 */
package kasicrunch;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public abstract class ApiHandler implements HttpHandler {

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange, exchange.getRequestMethod().toUpperCase(), exchange.getRequestURI().getPath());
        } catch (ApiException e) {
            Http.error(exchange, e.status(), e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error on " + exchange.getRequestURI() + ": " + e);
            e.printStackTrace();
            Http.error(exchange, 500, "Something went wrong on our side. Please try again in a moment.");
        } finally {
            exchange.close();
        }
    }

    /** Each endpoint handles its own paths and methods. */
    protected abstract void route(HttpExchange exchange, String method, String path) throws Exception;

    protected static ApiException noRoute() {
        return ApiException.notFound("That page or action does not exist.");
    }
}
