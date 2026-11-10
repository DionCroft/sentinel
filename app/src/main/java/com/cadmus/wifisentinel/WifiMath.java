package com.cadmus.wifisentinel;

/**
 * Pure math helpers, ported 1:1 from the Python reference.
 */
public final class WifiMath {
    private WifiMath() {}

    /** 2.4/5 GHz frequency (MHz) -> channel number. */
    public static int freqToChannel(int freqMhz) {
        if (freqMhz >= 2412 && freqMhz <= 2472) return (freqMhz - 2407) / 5;
        if (freqMhz == 2484) return 14;
        if (freqMhz >= 5000 && freqMhz <= 5900) return (freqMhz - 5000) / 5;
        return 0;
    }

    /** Linear RSSI (dBm) -> 0..100 quality, matching the reference scale. */
    public static int rssiToQuality(int rssi) {
        if (rssi <= -100) return 0;
        if (rssi >= -30) return 100;
        return (int) ((rssi + 100) * (100.0 / 70.0));
    }

    /** 0..100 quality -> RSSI (dBm). */
    public static int qualityToRssiDbm(int quality) {
        int q = Math.max(0, Math.min(100, quality));
        return (int) (-100 + (q * 50.0 / 100.0));
    }

    /**
     * Log-distance path-loss model to estimate distance (metres) from RSSI.
     * Returns null when inputs are unusable.
     */
    public static Double estimateDistanceM(int rssi, int freqMhz, double txPowerDbm, double n) {
        if (rssi <= -900 || freqMhz <= 0) return null;
        return Math.pow(10.0, (txPowerDbm - rssi) / (10.0 * n));
    }

    public static double distanceXy(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** Security strength rank, matching the reference _security_rank. */
    public static int securityRank(String sec) {
        String s = (sec == null ? "" : sec).toUpperCase();
        if (s.equals("WPA3")) return 5;
        if (s.contains("WPA2") && s.contains("WPA3")) return 5;
        if (s.equals("WPA2")) return 4;
        if (s.contains("WPA/WPA2") || (s.contains("WPA") && s.contains("WPA2"))) return 4;
        if (s.equals("WPA")) return 3;
        if (s.equals("WEP")) return 1;
        if (s.equals("OPEN")) return 0;
        return 2;
    }

    /** Normalise Android's encryption string into the reference's security labels. */
    public static String normalizeSecurity(int encryption) {
        // Encryption flags from WifiInfo.getEncryptionType()
        if (encryption == 0) return "OPEN";
        boolean wpa = (encryption & 1) != 0;   // WPA
        boolean rsn = (encryption & 2) != 0;   // RSN (WPA2)
        // 802.1X / WPA3 (SAE) is not reliably exposed pre-Android 10; treat RSN as WPA2.
        if (rsn && wpa) return "WPA/WPA2";
        if (rsn) return "WPA2";
        if (wpa) return "WPA";
        return "OPEN";
    }
}
