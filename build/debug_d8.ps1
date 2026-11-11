$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"

Write-Output "=== class files ==="
$classFiles = Get-ChildItem $obj -Filter "*.class" -Recurse -ErrorAction SilentlyContinue
Write-Output ("count: " + $classFiles.Count)
$classFiles | ForEach-Object { Write-Output $_.FullName }

Write-Output "=== d8 version ==="
$d8 = Join-Path $bt "d8.bat"
& $d8 --version 2>&1 | Select-Object -First 3

Write-Output "=== try d8 with explicit file list ==="
$dexDir = Join-Path $work "dex2"
New-Item -ItemType Directory -Path $dexDir -Force | Out-Null
$files = $classFiles | ForEach-Object { $_.FullName }
& $d8 --lib $androidJar --min-api 24 --output $dexDir $files 2>&1 | Select-Object -First 20
Write-Output ("d8 exit: " + $LASTEXITCODE)
Write-Output ("classes.dex exists: " + (Test-Path (Join-Path $dexDir "classes.dex")))
