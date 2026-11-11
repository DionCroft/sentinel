$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$srcDir = Join-Path $proj "app\src\main\java"
$d8 = Join-Path $bt "d8.bat"
$javac = Join-Path $env:JAVA_HOME "bin\javac.exe"

$Rjava = Get-ChildItem (Join-Path $work "obj") -Filter "R.java" -Recurse | Select-Object -First 1

foreach ($rel in @("11","17","21")) {
    $obj = Join-Path $work "obj_$rel"
    if (Test-Path $obj) { Remove-Item $obj -Recurse -Force }
    New-Item -ItemType Directory -Path $obj -Force | Out-Null
    $javaFiles = (Get-ChildItem $srcDir -Filter "*.java" -Recurse | ForEach-Object { $_.FullName }) + @($Rjava.FullName)
    & $javac --release $rel -classpath $androidJar -d $obj $javaFiles 2>&1 | Out-Null
    $cls = Get-ChildItem $obj -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
    $dex = Join-Path $work "dex_$rel"
    if (Test-Path $dex) { Remove-Item $dex -Recurse -Force }
    New-Item -ItemType Directory -Path $dex -Force | Out-Null
    & $d8 --lib $androidJar --min-api 24 --output $dex $cls 2>&1 | Out-Null
    $ok = Test-Path (Join-Path $dex "classes.dex")
    $ver = [System.IO.File]::ReadAllBytes((Join-Path $obj "com\cadmus\wifisentinel\Ap.class"))
    $major = [int]($ver[6] -shl 8) + $ver[7]
    Write-Output ("--release $rel : classfile major=$major  d8 dex=" + $ok)
}
