/*
 * File:    Pricing.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The business parameters (fixed values) and the money formulas of
 *          Kasi-Crunch. Money uses double, rounded to cents.
 *
 *   deliveryFee  = 0                                   for collection
 *                = (distance - passKm) x R16 per km    for delivery, never below 0
 *                  (passKm is 0 for customers without a Crunch Pass)
 *   orderTotal   = subtotal + deliveryFee
 *   passPrice    = R49 + R15 x the pass's maximum km   (for one month)
 *   grossProfit  = food sales - food cost
 *   margin %     = grossProfit / food sales * 100
 */
package kasicrunch;

public final class Pricing {

    /** Parameter: delivery charge per kilometre, in rand. */
    public static final double DELIVERY_RATE_PER_KM = 16.00;
    /** Parameter: furthest we deliver, in km. */
    public static final double MAX_DELIVERY_KM = 30.0;
    /** Parameter: Crunch Pass base price per month, in rand. */
    public static final double PASS_BASE_PRICE = 49.00;
    /** Parameter: Crunch Pass price per km of free-delivery distance, per month. */
    public static final double PASS_RATE_PER_KM = 15.00;
    /** Parameter: smallest and largest free-delivery distance a pass can cover. */
    public static final int PASS_MIN_KM = 1;
    public static final int PASS_MAX_KM = 20;
    /** Parameter: how long one pass lasts, in days. */
    public static final int PASS_DAYS = 30;
    /** Parameter: largest quantity of one menu item per order. */
    public static final int MAX_QUANTITY_PER_ITEM = 20;

    private Pricing() {
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Delivery costs R16 for every km beyond what the customer's Crunch Pass
     * covers. Without a pass, passKm is 0, so every km is charged.
     */
    public static double deliveryFee(double distanceKm, double passKm) {
        double chargedKm = Math.max(0, distanceKm - passKm);
        return round2(chargedKm * DELIVERY_RATE_PER_KM);
    }

    public static double orderTotal(double subtotal, double deliveryFee) {
        return round2(subtotal + deliveryFee);
    }

    /** One month of free delivery up to maxKm. */
    public static double passPrice(int maxKm) {
        return round2(PASS_BASE_PRICE + PASS_RATE_PER_KM * maxKm);
    }

    public static double grossProfit(double sales, double cost) {
        return round2(sales - cost);
    }

    public static double marginPercent(double sales, double cost) {
        return Statistics.percentage(sales - cost, sales);
    }
}
