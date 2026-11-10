package com.cadmus.wifisentinel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Radar visualisation, port of the reference's redraw_radar().
 *
 * Plots the top N APs on concentric distance rings. Colour = risk,
 * radius = estimated distance, dot size = signal strength. Angle is a
 * stable visual separator derived from the BSSID (not true bearing).
 */
public class RadarView extends View {

    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF boxRect = new RectF();

    private final List<RadarItem> items = new ArrayList<>();
    private String mode = "risk";
    private int topCount = 5;
    private double txPower1m = -40;
    private double pathLossN = 2.2;
    private String selectedBssid;

    private static class RadarItem {
        Ap ap;
        float x, y;
        int dotR;
        int risk;
        String distLabel;
        boolean selected;
    }

    public RadarView(Context context) { super(context); init(); }
    public RadarView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public RadarView(Context context, AttributeSet attrs, int defStyle) { super(context, attrs, defStyle); init(); }

    private void init() {
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(0xFFC8C8C8);
        ringPaint.setStrokeWidth(1f);
        axisPaint.setColor(0xFFE0E0E0);
        axisPaint.setStrokeWidth(1f);
        linePaint.setColor(0xFFDDDDDD);
        linePaint.setStrokeWidth(1f);
        textPaint.setColor(0xFF111111);
        boxPaint.setStyle(Paint.Style.FILL);
    }

