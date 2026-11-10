# Wi-Fi Sentinel (Android)

A native Android port of the **Wi-Fi Sentinel** desktop tool — a WiFi security
scanner with multi-persona risk analysis, rogue / evil-twin detection, RF
channel intelligence, scan history, and RSSI-based triangulation.

Built with **no external dependencies** (no AppCompat, no Gradle at build time):
plain `Activity` + platform views, compiled with a direct
`aapt2 → javac → d8 → zip → apksigner` pipeline.

## Features

- **Live Scan** — real-time list of visible access points with risk score,
  RSSI, band, channel, security, vendor (offline OUI lookup), and estimated
  distance. Filter by SSID/BSSID/vendor; "suspicious only" toggle.
- **Radar** — polar plot of the top-N APs on distance rings. Colour = risk,
  dot size = signal, radius = estimated distance. Modes: risk / signal /
  selected.
- **Alerts** — anomaly feed: new APs, security downgrades, SSID changes,
  channel changes, mixed-security SSIDs, and evil-twin candidates
  (known SSID with a new BSSID).
- **History** — per-BSSID presence timeline (first/last seen, count,
  strongest RSSI), persisted on-device.
- **Survey / Triangulation** — capture RSSI measurements at known (x, y)
  coordinates and estimate the target AP's position by least-squares
  distance fitting (coarse-to-fine grid search).
- **Baseline** — mark trusted APs; the risk engine flags anything that
  deviates from the baseline (new, downgraded, or SSID-mismatched).
- **Ignore / Alias** — suppress noisy APs and rename known ones.

All data is stored locally in the app's private directory and never uploaded.

## Permissions

- `ACCESS_WIFI_STATE` — read WiFi scan results.
- `ACCESS_FINE_LOCATION` (Android 8+) — required by the OS for the system
  WiFi scan to return results. Requested at runtime on first scan.

## Requirements

- minSdk 24 (Android 7.0), targetSdk 34 (Android 14)
- No root required; passive scanning only (no deauth / injection).

## Build

The repo ships a Gradle-free build so you can produce an APK with just the
Android SDK command-line tools and a JDK.

### One-shot (Windows)

```powershell
# 1. Bootstrap the SDK (downloads cmdline-tools + build-tools 34 + platform 34)
powershell -ExecutionPolicy Bypass -File build/bootstrap_sdk.ps1

# 2. Build the APK (output: build/out/wifisentinel.apk)
powershell -ExecutionPolicy Bypass -File build/build_apk.ps1 -OutDir build/out
```

### Manual (any OS)

```bash
export ANDROID_SDK=/path/to/sdk
BT=$ANDROID_SDK/build-tools/34.0.0
PLATFORM=$ANDROID_SDK/platforms/android-34/android.jar

# Compile resources
$BT/aapt2 compile --dir app/src/main/res -o build/res/compiled.zip
$BT/aapt2 link -o build/app_unlinked.apk -I $PLATFORM \
    --manifest app/src/main/AndroidManifest.xml --java build/obj \
    --min-sdk-version 24 --target-sdk-version 34 build/res/compiled.zip/*.flat

# Compile Java
javac -source 11 -target 11 -classpath $PLATFORM -d build/obj \
    $(find app/src/main/java -name '*.java') build/obj/R.java

# Desugar to DEX
$BT/d8 --lib $PLATFORM --min-api 24 --output build/dex $(find build/obj -name '*.class')

# Assemble
cp build/app_unlinked.apk build/app_unsigned.apk
cd build/dex && zip -u ../app_unsigned.apk classes.dex && cd ../..
# add assets
cd app/src/main/assets && zip -r ../../build/app_unsigned.apk . && cd ../../..

# Align + sign
$BT/zipalign -f 4 build/app_unsigned.apk build/app_aligned.apk
$BT/apksigner sign --ks your.keystore --ks-key-alias wifisentinel \
    --ks-pass pass:android --out wifisentinel.apk build/app_aligned.apk
```

## Project layout

```
app/src/main/
├── AndroidManifest.xml
├── assets/oui.txt                     # bundled offline OUI vendor table
├── java/com/cadmus/wifisentinel/
│   ├── MainActivity.java              # UI: tabs, lists, actions
│   ├── BaseSentinelActivity.java      # runtime permission handling
│   ├── Ap.java                        # AP data model
│   ├── Scanner.java                   # WifiManager scan + averaging
│   ├── WifiMath.java                  # channel/RSSI/path-loss helpers
│   ├── RiskEngine.java                # risk scoring + rogue detection
│   ├── Triangulation.java             # RSSI position estimation
│   ├── Store.java                     # JSON persistence + OUI lookup
│   ├── HistoryRecord.java             # per-BSSID history model
│   ├── RadarView.java                 # polar radar canvas
│   └── SurveyView.java                # survey/triangulation canvas
└── res/
    ├── layout/activity_main.xml       # main screen (tab bar + panels)
    ├── layout/row_ap.xml              # live-list row
    ├── layout/row_history.xml         # history-list row
    ├── drawable/ic_launcher_foreground.xml
    ├── mipmap-anydpi-v26/ic_launcher.xml
    └── values/{colors,strings,styles}.xml
build/
├── bootstrap_sdk.ps1                  # SDK setup (Windows)
└── build_apk.ps1                      # APK build (Windows)
```

## Porting notes (vs. the Python/Tkinter reference)

- The reference shelled out to `nmcli`/`iw` on Linux. On Android the system
  WiFi scan (`WifiManager.getScanResults()`) is the native equivalent and
  requires location permission on API 28+.
- `manuf` (OPI vendor DB) → bundled `assets/oui.txt` (offline). Replace with
  the full IANA `ieee-oui.txt` export for complete coverage.
- Tkinter `Canvas` radar/survey → custom `View` subclasses with identical
  drawing logic (rings, risk-coloured dots, de-overlapped labels, grid-search
  estimate crosshair).
- JSON file stores (`baseline.json`, `history.json`, `survey.json`,
  `prefs.json`, `log.csv`) → same files under the app's private `filesDir`.

## License

MIT — see `LICENSE`.
