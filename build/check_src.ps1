$proj = "C:\Users\viper\Projects\sentinel"
$scanner = Join-Path $proj "app\src\main\java\com\cadmus\wifisentinel\Scanner.java"
$main = Join-Path $proj "app\src\main\java\com\cadmus\wifisentinel\MainActivity.java"
Write-Output "=== Scanner.java line 35 ==="
(Get-Content $scanner)[34]
Write-Output "=== MainActivity File import ==="
Select-String -Path $main -Pattern "import java.io.File" | ForEach-Object { $_.Line }
Write-Output "=== MainActivity line 473 ==="
(Get-Content $main)[472]
Write-Output "=== MainActivity line 80-82 ==="
$lines = Get-Content $main
$lines[79..81]
