$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"
$d8 = Join-Path $bt "d8.bat"

Write-Output "=== javap R.class (check nested refs) ==="
& (Join-Path $env:JAVA_HOME "bin\javap.exe") -p -c (Join-Path $obj "com\cadmus\wifisentinel\R.class") 2>&1 | Select-Object -First 8

Write-Output ""
Write-Output "=== class file version of R.class ==="
$bytes = [System.IO.File]::ReadAllBytes((Join-Path $obj "com\cadmus\wifisentinel\R.class"))
$minor = [int]($bytes[6] -shl 8) + $bytes[7]
$major = [int]($bytes[4] -shl 8) + $bytes[5]
Write-Output "major=$major (Java $(( $major - 44 )))  minor=$minor"

Write-Output ""
Write-Output "=== Try d8 on R.class with --no-desugaring ==="
$dex = Join-Path $work "t_r_nodsg"
if (Test-Path $dex) { Remove-Item $dex -Recurse -Force }
New-Item -ItemType Directory -Path $dex -Force | Out-Null
& $d8 --no-desugaring --lib $androidJar --min-api 24 --output $dex (Join-Path $obj "com\cadmus\wifisentinel\R.class") 2>&1 | Select-Object -First 5
Write-Output ("dex=" + (Test-Path (Join-Path $dex "classes.dex")))

Write-Output ""
Write-Output "=== Try d8 on R.class + R\$id.class together ==="
$dex2 = Join-Path $work "t_r_id"
if (Test-Path $dex2) { Remove-Item $dex2 -Recurse -Force }
New-Item -ItemType Directory -Path $dex2 -Force | Out-Null
& $d8 --lib $androidJar --min-api 24 --output $dex2 (Join-Path $obj "com\cadmus\wifisentinel\R.class") (Join-Path $obj "com\cadmus\wifisentinel\R`$id.class") 2>&1 | Select-Object -First 5
Write-Output ("dex=" + (Test-Path (Join-Path $dex2 "classes.dex")))
