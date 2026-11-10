package com.cadmus.wifisentinel;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Risk scoring and rogue / evil-twin detection, ported 1:1 from the
 * reference's risk_score() and rogue_alerts_and_context().
 *
 * The "baseline" is the set of BSSIDs the user has confirmed as trusted.
 * Anything new, downgraded, or SSID-mismatched is flagged.
 */
public final class RiskEngine {

    /** Result of analysing a scan against the baseline. */
    public static class Result {
        public final List<String> alerts = new ArrayList<>();
        public final Map<String, Integer> riskMap = new LinkedHashMap<>();
    }

    /** Per-BSSID baseline entry. */
    public static class BaselineEntry {
        public String ssid;
        public String security;
        public int channel;
        public String vendor;
        public long firstSeen;
        public long lastSaved;
    }

    /**
     * Analyse a scan. Mirrors rogue_alerts_and_context() + risk_score().
     *
     * @param aps      current scan results
     * @param baseline baseline keyed by BSSID
     */
    public static Result analyse(List<Ap> aps, Map<String, BaselineEntry> baseline) {
        Result res = new Result();

        // Group by SSID to detect mixed-security SSIDs.
        Map<String, List<Ap>> bySsid = new LinkedHashMap<>();
        Set<String> mixedSecuritySsids = new HashSet<>();
        for (Ap ap : aps) {
            List<Ap> group = bySsid.get(ap.ssid);
            if (group == null) { group = new ArrayList<>(); bySsid.put(ap.ssid, group); }
            group.add(ap);
        }
        for (Map.Entry<String, List<Ap>> e : bySsid.entrySet()) {
            Set<String> secs = new HashSet<>();
            for (Ap ap : e.getValue()) secs.add(ap.security);
            if (secs.size() > 1) {
                mixedSecuritySsids.add(e.getKey());
                res.alerts.add("SSID '" + e.getKey() + "' advertised with multiple security types: "
                        + secs.toString());
            }
        }

        // Map SSID -> set of known (baseline) BSSIDs.
        Map<String, Set<String>> ssidKnownBssids = new HashMap<>();
        for (Map.Entry<String, BaselineEntry> e : baseline.entrySet()) {
            String ssid = e.getValue().ssid == null ? "" : e.getValue().ssid;
            Set<String> set = ssidKnownBssids.get(ssid);
            if (set == null) { set = new HashSet<>(); ssidKnownBssids.put(ssid, set); }
            set.add(e.getKey());
        }

        for (Ap ap : aps) {
            BaselineEntry base = baseline.get(ap.bssid);
            int risk = riskScore(ap, baseline, ssidKnownBssids, mixedSecuritySsids, res);
            res.riskMap.put(ap.bssid, risk);

            // Generate alerts.
            if (base == null) {
                if (ap.rssi > -55) {
                    res.alerts.add("NEW strong AP nearby: " + ap.ssid + " (" + ap.bssid + ") RSSI " + ap.rssi + " dBm");
                } else {
                    res.alerts.add("New AP detected: " + ap.ssid + " (" + ap.bssid + ")");
                }
            } else {
                if (base.ssid != null && !base.ssid.equals(ap.ssid)) {
                    res.alerts.add("BSSID " + ap.bssid + " changed SSID from '" + base.ssid + "' to '" + ap.ssid + "'");
                }
                String oldSec = base.security;
                if (oldSec != null && !oldSec.equals(ap.security)) {
                    if (WifiMath.securityRank(ap.security) < WifiMath.securityRank(oldSec)) {
                        res.alerts.add("SECURITY DOWNGRADE for " + ap.ssid + " (" + ap.bssid + "): "
                                + oldSec + " -> " + ap.security);
                    } else {
                        res.alerts.add("Security changed for " + ap.ssid + " (" + ap.bssid + "): "
                                + oldSec + " -> " + ap.security);
                    }
                }
                if (base.channel != 0 && ap.channel != 0 && base.channel != ap.channel) {
                    res.alerts.add("Channel changed for " + ap.ssid + " (" + ap.bssid + "): "
                            + base.channel + " -> " + ap.channel);
                }
            }

            Set<String> known = ssidKnownBssids.get(ap.ssid);
            if (known != null && !known.isEmpty() && !known.contains(ap.bssid)) {
                res.alerts.add("Evil-twin candidate: SSID '" + ap.ssid + "' with NEW BSSID " + ap.bssid
                        + " (known: " + known.size() + " BSSID(s))");
            }
        }

        return res;
    }

    /** Port of risk_score(). */
    private static int riskScore(Ap ap, Map<String, BaselineEntry> baseline,
                                 Map<String, Set<String>> ssidKnownBssids,
                                 Set<String> mixedSecuritySsids, Result res) {
        int score = 0;
        BaselineEntry base = baseline.get(ap.bssid);

        if (base == null) {
            score += 20;
            if (ap.rssi > -55) score += 25;
        }

        int secRank = WifiMath.securityRank(ap.security);
        if (secRank <= 1) score += 30;
        else if (secRank == 3) score += 10;

        if (mixedSecuritySsids.contains(ap.ssid)) score += 20;

        Set<String> knownSet = ssidKnownBssids.get(ap.ssid);
        if (knownSet != null && !knownSet.isEmpty() && !knownSet.contains(ap.bssid)) {
            score += 25;
        }

        if (base != null) {
            String old = base.security == null ? "" : base.security;
            if (WifiMath.securityRank(ap.security) < WifiMath.securityRank(old)) score += 35;
            if (base.channel != 0 && ap.channel != 0 && base.channel != ap.channel) score += 8;
        }

        return Math.max(0, Math.min(100, score));
    }

    // ----------------------------------------------------------------
    // Baseline JSON (de)serialisation
    // ----------------------------------------------------------------
    public static Map<String, BaselineEntry> loadBaseline(JSONObject obj) {
        Map<String, BaselineEntry> map = new HashMap<>();
        if (obj == null) return map;
        try {
            var keys = obj.keys();
            while (keys.hasNext()) {
                String bssid = keys.next();
                JSONObject e = obj.getJSONObject(bssid);
                BaselineEntry be = new BaselineEntry();
                be.ssid = e.optString("ssid");
                be.security = e.optString("security");
                be.channel = e.optInt("channel");
                be.vendor = e.optString("vendor");
                be.firstSeen = e.optLong("first_seen");
                be.lastSaved = e.optLong("last_saved");
                map.put(bssid, be);
            }
        } catch (Exception ignored) {}
        return map;
    }

    public static JSONObject toBaselineJson(Map<String, BaselineEntry> baseline) {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, BaselineEntry> e : baseline.entrySet()) {
                BaselineEntry be = e.getValue();
                JSONObject jo = new JSONObject();
                jo.put("ssid", be.ssid == null ? "" : be.ssid);
                jo.put("security", be.security == null ? "" : be.security);
                jo.put("channel", be.channel);
                jo.put("vendor", be.vendor == null ? "" : be.vendor);
                jo.put("first_seen", be.firstSeen);
                jo.put("last_saved", be.lastSaved);
                obj.put(e.getKey(), jo);
            }
        } catch (Exception ignored) {}
        return obj;
    }
}
