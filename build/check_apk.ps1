$proj = "C:\Users\viper\Projects\sentinel"
$apk = Join-Path $proj "build\out\wifisentinel.apk"
if (Test-Path $apk) {
    Write-Output ("APK EXISTS: " + (Get-Item $apk).Length + " bytes")
} else {
    Write-Output "APK NOT FOUND"
    Write-Output "--- build dir ---"
    Get-ChildItem (Join-Path $proj "build") -Recurse -ErrorAction SilentlyContinue | Select-Object FullName | Format-Table -AutoSize
    Write-Output "--- work dir tail ---"
    Get-ChildItem (Join-Path $proj "build\work") -ErrorAction SilentlyContinue | Select-Object Name,Length | Format-Table -AutoSize
}
