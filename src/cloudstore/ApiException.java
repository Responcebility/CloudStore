/*
 * File:    ApiException.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: An error carrying an HTTP status and a polite message that is
 *          safe to show the user (for example a failed validation).
 */
package cloudstore;

public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }

    public static ApiException badRequest(String message) { return new ApiException(400, message); }
    public static ApiException unauthorized() { return new ApiException(401, "Please log in to continue."); }
    public static ApiException forbidden() { return new ApiException(403, "You do not have access to this area."); }
    public static ApiException notFound(String message) { return new ApiException(404, message); }
}
