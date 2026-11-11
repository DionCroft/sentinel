$proj = "C:\Users\viper\Projects\sentinel"
Write-Output "=== before ==="
Write-Output ("sentinel exists: " + (Test-Path $proj))
if (Test-Path $proj) {
    $s = Join-Path $proj "app\src\main\java\com\cadmus\wifisentinel\Scanner.java"
    if (Test-Path $s) { Write-Output ("Scanner line 35: " + (Get-Content $s)[34]) }
}
Write-Output "=== delete sentinel ==="
if (Test-Path $proj) { Remove-Item $proj -Recurse -Force }
Write-Output ("after delete, exists: " + (Test-Path $proj))
New-Item -ItemType Directory -Path $proj -Force | Out-Null
Write-Output "=== extract ==="
$tar = "C:\Users\viper\Projects\sentinel-src.tar.gz"
tar -xzf $tar -C $proj 2>&1
Write-Output ("tar exit: " + $LASTEXITCODE)
Write-Output "=== after extract ==="
Write-Output ("file count: " + (Get-ChildItem $proj -Recurse -File -ErrorAction SilentlyContinue | Measure-Object).Count)
$s = Join-Path $proj "app\src\main\java\com\cadmus\wifisentinel\Scanner.java"
if (Test-Path $s) {
    Write-Output ("Scanner line 35: " + (Get-Content $s)[34])
} else {
    Write-Output "Scanner.java missing after extract"
    Get-ChildItem $proj -Recurse -File -ErrorAction SilentlyContinue | Select-Object -First 10 | ForEach-Object { $_.FullName }
}
