/*
 * File:    CoreTest.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Checks the money formulas, statistics, input validation and cart
 *          parsing, including deliberately bad input (letters, negatives,
 *          huge numbers). Run with test.ps1 or test.sh; no database needed.
 */
package kasicrunch;

import java.util.List;
import java.util.Map;

public final class CoreTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // Pricing: delivery is R25, free strictly above R250 or for club members.
        check("delivery on R249.99", Pricing.deliveryFee(249.99, false) == 25.0);
        check("delivery on exactly R250", Pricing.deliveryFee(250.00, false) == 25.0);
        check("free delivery on R250.01", Pricing.deliveryFee(250.01, false) == 0.0);
        check("free delivery for club members", Pricing.deliveryFee(40, true) == 0.0);
        check("10% club discount on R120", Pricing.discount(120, 0.10) == 12.0);
        check("no discount for non-members", Pricing.discount(120, 0.0) == 0.0);
        check("order total: R89.90 + R25 delivery", Pricing.orderTotal(89.90, 0, 25) == 114.9);
        check("order total with discount", Pricing.orderTotal(120, 12, 0) == 108.0);
        check("gross profit", Pricing.grossProfit(100, 42.5) == 57.5);
        check("gross margin %", Pricing.marginPercent(200, 90) == 55.0);
        check("rounds to cents", Pricing.round2(19.999) == 20.0 && Pricing.round2(3.141) == 3.14);

        // Statistics.
        List<Double> values = List.of(100.0, 250.0, 50.0, 400.0);
        check("mean", Statistics.mean(values) == 200.0);
        check("median of an even count", Statistics.median(values) == 175.0);
        check("median of an odd count", Statistics.median(List.of(3.0, 1.0, 2.0)) == 2.0);
        check("range", Statistics.range(values) == 350.0);
        check("percentage", Statistics.percentage(1, 4) == 25.0);
        check("percentage of zero whole", Statistics.percentage(5, 0) == 0.0);
        check("empty list mean", Statistics.mean(List.of()) == 0.0);
        check("daily to monthly", Statistics.dailyToMonthly(10) == 304.4);

        // Validation rejects 0, 7 and 'a' for a 1-5 range, without crashing.
        check("accepts 3 in 1-5", Validator.intInRange("3", "rating", 1, 5) == 3);
        rejects("rejects 0 in 1-5", () -> Validator.intInRange("0", "rating", 1, 5));
        rejects("rejects 7 in 1-5", () -> Validator.intInRange("7", "rating", 1, 5));
        rejects("rejects 'a' in 1-5", () -> Validator.intInRange("a", "rating", 1, 5));
        rejects("rejects a decimal", () -> Validator.intInRange("2.5", "rating", 1, 5));
        rejects("rejects a huge number", () -> Validator.intInRange("99999999999999", "rating", 1, 5));
        rejects("rejects a bad email", () -> Validator.email(Map.of("email", "not-an-email"), "email"));
        rejects("rejects a short password", () -> Validator.password(Map.of("password", "abc1"), "password"));
        rejects("rejects a password with no digit", () -> Validator.password(Map.of("password", "abcdefghij"), "password"));
        rejects("rejects a non-SA phone", () -> Validator.phone(Map.of("phone", "12345"), "phone"));
        check("accepts a SA phone with spaces", Validator.phone(Map.of("phone", "071 234 5678"), "phone").equals("0712345678"));
        rejects("rejects a negative price", () -> Validator.amount(Map.of("price", "-5"), "price", "price", 1, 100));
        rejects("rejects a price of letters", () -> Validator.amount(Map.of("price", "ten"), "price", "price", 1, 100));

        // Cart parsing.
        Map<Long, Integer> items = OrderHandler.parseItems("4:2,9:1,4:3");
        check("merges repeated products", items.get(4L) == 5 && items.get(9L) == 1);
        rejects("rejects an empty cart", () -> OrderHandler.parseItems(""));
        rejects("rejects quantity 0", () -> OrderHandler.parseItems("4:0"));
        rejects("rejects quantity over 20", () -> OrderHandler.parseItems("4:21"));
        rejects("rejects merged quantity over 20", () -> OrderHandler.parseItems("4:15,4:6"));
        rejects("rejects garbage", () -> OrderHandler.parseItems("hello"));

        // JSON output escapes dangerous characters.
        check("json escapes quotes and tags", Json.write(Json.map("x", "a\"<b>")).equals("{\"x\":\"a\\\"\\u003cb\\u003e\"}"));

        // Passwords are hashed and verified, never stored as given.
        String hash = PasswordHasher.hash("Secret123");
        check("hash is not the password", !hash.contains("Secret123"));
        check("correct password verifies", PasswordHasher.verify("Secret123", hash));
        check("wrong password fails", !PasswordHasher.verify("Secret124", hash));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }

    private static void rejects(String name, Runnable action) {
        try {
            action.run();
            check(name, false);
        } catch (ApiException e) {
            check(name + " -> \"" + e.getMessage() + "\"", e.status() == 400);
        }
    }
}
