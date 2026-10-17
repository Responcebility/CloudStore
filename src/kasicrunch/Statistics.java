/*
 * File:    Statistics.java
 * Author:  Kasi-Crunch group (JGA 2026)
 * Date:    2026-10-17
 * Purpose: Elementary statistics used on the dashboard: mean, median,
 *          range and percentages, plus a daily-to-monthly conversion.
 */
package kasicrunch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Statistics {

    /** Parameter: average number of days in a month, for conversions. */
    public static final double DAYS_PER_MONTH = 30.44;

    private Statistics() {
    }

    public static double mean(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return Pricing.round2(sum / values.size());
    }

    /** The middle value once sorted (average of the two middle values for an even count). */
    public static double median(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if (sorted.size() % 2 == 0) {
            return Pricing.round2((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
        }
        return Pricing.round2(sorted.get(middle));
    }

    /** Largest value minus smallest value. */
    public static double range(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        return Pricing.round2(Collections.max(values) - Collections.min(values));
    }

    public static double percentage(double part, double whole) {
        if (whole == 0) return 0.0;
        return Pricing.round2(part / whole * 100.0);
    }

    /** Converts a per-day figure (e.g. orders per day) into a per-month figure. */
    public static double dailyToMonthly(double perDay) {
        return Pricing.round2(perDay * DAYS_PER_MONTH);
    }
}
