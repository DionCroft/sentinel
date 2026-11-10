package com.cadmus.wifisentinel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

/**
 * Survey / triangulation plot, port of the reference's redraw_survey().
 *
 * Shows captured measurement points with their distance circles, and the
 * estimated AP position (if enough samples) as a red crosshair.
 */
public class SurveyView extends View {

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint crossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<Triangulation.Sample> samples = new java.util.ArrayList<>();
    private Triangulation.Estimate estimate;
    private String targetName = "";
    private String targetBssid = "";

    public SurveyView(Context context) { super(context); init(); }
    public SurveyView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setColor(0xFFCCCCCC);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setColor(0xFFBCD7FF);
        circlePaint.setStrokeWidth(1.5f);
        dotPaint.setColor(0xFF0066CC);
        dotPaint.setStyle(Paint.Style.FILL);
        crossPaint.setColor(0xFFCC0000);
        crossPaint.setStrokeWidth(2.5f);
    }

    public void setData(String name, String bssid, List<Triangulation.Sample> samples, Triangulation.Estimate estimate) {
        this.targetName = name == null ? "" : name;
        this.targetBssid = bssid == null ? "" : bssid;
        this.samples = samples == null ? new java.util.ArrayList<>() : samples;
        this.estimate = estimate;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.WHITE);

        int w = getWidth(), h = getHeight();
        if (w < 40 || h < 40) return;
        int pad = 60;

        if (targetBssid.isEmpty()) {
            textPaint.setTextSize(14f);
            textPaint.setColor(0xFF666666);
            String msg = "Choose a target AP and capture measurements at known coordinates.";
            canvas.drawText(msg, (w - textPaint.measureText(msg)) / 2, h / 2, textPaint);
            return;
        }

        textPaint.setTextSize(15f);
        textPaint.setColor(Color.BLACK);
        String title = "Survey: " + targetName + " | " + targetBssid;
        canvas.drawText(title, (w - textPaint.measureText(title)) / 2, 20, textPaint);

        if (samples.isEmpty()) {
            textPaint.setTextSize(14f);
            textPaint.setColor(0xFF666666);
            String msg = "No survey samples yet.";
            canvas.drawText(msg, (w - textPaint.measureText(msg)) / 2, h / 2, textPaint);
            return;
        }

        // Compute bounds.
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (Triangulation.Sample s : samples) {
            minX = Math.min(minX, s.x); maxX = Math.max(maxX, s.x);
            minY = Math.min(minY, s.y); maxY = Math.max(maxY, s.y);
        }
        if (estimate != null) {
            minX = Math.min(minX, estimate.x); maxX = Math.max(maxX, estimate.x);
            minY = Math.min(minY, estimate.y); maxY = Math.max(maxY, estimate.y);
        }
        double maxD = 0;
        for (Triangulation.Sample s : samples) maxD = Math.max(maxD, s.distM);
        double margin = maxD + 1.0;
        minX -= margin; maxX += margin;
        minY -= margin; maxY += margin;
        if (Math.abs(maxX - minX) < 1e-6) { maxX += 1; minX -= 1; }
        if (Math.abs(maxY - minY) < 1e-6) { maxY += 1; minY -= 1; }

        // Frame.
        canvas.drawRect(pad, pad, w - pad, h - pad, framePaint);

        // Range labels.
        textPaint.setTextSize(11f);
        textPaint.setColor(0xFF666666);
        String xr = String.format(java.util.Locale.US, "X range: %.1f to %.1f m", minX, maxX);
        String yr = String.format(java.util.Locale.US, "Y range: %.1f to %.1f m", minY, maxY);
        canvas.drawText(xr, pad, h - 25, textPaint);
        canvas.drawText(yr, w - pad - textPaint.measureText(yr), h - 25, textPaint);
        textPaint.setColor(Color.BLACK);

        // Map function.
        for (int i = 0; i < samples.size(); i++) {
            Triangulation.Sample s = samples.get(i);
            float px = mapX(s.x, minX, maxX, pad, w);
            float py = mapY(s.y, minY, maxY, pad, h);
            float rpx = Math.abs(mapX(s.x + s.distM, minX, maxX, pad, w) - px);

            canvas.drawCircle(px, py, Math.max(4, rpx), circlePaint);
            canvas.drawCircle(px, py, 4, dotPaint);
            textPaint.setTextSize(11f);
            canvas.drawText("P" + (i + 1) + " (" + fmt(s.x) + ", " + fmt(s.y) + ")", px + 8, py - 8, textPaint);
        }

        if (estimate != null) {
            float epx = mapX(estimate.x, minX, maxX, pad, w);
            float epy = mapY(estimate.y, minY, maxY, pad, h);
            canvas.drawLine(epx - 8, epy - 8, epx + 8, epy + 8, crossPaint);
            canvas.drawLine(epx - 8, epy + 8, epx + 8, epy - 8, crossPaint);
            textPaint.setTextSize(12f);
            textPaint.setColor(0xFF990000);
            canvas.drawText("Estimated AP (" + fmt(estimate.x) + ", " + fmt(estimate.y) + ")", epx + 10, epy - 10, textPaint);
            canvas.drawText("RMSE " + String.format(java.util.Locale.US, "%.2f m", estimate.rmse), epx + 10, epy + 6, textPaint);
            textPaint.setColor(Color.BLACK);
        } else {
            textPaint.setTextSize(12f);
            textPaint.setColor(0xFF666666);
            String msg = "Need at least 3 samples for estimation.";
            canvas.drawText(msg, w - 20 - textPaint.measureText(msg), 40, textPaint);
            textPaint.setColor(Color.BLACK);
        }
    }

    private float mapX(double xm, double minX, double maxX, int pad, int w) {
        return pad + (float) ((xm - minX) / (maxX - minX) * (w - 2 * pad));
    }

    private float mapY(double ym, double minY, double maxY, int pad, int h) {
        return h - pad - (float) ((ym - minY) / (maxY - minY) * (h - 2 * pad));
    }

    private String fmt(double v) {
        return String.format(java.util.Locale.US, "%.1f", v);
    }
}
