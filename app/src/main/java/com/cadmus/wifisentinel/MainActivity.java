package com.cadmus.wifisentinel;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Main UI: live scan list, radar, alerts, history, and survey tabs.
 * Port of the reference WifiSentinelApp (Tkinter) to a single-activity
 * Android app with a tab bar.
 */
public class MainActivity extends BaseSentinelActivity {

    // ---- state ----
    private List<Ap> aps = new ArrayList<>();
    private List<Ap> filteredAps = new ArrayList<>();
    private Map<String, Integer> riskMap = new HashMap<>();
    private List<String> alerts = new ArrayList<>();
    private Map<String, HistoryRecord> history = new HashMap<>();
    private Map<String, RiskEngine.BaselineEntry> baseline = new HashMap<>();
    private Map<String, String> aliases = new HashMap<>();
    private Map<String, List<Triangulation.Sample>> surveyData = new HashMap<>();
    private List<String> ignored = new ArrayList<>();

    private String selectedBssid;
    private String surveyTargetBssid;
    private Triangulation.Estimate surveyEstimate;
    private boolean autoRefresh = true;
    private int refreshSecs = 5;
    private int avgScans = 1;
    private double txPower1m = -40;
    private double pathLossN = 2.2;
    private String sortColumn = "Risk";
    private boolean sortReverse = true;
    private String filterText = "";
    private boolean suspiciousOnly = false;

    // ---- views ----
    private ListView liveList;
    private ListView historyList;
    private TextView alertsText;
    private TextView summaryTotal, summarySuspicious, summaryOpen, summaryStatus;
    private RadarView radarView;
    private SurveyView surveyView;
    private TextView surveyStatus;
    private EditText filterEditText, radarCountEdit, surveyXEdit, surveyYEdit;
    private CheckBox suspiciousOnlyCheck;
    private Spinner radarModeSpinner;
    private LinearLayout panelLive, panelRadar, panelAlerts, panelHistory, panelSurvey;
    private View root;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoRefreshRunnable;

    @Override
    protected void onReady() {
        super.onReady();
        setContentView(R.layout.activity_main);
        root = findViewById(R.id.root);
        bindViews();
        loadStores();
        setupTabs();
        setupLists();
        setupControls();
        setupActions();
        showTab(0);
        scanNow();
    }

    private void bindViews() {
        liveList = findViewById(R.id.liveList);
        historyList = findViewById(R.id.historyList);
        alertsText = findViewById(R.id.alertsText);
        summaryTotal = findViewById(R.id.summaryTotal);
        summarySuspicious = findViewById(R.id.summarySuspicious);
        summaryOpen = findViewById(R.id.summaryOpen);
        summaryStatus = findViewById(R.id.summaryStatus);
        radarView = findViewById(R.id.radarView);
        surveyView = findViewById(R.id.surveyView);
        surveyStatus = findViewById(R.id.surveyStatus);
        filterEditText = findViewById(R.id.filterText);
        radarCountEdit = findViewById(R.id.radarCount);
        surveyXEdit = findViewById(R.id.surveyX);
        surveyYEdit = findViewById(R.id.surveyY);
        suspiciousOnlyCheck = findViewById(R.id.suspiciousOnly);
        radarModeSpinner = findViewById(R.id.radarMode);
        panelLive = findViewById(R.id.panelLive);
        panelRadar = findViewById(R.id.panelRadar);
        panelAlerts = findViewById(R.id.panelAlerts);
        panelHistory = findViewById(R.id.panelHistory);
        panelSurvey = findViewById(R.id.panelSurvey);
    }

