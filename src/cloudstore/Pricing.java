/*
 * File:    Pricing.java
 * Author:  CloudStore group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: The business parameters (fixed values) and the money formulas of
 *          the CloudStore revenue model. Money uses double, rounded to cents.
 *
 *   deliveryFee(subtotal) = 0           if subtotal > FREE_DELIVERY_THRESHOLD
 *                         = DELIVERY_FEE otherwise
 *   orderTotal            = subtotal + deliveryFee
 *   platformCommission    = subtotal * commissionRate (the vendor's plan rate)
 *   vendorPayout          = subtotal - platformCommission
 */
package cloudstore;

public final class Pricing {

    /** Parameter: flat delivery fee in rand. */
    public static final double DELIVERY_FEE = 35.00;
    /** Parameter: orders above this subtotal (rand) get free delivery. */
    public static final double FREE_DELIVERY_THRESHOLD = 500.00;
    /** Parameter: commission on vendors who stay on the free Starter plan. */
    public static final double DEFAULT_COMMISSION_RATE = 0.10;
    /** Parameter: largest quantity of one product per order. */
    public static final int MAX_QUANTITY_PER_ITEM = 20;

    private Pricing() {
    }

    public static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /**
     * Delivery is free above the threshold, or for CloudStore Plus members.
     */
    public static double deliveryFee(double subtotal, boolean hasPlus) {
        if (hasPlus || subtotal > FREE_DELIVERY_THRESHOLD) {
            return 0.0;
        }
        return DELIVERY_FEE;
    }

    public static double orderTotal(double subtotal, double deliveryFee) {
        return round2(subtotal + deliveryFee);
    }

    public static double commission(double subtotal, double commissionRate) {
        return round2(subtotal * commissionRate);
    }

    public static double vendorPayout(double subtotal, double commissionRate) {
        return round2(subtotal - commission(subtotal, commissionRate));
    }
}
