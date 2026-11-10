package com.cadmus.wifisentinel;

import java.util.ArrayList;
import java.util.List;

/**
 * RSSI-based AP position estimation, ported 1:1 from the reference's
 * estimate_ap_position_from_samples().
 *
 * The user captures measurements at known (x, y) coordinates. Each yields an
 * estimated distance to the target AP. We then do a coarse-to-fine grid search
 * for the point that best satisfies all distance constraints (least-squares).
 */
public final class Triangulation {

    public static class Sample {
        public double x;
        public double y;
        public double distM;
        public int rssi;
        public String timestamp;
    }

    public static class Estimate {
        public double x;
        public double y;
        public double rmse;
    }

    /**
     * @param samples measurement points with estimated distances
     * @return best-fit AP position, or null if fewer than 3 usable samples
     */
    public static Estimate estimate(List<Sample> samples) {
        List<double[]> valid = new ArrayList<>(); // {x, y, d}
        for (Sample s : samples) {
            if (s == null) continue;
            if (s.distM > 0) valid.add(new double[]{s.x, s.y, s.distM});
        }
        if (valid.size() < 3) return null;

        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double maxD = 0;
        for (double[] v : valid) {
            minX = Math.min(minX, v[0]); maxX = Math.max(maxX, v[0]);
            minY = Math.min(minY, v[1]); maxY = Math.max(maxY, v[1]);
            maxD = Math.max(maxD, v[2]);
        }
        double margin = Math.max(maxD, 2.0);
        minX -= margin; maxX += margin;
        minY -= margin; maxY += margin;

        double bestX = Double.NaN, bestY = Double.NaN, bestErr = Double.POSITIVE_INFINITY;

        // Coarse pass.
        double coarseStep = Math.max(Math.max(maxX - minX, maxY - minY) / 40.0, 0.25);
        for (double x = minX; x <= maxX; x += coarseStep) {
            for (double y = minY; y <= maxY; y += coarseStep) {
                double err = errorFn(valid, x, y);
                if (err < bestErr) {
                    bestErr = err; bestX = x; bestY = y;
                }
            }
        }

        // Fine pass around the coarse best.
        double refineSpan = Math.max(coarseStep * 2.5, 1.0);
        double refineStep = Math.max(coarseStep / 8.0, 0.05);
        double rx1 = bestX - refineSpan, rx2 = bestX + refineSpan;
        double ry1 = bestY - refineSpan, ry2 = bestY + refineSpan;
        for (double x = rx1; x <= rx2; x += refineStep) {
            for (double y = ry1; y <= ry2; y += refineStep) {
                double err = errorFn(valid, x, y);
                if (err < bestErr) {
                    bestErr = err; bestX = x; bestY = y;
                }
            }
        }

        Estimate e = new Estimate();
        e.x = bestX;
        e.y = bestY;
        e.rmse = Math.sqrt(bestErr);
        return e;
    }

    private static double errorFn(List<double[]> valid, double px, double py) {
        double sum = 0;
        for (double[] v : valid) {
            double dx = px - v[0], dy = py - v[1];
            double pred = Math.sqrt(dx * dx + dy * dy);
            double diff = pred - v[2];
            sum += diff * diff;
        }
        return sum / valid.size();
    }
}
