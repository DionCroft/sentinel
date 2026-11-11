$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"
$d8 = Join-Path $bt "d8.bat"

# All classes, full error output
$cls = Get-ChildItem $obj -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
$dex = Join-Path $work "dex_full"
if (Test-Path $dex) { Remove-Item $dex -Recurse -Force }
New-Item -ItemType Directory -Path $dex -Force | Out-Null
Write-Output "=== d8 full error (all classes) ==="
& $d8 --lib $androidJar --min-api 24 --output $dex $cls 2>&1 | Select-Object -First 40
Write-Output ("dex=" + (Test-Path (Join-Path $dex "classes.dex")))
