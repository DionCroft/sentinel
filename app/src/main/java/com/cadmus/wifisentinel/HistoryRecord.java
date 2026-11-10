package com.cadmus.wifisentinel;

import java.util.Map;
import java.util.HashMap;

/**
 * Per-BSSID history record, mirroring the Python HISTORY_FILE entries.
 */
public class HistoryRecord {
    public String ssid;
    public String bssid;
    public String firstSeen;
    public String lastSeen;
    public int seenCount;
    public int strongestRssi;

    public static HistoryRecord fromMap(Map<String, Object> m) {
        HistoryRecord r = new HistoryRecord();
        r.ssid = str(m, "ssid");
        r.bssid = str(m, "bssid");
        r.firstSeen = str(m, "first_seen");
        r.lastSeen = str(m, "last_seen");
        r.seenCount = intVal(m, "seen_count");
        r.strongestRssi = intVal(m, "strongest_rssi");
        return r;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("ssid", ssid);
        m.put("bssid", bssid);
        m.put("first_seen", firstSeen);
        m.put("last_seen", lastSeen);
        m.put("seen_count", seenCount);
        m.put("strongest_rssi", strongestRssi);
        m.put("security", security);
        m.put("vendor", vendor);
        return m;
    }

    public String security;
    public String vendor;

    private static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? "" : v.toString();
    }

    private static int intVal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v == null ? "" : v.toString()); }
        catch (Exception e) { return 0; }
    }
}
