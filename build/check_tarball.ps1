Write-Output "=== tarball on Arete ==="
$t = "C:\Users\viper\Projects\sentinel-src.tar.gz"
Write-Output ("exists: " + (Test-Path $t) + "  size: " + (Get-Item $t).Length + "  modified: " + (Get-Item $t).LastWriteTime)
Write-Output "=== Scanner.java line 35 INSIDE tarball ==="
$dir = "C:\Users\viper\Projects\tarball_check"
if (Test-Path $dir) { Remove-Item $dir -Recurse -Force }
New-Item -ItemType Directory -Path $dir -Force | Out-Null
tar -xzf $t -C $dir
$s = Join-Path $dir "app\src\main\java\com\cadmus\wifisentinel\Scanner.java"
if (Test-Path $s) {
    Write-Output ((Get-Content $s)[34])
} else {
    Write-Output "Scanner.java NOT in tarball; listing:"
    Get-ChildItem $dir -Recurse -Filter "Scanner.java" | ForEach-Object { $_.FullName }
}
Write-Output "=== MainActivity File import in tarball ==="
$m = Get-ChildItem $dir -Recurse -Filter "MainActivity.java" | Select-Object -First 1
if ($m) {
    Select-String -Path $m.FullName -Pattern "import java.io.File" | ForEach-Object { $_.Line }
}