    // ----------------------------------------------------------------
    // Stores
    // ----------------------------------------------------------------
    private void loadStores() {
        File base = Store.baseDir(this);
        JSONObject histObj = Store.loadJson(Store.historyFile(this), new JSONObject());
        history = loadHistoryJson(histObj);
        JSONObject baseObj = Store.loadJson(Store.baselineFile(this), new JSONObject());
        baseline = RiskEngine.loadBaseline(baseObj);
        JSONObject prefsObj = Store.loadJson(Store.prefsFile(this), new JSONObject());
        aliases = loadStringMap(prefsObj, "aliases");
        ignored = loadStringList(prefsObj, "ignored");
        JSONObject surveyObj = Store.loadJson(Store.surveyFile(this), new JSONObject());
        surveyData = loadSurveyJson(surveyObj);
        Store.ensureOuiLoaded(this);
    }

    private void saveHistory() {
        JSONObject obj = new JSONObject();
        try {
            for (Map.Entry<String, HistoryRecord> e : history.entrySet()) {
                obj.put(e.getKey(), e.getValue().toMap());
            }
        } catch (Exception ignored) {}
        Store.saveJson(Store.historyFile(this), obj);
    }

    private void saveBaseline() {
        Store.saveJson(Store.baselineFile(this), RiskEngine.toBaselineJson(baseline));
    }

    private void savePrefs() {
        try {
            JSONObject obj = new JSONObject();
            JSONObject aliasesObj = new JSONObject(aliases);
            obj.put("aliases", aliasesObj);
            obj.put("ignored", new JSONArray(ignored));
            Store.saveJson(Store.prefsFile(this), obj);
        } catch (Exception ignored) {}
    }

    private void saveSurvey() {
        try {
            JSONObject obj = new JSONObject();
            for (Map.Entry<String, List<Triangulation.Sample>> e : surveyData.entrySet()) {
                JSONObject rec = new JSONObject();
                JSONArray arr = new JSONArray();
                for (Triangulation.Sample s : e.getValue()) {
                    JSONObject jo = new JSONObject();
                    jo.put("x", s.x);
                    jo.put("y", s.y);
                    jo.put("dist_m", s.distM);
                    jo.put("rssi", s.rssi);
                    jo.put("timestamp", s.timestamp == null ? "" : s.timestamp);
                    arr.put(jo);
                }
                rec.put("samples", arr);
                obj.put(e.getKey(), rec);
            }
            Store.saveJson(Store.surveyFile(this), obj);
        } catch (Exception ignored) {}
    }

