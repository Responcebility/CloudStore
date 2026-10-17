/*
 * File:    Validator.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Checks every value the user sends before it is used. Each check
 *          either returns the cleaned value or throws a polite message such
 *          as "Please enter a quantity between 1 and 20." instead of "ERROR".
 */
package kasicrunch;

import java.util.Map;
import java.util.regex.Pattern;

public final class Validator {

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    // South African numbers: 0XX XXX XXXX or +27 XX XXX XXXX (spaces allowed).
    private static final Pattern SA_PHONE = Pattern.compile("^(0\\d{9}|\\+27\\d{9})$");

    private Validator() {
    }

    public static String text(Map<String, String> input, String field, String label, int min, int max) {
        String value = input.getOrDefault(field, "").trim();
        if (value.length() < min || value.length() > max) {
            if (min > 0 && value.isEmpty()) {
                throw ApiException.badRequest("Please enter your " + label + ".");
            }
            throw ApiException.badRequest("Please enter a " + label + " between " + min + " and " + max + " characters long.");
        }
        return value;
    }

    public static String email(Map<String, String> input, String field) {
        String value = input.getOrDefault(field, "").trim().toLowerCase();
        if (value.length() > 120 || !EMAIL.matcher(value).matches()) {
            throw ApiException.badRequest("Please enter a valid email address, for example name@example.com.");
        }
        return value;
    }

    public static String password(Map<String, String> input, String field) {
        String value = input.getOrDefault(field, "");
        boolean hasLetter = value.chars().anyMatch(Character::isLetter);
        boolean hasDigit = value.chars().anyMatch(Character::isDigit);
        if (value.length() < 8 || value.length() > 72 || !hasLetter || !hasDigit) {
            throw ApiException.badRequest("Please choose a password of at least 8 characters with at least one letter and one number.");
        }
        return value;
    }

    public static String phone(Map<String, String> input, String field) {
        String value = input.getOrDefault(field, "").replaceAll("[\\s-]", "");
        if (!SA_PHONE.matcher(value).matches()) {
            throw ApiException.badRequest("Please enter a South African phone number, for example 0712345678.");
        }
        return value;
    }

    /** Whole number within a range; rejects letters, decimals and out-of-range values. */
    public static int intInRange(String raw, String label, int min, int max) {
        try {
            int value = Integer.parseInt(raw == null ? "" : raw.trim());
            if (value < min || value > max) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Please enter a " + label + " between " + min + " and " + max + ".");
        }
    }

    /** Money amount within a range, e.g. a product price. */
    public static double amount(Map<String, String> input, String field, String label, double min, double max) {
        try {
            double value = Double.parseDouble(input.getOrDefault(field, "").trim());
            if (Double.isNaN(value) || value < min || value > max) throw new NumberFormatException();
            return Pricing.round2(value);
        } catch (NumberFormatException e) {
            throw ApiException.badRequest("Please enter a " + label + " between R" + plain(min) + " and R" + plain(max) + ".");
        }
    }

    /** Shows 50000.0 as "50000" and 2.5 as "2.5" in messages. */
    private static String plain(double value) {
        return value == Math.floor(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    public static String oneOf(Map<String, String> input, String field, String label, String... allowed) {
        String value = input.getOrDefault(field, "").trim();
        for (String option : allowed) {
            if (option.equals(value)) return value;
        }
        throw ApiException.badRequest("Please choose a valid " + label + ".");
    }
}
