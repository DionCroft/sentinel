package com.cadmus.wifisentinel;

import android.content.Context;
import android.content.res.AssetManager;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * JSON file persistence (baseline / history / survey / prefs) plus an
 * offline OUI vendor lookup, mirroring the reference's JSON-file stores
 * and its `manuf`-based vendor identification.
 */
public final class Store {

    public static File baseDir(Context ctx) {
        File d = new File(ctx.getFilesDir(), "wifisentinel");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    public static File baselineFile(Context ctx) { return new File(baseDir(ctx), "baseline.json"); }
    public static File historyFile(Context ctx)  { return new File(baseDir(ctx), "history.json"); }
    public static File surveyFile(Context ctx)   { return new File(baseDir(ctx), "survey.json"); }
    public static File prefsFile(Context ctx)    { return new File(baseDir(ctx), "prefs.json"); }
    public static File logFile(Context ctx)      { return new File(baseDir(ctx), "log.csv"); }

    // ----------------------------------------------------------------
    // JSON read / write
    // ----------------------------------------------------------------
    public static JSONObject loadJson(File f, JSONObject fallback) {
        if (!f.exists()) return fallback;
        try {
            byte[] buf = new byte[(int) Math.max(1, f.length())];
            InputStream in = new FileInputStream(f);
            int off = 0;
            while (off < buf.length) {
                int r = in.read(buf, off, buf.length - off);
                if (r < 0) break;
                off += r;
            }
            in.close();
            return new JSONObject(new String(buf, "UTF-8"));
        } catch (Exception e) {
            return fallback;
        }
    }

    public static void saveJson(File f, JSONObject obj) {
        try {
            OutputStream out = new FileOutputStream(f);
            out.write(obj.toString(2).getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) {}
    }

    // ----------------------------------------------------------------
    // OUI vendor lookup (offline)
    // ----------------------------------------------------------------
    private static final Map<String, String> OUI = new HashMap<>();
    private static volatile boolean ouiLoaded = false;

    /** Lazily load the bundled OUI table from assets on first use. */
    public static synchronized void ensureOuiLoaded(Context ctx) {
        if (ouiLoaded) return;
        ouiLoaded = true;
        try {
            AssetManager am = ctx.getAssets();
            InputStream in = am.open("oui.txt");
            BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                // Accept "AABBCC  Vendor" or "AABBCC\tVendor".
                String[] parts = line.split("\\s{2,}|\\t", 2);
                if (parts.length < 2) continue;
                String oui = parts[0].toUpperCase().trim();
                if (oui.length() < 6) continue;
                oui = oui.substring(0, 6);
                OUI.put(oui, parts[1].trim());
            }
            br.close();
        } catch (Exception ignored) {
            // No bundled table — built-in fallback only.
        }
    }

    public static String lookupVendor(Context ctx, String bssid) {
        ensureOuiLoaded(ctx);
        String oui = ouiPrefix(bssid);
        if (oui.isEmpty()) return "Unknown";
        String v = OUI.get(oui);
        if (v != null) return v;
        v = BUILTIN.get(oui);
        return v != null ? v : "Unknown";
    }

    private static final Map<String, String> BUILTIN = new HashMap<>();
    static {
        BUILTIN.put("001A11", "Google");
        BUILTIN.put("005043", "Apple");
        BUILTIN.put("001B63", "Apple");
        BUILTIN.put("F4E27D", "Intel");
        BUILTIN.put("0017F2", "Cisco / Linksys");
        BUILTIN.put("C83A35", "TP-Link");
        BUILTIN.put("B827EB", "Raspberry Pi");
        BUILTIN.put("DCA632", "Raspberry Pi");
        BUILTIN.put("B4430D", "Raspberry Pi");
        BUILTIN.put("E47520", "Samsung");
        BUILTIN.put("00805F", "Espressif (ESP32)");
        BUILTIN.put("001E58", "Espressif (ESP32)");
        BUILTIN.put("94D9B3", "Espressif (ESP32)");
        BUILTIN.put("0024D7", "Espressif (ESP32)");
        BUILTIN.put("AC67B2", "Intel");
        BUILTIN.put("3C22FB", "Intel");
        BUILTIN.put("A0F3C1", "Microsoft");
        BUILTIN.put("00155D", "Microsoft");
    }

    private static String ouiPrefix(String bssid) {
        if (bssid == null) return "";
        String s = bssid.replace(":", "").replace("-", "").toUpperCase();
        if (s.length() < 6) return s;
        return s.substring(0, 6);
    }
}
