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
        // Pricing: delivery is R16 per km; a Crunch Pass covers its chosen distance.
        check("4.5 km without a pass costs R72", Pricing.deliveryFee(4.5, 0) == 72.0);
        check("0.1 km without a pass costs R1.60", Pricing.deliveryFee(0.1, 0) == 1.6);
        check("4.5 km with a 5 km pass is free", Pricing.deliveryFee(4.5, 5) == 0.0);
        check("exactly 5 km with a 5 km pass is free", Pricing.deliveryFee(5, 5) == 0.0);
        check("7 km with a 5 km pass pays 2 km (R32)", Pricing.deliveryFee(7, 5) == 32.0);
        check("pass price for 1 km is R64", Pricing.passPrice(1) == 64.0);
        check("pass price for 5 km is R124", Pricing.passPrice(5) == 124.0);
        check("pass price for 20 km is R349", Pricing.passPrice(20) == 349.0);
        check("order total adds delivery", Pricing.orderTotal(82, 72) == 154.0);
        check("gross profit", Pricing.grossProfit(100, 42.5) == 57.5);
        check("gross margin %", Pricing.marginPercent(200, 90) == 55.0);
        check("rounds to cents", Pricing.round2(19.999) == 20.0 && Pricing.round2(3.141) == 3.14);

        // Distance input.
        check("accepts 4.5 km", Validator.decimalInRange("4.5", "delivery distance", 0.1, 30, "km") == 4.5);
        rejects("rejects 0 km", () -> Validator.decimalInRange("0", "delivery distance", 0.1, 30, "km"));
        rejects("rejects 31 km", () -> Validator.decimalInRange("31", "delivery distance", 0.1, 30, "km"));
        rejects("rejects km as letters", () -> Validator.decimalInRange("far", "delivery distance", 0.1, 30, "km"));

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
