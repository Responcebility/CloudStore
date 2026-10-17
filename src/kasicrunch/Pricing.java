/*
 * File:    Pricing.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The business parameters (fixed values) and the money formulas of
 *          Kasi-Crunch. Money uses double, rounded to cents.
 *
 *   discount     = subtotal * club discount rate        (0 for non-members)
 *   deliveryFee  = 0            if the club includes free delivery
 *                               or subtotal > FREE_DELIVERY_THRESHOLD
 *                = DELIVERY_FEE otherwise
 *   orderTotal   = subtotal - discount + deliveryFee
 *   grossProfit  = food sales - food cost
 *   margin %     = grossProfit / food sales * 100
 */
package kasicrunch;

public final class Pricing {

    /** Parameter: flat delivery fee in rand. */
    public static final double DELIVERY_FEE = 25.00;
    /** Parameter: orders above this subtotal (rand) get free delivery. */
    public static final double FREE_DELIVERY_THRESHOLD = 250.00;
    /** Parameter: largest quantity of one menu item per order. */
    public static final int MAX_QUANTITY_PER_ITEM = 20;

    private Pricing() {
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public static double discount(double subtotal, double discountRate) {
        return round2(subtotal * discountRate);
    }

    /** Free for club members with free delivery, or when the order is over the threshold. */
    public static double deliveryFee(double subtotal, boolean clubFreeDelivery) {
        if (clubFreeDelivery || subtotal > FREE_DELIVERY_THRESHOLD) {
            return 0.0;
        }
        return DELIVERY_FEE;
    }

    public static double orderTotal(double subtotal, double discount, double deliveryFee) {
        return round2(subtotal - discount + deliveryFee);
    }

    public static double grossProfit(double sales, double cost) {
        return round2(sales - cost);
    }

    public static double marginPercent(double sales, double cost) {
        return Statistics.percentage(sales - cost, sales);
    }
}
