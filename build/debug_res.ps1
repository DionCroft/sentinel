$work = "C:\Users\viper\Projects\sentinel\build\work"
Write-Output "--- res dir ---"
Get-ChildItem (Join-Path $work "res") -ErrorAction SilentlyContinue | Select-Object Name,Length | Format-Table -AutoSize
Write-Output "--- compiled.zip? ---"
Test-Path (Join-Path $work "res\compiled.zip")
Write-Output "--- flat count ---"
(Get-ChildItem (Join-Path $work "res") -Filter "*.flat" -ErrorAction SilentlyContinue).Count
Write-Output "--- try aapt2 compile manually ---"
$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$proj = "C:\Users\viper\Projects\sentinel"
$resDir = Join-Path $proj "app\src\main\res"
$outDir = Join-Path $work "res2"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null
& (Join-Path $bt "aapt2.exe") compile --dir $resDir -o (Join-Path $outDir "out.zip")
Write-Output "aapt2 compile exit: $LASTEXITCODE"
Write-Output "--- res2 contents ---"
Get-ChildItem $outDir -ErrorAction SilentlyContinue | Select-Object Name,Length | Format-Table -AutoSize
