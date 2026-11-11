$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$latestBin = Join-Path $sdkRoot "cmdline-tools\latest\bin\sdkmanager.bat"

Write-Output "=== Install build-tools 35.0.0 ==="
$yes = Join-Path $env:TEMP "sdk_yes35.txt"
Set-Content $yes ("y`n" * 40) -Encoding ASCII
$proc = Start-Process -FilePath $latestBin -ArgumentList "--sdk_root=$sdkRoot", "build-tools;35.0.0" -NoNewWindow -PassThru -RedirectStandardInput $yes
$proc.WaitForExit()
Write-Output ("sdkmanager exit: " + $proc.ExitCode)

$bt35 = Join-Path $sdkRoot "build-tools\35.0.0"
Write-Output ("build-tools 35 present: " + (Test-Path $bt35))
Write-Output ("d8.bat in 35: " + (Test-Path (Join-Path $bt35 "d8.bat")))

# Test d8 35 on the existing class files
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$d8 = Join-Path $bt35 "d8.bat"
$cls = Get-ChildItem $obj -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
$dex = Join-Path $work "dex35"
if (Test-Path $dex) { Remove-Item $dex -Recurse -Force }
New-Item -ItemType Directory -Path $dex -Force | Out-Null
Write-Output "=== d8 35 test ==="
& $d8 --lib $androidJar --min-api 24 --output $dex $cls 2>&1 | Select-Object -First 8
Write-Output ("d8 35 dex=" + (Test-Path (Join-Path $dex "classes.dex")))