    public void setRadarMode(String m) { this.mode = m; invalidate(); }
    public void setTopCount(int n) { this.topCount = n; invalidate(); }
    public void setPathLoss(double tx, double n) { this.txPower1m = tx; this.pathLossN = n; invalidate(); }
    public void setSelectedBssid(String b) { this.selectedBssid = b; invalidate(); }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);

        int w = getWidth(), h = getHeight();
        if (w < 40 || h < 40) return;
        int cx = w / 2, cy = h / 2 + 12;
        float pxPerM = 18f;
        int maxR = Math.min(w, h) / 2 - 90;
        if (maxR < 40) maxR = 40;

        // Distance rings.
        int[] ringM = {1, 3, 6, 10, 15};
        textPaint.setTextSize(12f);
        for (int m : ringM) {
            int r = (int) (m * pxPerM);
            if (r > maxR) continue;
            canvas.drawCircle(cx, cy, r, ringPaint);
            canvas.drawText(m + "m", cx + r - 22, cy - 10, textPaint);
        }

        // Centre + axes.
        textPaint.setTextSize(16f);
        canvas.drawText("YOU", cx, cy, textPaint);
        canvas.drawLine(cx, cy - maxR, cx, cy + maxR, axisPaint);
        canvas.drawLine(cx - maxR, cy, cx + maxR, cy, axisPaint);

        // Legend.
        drawLegend(canvas);

        // Build items.
        items.clear();
        for (Ap ap : radarAps()) {
            items.add(buildItem(ap, cx, cy, maxR, pxPerM));
        }

        if (items.isEmpty()) {
            textPaint.setTextSize(14f);
            canvas.drawText("No APs to display", cx, cy - maxR - 20, textPaint);
            return;
        }

        // Draw spokes + dots.
        for (RadarItem it : items) {
            canvas.drawLine(cx, cy, it.x, it.y, linePaint);
            dotPaint.setColor(riskColor(it.risk));
            dotPaint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(it.x, it.y, it.dotR, dotPaint);
            dotPaint.setStyle(Paint.Style.STROKE);
            dotPaint.setStrokeWidth(it.selected ? 3f : 1f);
            dotPaint.setColor(Color.BLACK);
            canvas.drawCircle(it.x, it.y, it.dotR, dotPaint);
        }

        // Labels (right / left side, de-overlapped).
        drawLabels(canvas, cx, cy, maxR, h);
    }

    private void drawLegend(Canvas canvas) {
        float lx = 15, ly = 15;
        textPaint.setTextSize(13f);
        canvas.drawText("Radar legend", lx, ly, textPaint);
        float[] ys = {ly + 20, ly + 40, ly + 60};
        int[] cols = {0xFF2E7D32, 0xFFF9A825, 0xFFD32F2F};
        String[] labels = {"Low risk", "Medium risk", "High risk"};
        for (int i = 0; i < 3; i++) {
            canvas.drawCircle(lx + 5, ys[i] - 4, 5, fillPaint(cols[i]));
            textPaint.setTextSize(12f);
            canvas.drawText(labels[i], lx + 18, ys[i], textPaint);
        }
        textPaint.setTextSize(11f);
        textPaint.setColor(0xFF555555);
        canvas.drawText("Colour = risk  Size = signal", lx, ly + 85, textPaint);
        canvas.drawText("Radius = est. distance", lx, ly + 102, textPaint);
        canvas.drawText("Angle = visual separator", lx, ly + 119, textPaint);
        textPaint.setColor(0xFF111111);
    }

    private Paint fillPaint(int c) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(c);
        p.setStyle(Paint.Style.FILL);
        return p;
    }

    private List<Ap> radarAps() {
        // Supplied by MainActivity via setData().
        return currentAps;
    }

    private List<Ap> currentAps = new ArrayList<>();
    private Map<String, Integer> currentRisk = java.util.Collections.emptyMap();

    public void setData(List<Ap> aps, Map<String, Integer> riskMap) {
        this.currentAps = aps == null ? new ArrayList<>() : aps;
        this.currentRisk = riskMap == null ? java.util.Collections.<String, Integer>emptyMap() : riskMap;
        invalidate();
    }

    private RadarItem buildItem(Ap ap, int cx, int cy, int maxR, float pxPerM) {
        RadarItem it = new RadarItem();
        it.ap = ap;
        it.risk = currentRisk.getOrDefault(ap.bssid, 0);
        it.selected = (ap.bssid != null && ap.bssid.equals(selectedBssid));

        Double dist = WifiMath.estimateDistanceM(ap.rssi, ap.freqMhz, txPower1m, pathLossN);
        int r;
        if (dist == null) {
            r = maxR;
            it.distLabel = "?";
        } else {
            r = (int) Math.min(maxR, Math.max(12, dist * pxPerM));
            it.distLabel = String.format(java.util.Locale.US, "%.1fm", dist);
        }

        double angle = angleFromBssid(ap.bssid);
        it.x = cx + (float) (r * Math.cos(Math.toRadians(angle)));
        it.y = cy - (float) (r * Math.sin(Math.toRadians(angle)));
        int q = ap.signalPct != null ? ap.signalPct : WifiMath.rssiToQuality(ap.rssi);
        q = Math.max(0, Math.min(100, q));
        it.dotR = 5 + q / 15;
        return it;
    }

    private double angleFromBssid(String bssid) {
        try {
            long v = Long.parseLong(bssid.replace(":", ""), 16);
            return v % 360;
        } catch (Exception e) {
            int sum = 0;
            for (char c : bssid.toCharArray()) sum += c;
            return sum % 360;
        }
    }

    private int riskColor(int risk) {
        if (risk >= 60) return 0xFFD32F2F;
        if (risk >= 30) return 0xFFF9A825;
        return 0xFF2E7D32;
    }

    private void drawLabels(Canvas canvas, int cx, int cy, int maxR, int h) {
        List<RadarItem> right = new ArrayList<>();
        List<RadarItem> left = new ArrayList<>();
        for (RadarItem it : items) {
            if (it.x >= cx) right.add(it); else left.add(it);
        }
        Collections.sort(right, byY());
        Collections.sort(left, byY());

        int top = 55, bottom = h - 45, minGap = 54;
        float[] rightPos = resolveLabelPositions(ys(right), top, bottom, minGap);
        float[] leftPos = resolveLabelPositions(ys(left), top, bottom, minGap);

        float labelXRight = cx + maxR + 35;
        float labelXLeft = cx - maxR - 35;

        for (int i = 0; i < right.size(); i++) drawLabel(canvas, right.get(i), labelXRight, rightPos[i], true);
        for (int i = 0; i < left.size(); i++) drawLabel(canvas, left.get(i), labelXLeft, leftPos[i], false);
    }

    private Comparator<RadarItem> byY() {
        return new Comparator<RadarItem>() {
            @Override public int compare(RadarItem a, RadarItem b) {
                return Float.compare(a.y, b.y);
            }
        };
    }

    private float[] ys(List<RadarItem> list) {
        float[] out = new float[list.size()];
        for (int i = 0; i < list.size(); i++) out[i] = list.get(i).y;
        return out;
    }

    private float[] resolveLabelPositions(float[] yValues, int top, int bottom, int minGap) {
        float[] adjusted = new float[yValues.length];
        float currentY = top;
        for (int i = 0; i < yValues.length; i++) {
            currentY = Math.max(currentY, yValues[i]);
            adjusted[i] = currentY;
            currentY += minGap;
        }
        if (adjusted.length > 0) {
            float overflow = adjusted[adjusted.length - 1] - bottom;
            if (overflow > 0) {
                for (int i = 0; i < adjusted.length; i++) adjusted[i] -= overflow;
                if (adjusted[0] < top) {
                    adjusted[0] = top;
                    for (int i = 1; i < adjusted.length; i++) {
                        adjusted[i] = Math.max(adjusted[i], adjusted[i - 1] + minGap);
                    }
                }
            }
        }
        return adjusted;
    }

    private void drawLabel(Canvas canvas, RadarItem it, float labelX, float labelY, boolean onRight) {
        Ap ap = it.ap;
        int color = riskColor(it.risk);
        int signal = ap.signalPct != null ? ap.signalPct : WifiMath.rssiToQuality(ap.rssi);

        String name = displayName(ap);
        String line1 = name;
        String line2 = ap.rssi + " dBm | " + signal + "% | " + it.distLabel;
        String line3 = "Ch " + ap.channel + " | " + ap.security + " | Risk " + it.risk;

        // Connector line from dot to label.
        linePaint.setColor(color);
        float lineEndX = onRight ? labelX - 8 : labelX + 8;
        canvas.drawLine(it.x, it.y, lineEndX, labelY, linePaint);

        // Text block.
        textPaint.setTextSize(11f);
        textPaint.setFakeBoldText(it.selected);
        textPaint.setColor(0xFF111111);

        float lx = onRight ? labelX : labelX;
        // Draw three lines; estimate box from measured widths.
        float w1 = textPaint.measureText(line1);
        float w2 = textPaint.measureText(line2);
        float w3 = textPaint.measureText(line3);
        float maxW = Math.max(w1, Math.max(w2, w3));
        float lineH = 15f;
        float totalH = lineH * 3;
        float boxTop = labelY - totalH / 2;

        boxPaint.setColor(Color.WHITE);
        boxPaint.setStyle(Paint.Style.FILL);
        boxRect.set(lx - 6, boxTop - 4, lx + maxW + 6, boxTop + totalH + 4);
        canvas.drawRect(boxRect, boxPaint);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setColor(color);
        boxPaint.setStrokeWidth(it.selected ? 2f : 1f);
        canvas.drawRect(boxRect, boxPaint);

        textPaint.setColor(0xFF111111);
        canvas.drawText(line1, lx, boxTop + 12, textPaint);
        canvas.drawText(line2, lx, boxTop + 27, textPaint);
        canvas.drawText(line3, lx, boxTop + 42, textPaint);
        textPaint.setFakeBoldText(false);
    }

    private String displayName(Ap ap) {
        String alias = currentAlias.get(ap.bssid);
        return (alias != null && !alias.isEmpty()) ? alias : ap.ssid;
    }

    private Map<String, String> currentAlias = java.util.Collections.emptyMap();
    public void setAliases(Map<String, String> aliases) {
        this.currentAlias = aliases == null ? java.util.Collections.<String, String>emptyMap() : aliases;
        invalidate();
    }
}
