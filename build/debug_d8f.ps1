$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"
$d8 = Join-Path $bt "d8.bat"
$cls = Get-ChildItem $obj -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }

function TryD8($label, $extraArgs) {
    $dex = Join-Path $work "t_$label"
    if (Test-Path $dex) { Remove-Item $dex -Recurse -Force }
    New-Item -ItemType Directory -Path $dex -Force | Out-Null
    $err = & $d8 @extraArgs --lib $androidJar --min-api 24 --output $dex $cls 2>&1 | Out-String
    $ok = Test-Path (Join-Path $dex "classes.dex")
    Write-Output ("$label : dex=" + $ok + "  err=[" + ($err -replace "`r`n"," " | Select-Object -First 1) + "]")
}

Write-Output "=== base ==="
TryD8 "base" @()
Write-Output "=== --no-desugaring ==="
TryD8 "nodesg" @("--no-desugaring")
Write-Output "=== --release 24 ==="
TryD8 "rel24" @("--release","24")
Write-Output "=== --release 21 ==="
TryD8 "rel21" @("--release","21")
