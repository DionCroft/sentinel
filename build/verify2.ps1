$apk = "C:\Users\viper\Projects\sentinel\build\out\wifisentinel.apk"
Write-Output "=== APK size ==="
Write-Output ("Size: " + (Get-Item $apk).Length + " bytes")

Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($apk)
Write-Output "=== Key entries ==="
$z.Entries | ForEach-Object {
    if ($_.FullName -match "classes.dex|assets/|AndroidManifest|resources.arsc|mipmap|drawable") {
        Write-Output ("  " + $_.FullName + " (" + $_.Length + ")")
    }
}
Write-Output ("Total entries: " + $z.Entries.Count)
$z.Dispose()
