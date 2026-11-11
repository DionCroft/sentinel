package com.cadmus.wifisentinel;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Wraps WifiManager scan results into our Ap model.
 *
 * Android 8+ requires location permission (granted at runtime) for the system
 * WiFi scan to return any results — this is enforced in MainActivity.
 */
public final class Scanner {

    /** One scan pass: fetch the latest system scan results as Ap objects. */
    public static List<Ap> scanOnce(Context ctx) {
        WifiManager wm = (WifiManager) ctx.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        if (wm == null) return Collections.emptyList();

        List<ScanResult> results = wm.getScanResults();
        List<Ap> aps = new ArrayList<>();
        if (results == null) return aps;

        for (ScanResult r : results) {
            if (r.BSSID == null) continue;
            Ap ap = new Ap();
            ap.ssid = (r.SSID == null || r.SSID.isEmpty()) ? "<hidden>" : r.SSID;
            ap.bssid = r.BSSID.toLowerCase();
            ap.rssi = r.level;                       // dBm
            ap.freqMhz = r.frequency;                // MHz
            ap.channel = WifiMath.freqToChannel(r.frequency);
            ap.security = WifiMath.normalizeSecurity(parseCaps(r.capabilities));
            ap.vendor = Store.lookupVendor(ctx, ap.bssid);
            ap.signalPct = WifiMath.rssiToQuality(ap.rssi);
            aps.add(ap);
        }

        Collections.sort(aps, new Comparator<Ap>() {
            @Override public int compare(Ap a, Ap b) {
                return Integer.compare(b.rssi, a.rssi);
            }
        });
        return aps;
    }

    /**
     * Merge multiple scans by BSSID, averaging RSSI/quality — port of average_scans().
     */
    public static List<Ap> averageScans(List<List<Ap>> scans) {
        Map<String, Map<String, Object>> merged = new HashMap<>();

        for (List<Ap> scan : scans) {
            for (Ap ap : scan) {
                Map<String, Object> rec = merged.get(ap.bssid);
                if (rec == null) {
                    rec = new HashMap<>();
                    merged.put(ap.bssid, rec);
                }
                rec.put("ssid", ap.ssid);
                rec.put("freq_mhz", ap.freqMhz != 0 ? ap.freqMhz : intVal(rec, "freq_mhz"));
                rec.put("channel", ap.channel != 0 ? ap.channel : intVal(rec, "channel"));
                rec.put("security", notEmpty(ap.security) ? ap.security : strVal(rec, "security"));
                rec.put("vendor", notEmpty(ap.vendor) ? ap.vendor : strVal(rec, "vendor"));
                List<Integer> rssiVals = intList(rec, "rssi_vals");
                rssiVals.add(ap.rssi);
                if (ap.signalPct != null) {
                    List<Integer> sigVals = intList(rec, "signal_vals");
                    sigVals.add(ap.signalPct);
                }
            }
        }

        List<Ap> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> e : merged.entrySet()) {
            String bssid = e.getKey();
            Map<String, Object> rec = e.getValue();
            List<Integer> rssiVals = intList(rec, "rssi_vals");
            List<Integer> sigVals = intList(rec, "signal_vals");

            Ap ap = new Ap();
            ap.bssid = bssid;
            ap.ssid = strVal(rec, "ssid");
            ap.freqMhz = intVal(rec, "freq_mhz");
            ap.channel = intVal(rec, "channel");
            ap.security = strVal(rec, "security");
            ap.vendor = strVal(rec, "vendor");
            ap.rssi = avgInt(rssiVals);
            ap.signalPct = sigVals.isEmpty() ? null : avgInt(sigVals);
            out.add(ap);
        }

        Collections.sort(out, new Comparator<Ap>() {
            @Override public int compare(Ap a, Ap b) {
                return Integer.compare(b.rssi, a.rssi);
            }
        });
        return out;
    }

    // ---- small map helpers ----
    private static int parseCaps(String caps) {
        // Map capability string to a coarse encryption bitmask compatible with
        // WifiMath.normalizeSecurity. RSN => WPA2, WPA => WPA.
        if (caps == null) return 0;
        String c = caps.toUpperCase();
        boolean rsn = c.contains("RSN") || c.contains("WPA2");
        boolean wpa = c.contains("WPA");
        if (rsn && wpa && c.contains("WPA1")) return 3; // WPA + RSN
        if (rsn) return 2;
        if (wpa) return 1;
        return 0;
    }

    @SuppressWarnings("unchecked")
    private static List<Integer> intList(Map<String, Object> rec, String key) {
        Object o = rec.get(key);
        if (o instanceof List) return (List<Integer>) o;
        List<Integer> l = new ArrayList<>();
        rec.put(key, l);
        return l;
    }

    private static String strVal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private static int intVal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        return 0;
    }

    private static boolean notEmpty(String s) { return s != null && !s.isEmpty(); }

    private static int avgInt(List<Integer> vals) {
        if (vals.isEmpty()) return 0;
        long sum = 0;
        for (int v : vals) sum += v;
        return (int) Math.round((double) sum / vals.size());
    }
}