    private Map<String, HistoryRecord> loadHistoryJson(JSONObject obj) {
        Map<String, HistoryRecord> map = new HashMap<>();
        try {
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String bssid = keys.next();
                JSONObject e = obj.getJSONObject(bssid);
                Map<String, Object> m = new HashMap<>();
                java.util.Iterator<String> f = e.keys();
                while (f.hasNext()) { String k = f.next(); m.put(k, e.get(k)); }
                map.put(bssid, HistoryRecord.fromMap(m));
            }
        } catch (Exception ignored) {}
        return map;
    }

    private Map<String, String> loadStringMap(JSONObject obj, String key) {
        Map<String, String> map = new HashMap<>();
        try {
            JSONObject sub = obj.getJSONObject(key);
            java.util.Iterator<String> it = sub.keys();
            while (it.hasNext()) { String k = it.next(); map.put(k, sub.optString(k)); }
        } catch (Exception ignored) {}
        return map;
    }

    private List<String> loadStringList(JSONObject obj, String key) {
        List<String> list = new ArrayList<>();
        try {
            JSONArray arr = obj.getJSONArray(key);
            for (int i = 0; i < arr.length(); i++) list.add(arr.optString(i));
        } catch (Exception ignored) {}
        return list;
    }

    private Map<String, List<Triangulation.Sample>> loadSurveyJson(JSONObject obj) {
        Map<String, List<Triangulation.Sample>> map = new HashMap<>();
        try {
            java.util.Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String bssid = keys.next();
                JSONObject rec = obj.getJSONObject(bssid);
                JSONArray arr = rec.optJSONArray("samples");
                if (arr == null) continue;
                List<Triangulation.Sample> samples = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject s = arr.getJSONObject(i);
                    Triangulation.Sample sample = new Triangulation.Sample();
                    sample.x = s.optDouble("x", 0);
                    sample.y = s.optDouble("y", 0);
                    sample.distM = s.optDouble("dist_m", 0);
                    sample.rssi = s.optInt("rssi", 0);
                    sample.timestamp = s.optString("timestamp");
                    samples.add(sample);
                }
                map.put(bssid, samples);
            }
        } catch (Exception ignored) {}
        return map;
    }

    // ----------------------------------------------------------------
    // Scanning
    // ----------------------------------------------------------------
    private boolean scanning = false;

    private void scanNow() {
        if (scanning) return;
        scanning = true;
        summaryStatus.setText("Scanning…");

        new Thread(() -> {
            final List<Ap> result;
            try {
                result = Scanner.scanOnce(this);
            } catch (Exception e) {
                result = new ArrayList<>();
            }
            final List<Ap> finalResult = result;
            handler.post(() -> {
                applyScanResults(finalResult);
                scanning = false;
                summaryStatus.setText("Ready.");
                if (autoRefresh) scheduleAutoRefresh();
            });
        }).start();
    }

    private void applyScanResults(List<Ap> scanAps) {
        aps = scanAps;
        updateHistory(aps);

        RiskEngine.Result res = RiskEngine.analyse(aps, baseline);
        alerts = res.alerts;
        riskMap = res.riskMap;

        refreshAllViews();
    }

    private void scheduleAutoRefresh() {
        if (autoRefreshRunnable != null) handler.removeCallbacks(autoRefreshRunnable);
        autoRefreshRunnable = new Runnable() {
            @Override public void run() {
                scanNow();
            }
        };
        handler.postDelayed(autoRefreshRunnable, refreshSecs * 1000L);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (autoRefreshRunnable != null) handler.removeCallbacks(autoRefreshRunnable);
    }

    private void updateHistory(List<Ap> scanAps) {
        String now = timestamp();
        for (Ap ap : scanAps) {
            HistoryRecord rec = history.get(ap.bssid);
            if (rec == null) {
                rec = new HistoryRecord();
                rec.bssid = ap.bssid;
                rec.firstSeen = now;
                rec.seenCount = 0;
                rec.strongestRssi = Integer.MIN_VALUE;
            }
            rec.ssid = ap.ssid;
            rec.security = ap.security;
            rec.vendor = ap.vendor;
            rec.seenCount += 1;
            rec.lastSeen = now;
            if (ap.rssi > rec.strongestRssi) rec.strongestRssi = ap.rssi;
            history.put(ap.bssid, rec);
        }
        saveHistory();
    }

    // ----------------------------------------------------------------
    // Filtering / sorting
    // ----------------------------------------------------------------
    private List<Ap> filteredSortedAps() {
        String text = filterText.toLowerCase();
        List<Ap> result = new ArrayList<>();
        for (Ap ap : aps) {
            if (ignored.contains(ap.bssid)) continue;
            int risk = riskMap.getOrDefault(ap.bssid, 0);
            String alias = aliases.getOrDefault(ap.bssid, "");
            String hay = (ap.ssid + " " + alias + " " + ap.bssid + " "
                    + ap.vendor + " " + ap.security).toLowerCase();
            if (!text.isEmpty() && !hay.contains(text)) continue;
            if (suspiciousOnly && risk < 30) continue;
            result.add(ap);
        }

        result.sort((a, b) -> {
            int cmp = 0;
            switch (sortColumn) {
                case "Risk":
                    cmp = Integer.compare(riskMap.getOrDefault(a.bssid, 0), riskMap.getOrDefault(b.bssid, 0));
                    break;
                case "RSSI":
                    cmp = Integer.compare(a.rssi, b.rssi);
                    break;
                case "SSID":
                    cmp = a.ssid.compareToIgnoreCase(b.ssid);
                    break;
                case "Channel":
                    cmp = Integer.compare(a.channel, b.channel);
                    break;
                case "Vendor":
                    cmp = a.vendor.compareToIgnoreCase(b.vendor);
                    break;
                default:
                    cmp = Integer.compare(riskMap.getOrDefault(a.bssid, 0), riskMap.getOrDefault(b.bssid, 0));
            }
            return sortReverse ? -cmp : cmp;
        });
        return result;
    }

    private void refreshAllViews() {
        filteredAps = filteredSortedAps();
        populateLiveList();
        populateAlerts();
        populateHistoryList();
        updateSummary();
        radarView.setData(filteredAps, riskMap);
        radarView.setAliases(aliases);
        refreshSurveyView();
    }

    private void updateSummary() {
        int total = aps.size();
        int suspicious = 0, open = 0;
        for (Ap ap : aps) {
            if (ignored.contains(ap.bssid)) continue;
            if (riskMap.getOrDefault(ap.bssid, 0) >= 30) suspicious++;
            if ("OPEN".equals(ap.security)) open++;
        }
        summaryTotal.setText("APs: " + total);
        summarySuspicious.setText("Susp: " + suspicious);
        summaryOpen.setText("Open: " + open);
    }

    // ----------------------------------------------------------------
    // Tabs
    // ----------------------------------------------------------------
    private int currentTab = 0;

    private void setupTabs() {
        Button tabLive = findViewById(R.id.tabLive);
        Button tabRadar = findViewById(R.id.tabRadar);
        Button tabAlerts = findViewById(R.id.tabAlerts);
        Button tabHistory = findViewById(R.id.tabHistory);
        Button tabSurvey = findViewById(R.id.tabSurvey);
        tabLive.setOnClickListener(v -> showTab(0));
        tabRadar.setOnClickListener(v -> showTab(1));
        tabAlerts.setOnClickListener(v -> showTab(2));
        tabHistory.setOnClickListener(v -> showTab(3));
        tabSurvey.setOnClickListener(v -> showTab(4));

        Button btnScan = findViewById(R.id.btnScan);
        btnScan.setOnClickListener(v -> scanNow());

        // Radar mode spinner.
        String[] modes = {"risk", "signal", "selected"};
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        radarModeSpinner.setAdapter(modeAdapter);
        radarModeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                radarView.setRadarMode(modes[pos]);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        radarCountEdit.addTextChangedListener(textChangedWatcher(
                s -> { try { radarView.setTopCount(Math.max(1, Integer.parseInt(s.toString()))); } catch (Exception ignored) {} }));
    }

    private void showTab(int index) {
        currentTab = index;
        panelLive.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        panelRadar.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        panelAlerts.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        panelHistory.setVisibility(index == 3 ? View.VISIBLE : View.GONE);
        panelSurvey.setVisibility(index == 4 ? View.VISIBLE : View.GONE);
        if (index == 0) populateLiveList();
        if (index == 1) radarView.invalidate();
        if (index == 2) populateAlerts();
        if (index == 3) populateHistoryList();
        if (index == 4) refreshSurveyView();
    }

    // ----------------------------------------------------------------
    // Live list
    // ----------------------------------------------------------------
    private void setupLists() {
        liveList.setOnItemClickListener((parent, view, position, id) -> {
            Ap ap = (Ap) liveList.getItemAtPosition(position);
            selectedBssid = ap.bssid;
            radarView.setSelectedBssid(selectedBssid);
            Toast.makeText(this, "Selected: " + ap.ssid, Toast.LENGTH_SHORT).show();
        });
    }

    private void populateLiveList() {
        liveList.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return filteredAps.size(); }
            @Override public Object getItem(int position) { return filteredAps.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(MainActivity.this)
                            .inflate(R.layout.row_ap, parent, false);
                }
                Ap ap = filteredAps.get(position);
                int risk = riskMap.getOrDefault(ap.bssid, 0);
                TextView riskView = row.findViewById(R.id.rowRisk);
                TextView ssidView = row.findViewById(R.id.rowSsid);
                TextView metaView = row.findViewById(R.id.rowMeta);
                TextView rssiView = row.findViewById(R.id.rowRssi);
                TextView secView = row.findViewById(R.id.rowSec);

                riskView.setText(String.valueOf(risk));
                int color = risk >= 60 ? 0xFFD32F2F : (risk >= 30 ? 0xFFF9A825 : 0xFF2E7D32);
                riskView.setTextColor(color);

                String alias = aliases.getOrDefault(ap.bssid, "");
                ssidView.setText(alias.isEmpty() ? ap.ssid : alias + " (" + ap.ssid + ")");
                metaView.setText(ap.bssid + "  ·  " + ap.vendor + "  ·  " + ap.band + "  Ch " + ap.channel);
                rssiView.setText(ap.rssi + " dBm");
                secView.setText(ap.security);
                return row;
            }
        });
    }

    // ----------------------------------------------------------------
    // Alerts
    // ----------------------------------------------------------------
    private void populateAlerts() {
        if (alerts.isEmpty()) {
            alertsText.setText("No anomalies detected.");
            return;
        }
        StringBuilder sb = new StringBuilder();
        List<String> priority = new ArrayList<>();
        List<String> normal = new ArrayList<>();
        for (String a : alerts) {
            if (a.contains("DOWNGRADE") || a.contains("Evil-twin") || a.contains("NEW strong AP")) {
                priority.add(a);
            } else {
                normal.add(a);
            }
        }
        for (String a : priority) sb.append("⚠ ").append(a).append("\n");
        for (String a : normal) sb.append("• ").append(a).append("\n");
        alertsText.setText(sb.toString());
    }

    // ----------------------------------------------------------------
    // History
    // ----------------------------------------------------------------
    private void populateHistoryList() {
        List<HistoryRecord> items = new ArrayList<>(history.values());
        items.sort((a, b) -> b.lastSeen == null ? 1 : (a.lastSeen == null ? -1 : b.lastSeen.compareTo(a.lastSeen)));
        historyList.setAdapter(new BaseAdapter() {
            @Override public int getCount() { return items.size(); }
            @Override public Object getItem(int position) { return items.get(position); }
            @Override public long getItemId(int position) { return position; }
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View row = convertView;
                if (row == null) {
                    row = LayoutInflater.from(MainActivity.this)
                            .inflate(R.layout.row_history, parent, false);
                }
                HistoryRecord rec = items.get(position);
                row.findViewById(R.id.histSsid).setText(
                        rec.ssid == null ? "" : rec.ssid);
                row.findViewById(R.id.histBssid).setText(rec.bssid);
                row.findViewById(R.id.histSeen).setText("×" + rec.seenCount);
                row.findViewById(R.id.histFirst).setText("First: " + (rec.firstSeen == null ? "-" : rec.firstSeen));
                row.findViewById(R.id.histLast).setText("Last: " + (rec.lastSeen == null ? "-" : rec.lastSeen));
                return row;
            }
        });
    }

    // ----------------------------------------------------------------
    // Controls
    // ----------------------------------------------------------------
    private void setupControls() {
        filterEditText.addTextChangedListener(textChangedWatcher(s -> {
            filterText = s.toString().trim();
            refreshAllViews();
        }));
        suspiciousOnlyCheck.setOnCheckedChangeListener((button, checked) -> {
            suspiciousOnly = checked;
            refreshAllViews();
        });
    }

    private android.text.TextWatcher textChangedWatcher(java.util.function.Consumer<android.text.Editable> onText) {
        return new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (onText != null) onText.accept(s);
            }
        };
    }

    // ----------------------------------------------------------------
    // Bottom actions
    // ----------------------------------------------------------------
    private void setupActions() {
        findViewById(R.id.btnBaselineAdd).setOnClickListener(v -> baselineAddSelected());
        findViewById(R.id.btnBaselineAll).setOnClickListener(v -> baselineAddAll());
        findViewById(R.id.btnClearBaseline).setOnClickListener(v -> clearBaseline());
        findViewById(R.id.btnIgnore).setOnClickListener(v -> toggleIgnoreSelected());

        findViewById(R.id.btnCapture).setOnClickListener(v -> captureSurveyPoint());
        findViewById(R.id.btnEstimate).setOnClickListener(v -> estimateSurvey());
        findViewById(R.id.btnClearSurvey).setOnClickListener(v -> clearSurveyTarget());
    }

    private Ap selectedAp() {
        if (selectedBssid == null) return null;
        for (Ap ap : aps) if (ap.bssid.equals(selectedBssid)) return ap;
        return null;
    }

    private void baselineAddSelected() {
        Ap ap = selectedAp();
        if (ap == null) {
            Toast.makeText(this, "Select an AP first (tap a row in Live)", Toast.LENGTH_SHORT).show();
            return;
        }
        addBaseline(ap, "baseline_add");
        recomputeAfterBaselineChange();
        Toast.makeText(this, "Added to baseline: " + ap.ssid, Toast.LENGTH_SHORT).show();
    }

    private void baselineAddAll() {
        if (filteredAps.isEmpty()) {
            Toast.makeText(this, "No visible APs", Toast.LENGTH_SHORT).show();
            return;
        }
        for (Ap ap : filteredAps) addBaseline(ap, "baseline_add_all");
        recomputeAfterBaselineChange();
        Toast.makeText(this, "Added " + filteredAps.size() + " APs to baseline", Toast.LENGTH_SHORT).show();
    }

    private void clearBaseline() {
        baseline.clear();
        saveBaseline();
        recomputeAfterBaselineChange();
        Toast.makeText(this, "Baseline cleared", Toast.LENGTH_SHORT).show();
    }

    private void addBaseline(Ap ap, String note) {
        RiskEngine.BaselineEntry be = new RiskEngine.BaselineEntry();
        be.ssid = ap.ssid;
        be.security = ap.security;
        be.channel = ap.channel;
        be.vendor = ap.vendor;
        be.firstSeen = System.currentTimeMillis() / 1000;
        be.lastSaved = System.currentTimeMillis() / 1000;
        baseline.put(ap.bssid, be);
        saveBaseline();
        logCsvRow(ap, note);
    }

    private void recomputeAfterBaselineChange() {
        RiskEngine.Result res = RiskEngine.analyse(aps, baseline);
        alerts = res.alerts;
        riskMap = res.riskMap;
        refreshAllViews();
    }

    private void toggleIgnoreSelected() {
        Ap ap = selectedAp();
        if (ap == null) {
            Toast.makeText(this, "Select an AP first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ignored.contains(ap.bssid)) ignored.remove(ap.bssid);
        else ignored.add(ap.bssid);
        Collections.sort(ignored);
        savePrefs();
        refreshAllViews();
    }

    private void logCsvRow(Ap ap, String note) {
        try {
            File f = Store.logFile(this);
            boolean header = !f.exists();
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            if (header) w.write("timestamp,ssid,bssid,rssi,freq_mhz,channel,security,vendor,note\n");
            w.write(String.format(java.util.Locale.US, "%s,%s,%s,%d,%d,%d,%s,%s,%s\n",
                    timestamp(), csv(ap.ssid), ap.bssid, ap.rssi, ap.freqMhz,
                    ap.channel, csv(ap.security), csv(ap.vendor), csv(note)));
            w.close();
        } catch (Exception ignored) {}
    }

    private String csv(String s) {
        if (s == null) return "";
        s = s.replace("\"", "\"\"");
        if (s.contains(",") || s.contains("\"")) s = "\"" + s + "\"";
        return s;
    }

    // ----------------------------------------------------------------
    // Survey / triangulation
    // ----------------------------------------------------------------
    private void refreshSurveyView() {
        if (surveyTargetBssid == null) {
            surveyView.setData("", "", new ArrayList<>(), null);
            surveyStatus.setText("No survey target selected.");
            return;
        }
        Ap target = findAp(surveyTargetBssid);
        String name = target != null ? target.ssid : surveyTargetBssid;
        List<Triangulation.Sample> samples = surveyData.getOrDefault(surveyTargetBssid, new ArrayList<>());
        surveyView.setData(name, surveyTargetBssid, samples, surveyEstimate);

        if (surveyEstimate != null) {
            surveyStatus.setText(String.format(java.util.Locale.US,
                    "Estimated AP position: (%.2f, %.2f)   RMSE: %.2f m",
                    surveyEstimate.x, surveyEstimate.y, surveyEstimate.rmse));
        } else {
            surveyStatus.setText("Need at least 3 samples to estimate AP position. Current: " + samples.size());
        }
    }

    private Ap findAp(String bssid) {
        for (Ap ap : aps) if (ap.bssid.equals(bssid)) return ap;
        return null;
    }

    private void captureSurveyPoint() {
        if (surveyTargetBssid == null) {
            // Default to the selected AP.
            Ap ap = selectedAp();
            if (ap == null) {
                Toast.makeText(this, "Select an AP first, then capture", Toast.LENGTH_SHORT).show();
                return;
            }
            surveyTargetBssid = ap.bssid;
        }
        Ap ap = findAp(surveyTargetBssid);
        if (ap == null) {
            Toast.makeText(this, "Target AP not in current scan", Toast.LENGTH_SHORT).show();
            return;
        }
        double x, y;
        try {
            x = Double.parseDouble(surveyXEdit.getText().toString().trim());
            y = Double.parseDouble(surveyYEdit.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "Invalid X/Y", Toast.LENGTH_SHORT).show();
            return;
        }
        Double dist = WifiMath.estimateDistanceM(ap.rssi, ap.freqMhz, txPower1m, pathLossN);
        if (dist == null) {
            Toast.makeText(this, "Could not estimate distance", Toast.LENGTH_SHORT).show();
            return;
        }
        Triangulation.Sample sample = new Triangulation.Sample();
        sample.x = x;
        sample.y = y;
        sample.distM = dist;
        sample.rssi = ap.rssi;
        sample.timestamp = timestamp();

        List<Triangulation.Sample> samples = surveyData.get(surveyTargetBssid);
        if (samples == null) { samples = new ArrayList<>(); surveyData.put(surveyTargetBssid, samples); }
        samples.add(sample);
        saveSurvey();

        surveyStatus.setText(String.format(java.util.Locale.US,
                "Captured measurement at (%.2f, %.2f), dist %.2f m", x, y, dist));
        estimateSurvey();
    }

    private void estimateSurvey() {
        if (surveyTargetBssid == null) {
            surveyEstimate = null;
            refreshSurveyView();
            return;
        }
        List<Triangulation.Sample> samples = surveyData.getOrDefault(surveyTargetBssid, new ArrayList<>());
        surveyEstimate = Triangulation.estimate(samples);
        refreshSurveyView();
    }

    private void clearSurveyTarget() {
        if (surveyTargetBssid == null) {
            Toast.makeText(this, "No survey target", Toast.LENGTH_SHORT).show();
            return;
        }
        surveyData.remove(surveyTargetBssid);
        saveSurvey();
        surveyEstimate = null;
        Toast.makeText(this, "Survey samples cleared", Toast.LENGTH_SHORT).show();
        refreshSurveyView();
    }

    /** Allow the user to set the survey target from the live list (long-press). */
    @Override
    protected void onReady() {
        super.onReady();
    }
}

