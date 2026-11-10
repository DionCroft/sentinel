package com.cadmus.wifisentinel;

/**
 * A single observed Access Point, mirroring the Python reference AP dataclass.
 */
public class Ap {
    public String ssid;
    public String bssid;
    public int rssi;            // dBm
    public int freqMhz;
    public int channel;
    public String security;
    public String vendor;
    public Integer signalPct;   // null when not provided (Android gives dBm directly)

    public String getBand() {
        return freqMhz < 3000 ? "2.4GHz" : "5GHz";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Ap)) return false;
        return bssid.equals(((Ap) o).bssid);
    }

    @Override
    public int hashCode() {
        return bssid.hashCode();
    }

    @Override
    public String toString() {
        return ssid + " (" + bssid + ") " + rssi + "dBm " + security;
    }
}
