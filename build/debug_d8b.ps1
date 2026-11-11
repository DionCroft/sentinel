$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = (Join-Path $sdkRoot "platforms\android-34\android.jar").Replace("\","/")
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"
$dexDir = (Join-Path $work "dex3").Replace("\","/")

Write-Output "=== class files (forward slash) ==="
$classFiles = Get-ChildItem $obj -Filter "*.class" -Recurse -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName.Replace("\","/") }
Write-Output ("count: " + $classFiles.Count)

$d8 = Join-Path $bt "d8.bat"
Write-Output "=== d8 with forward-slash paths ==="
& $d8 --lib $androidJar --min-api 24 --output $dexDir $classFiles 2>&1 | Select-Object -First 10
Write-Output ("d8 exit: " + $LASTEXITCODE)
Write-Output ("classes.dex: " + (Test-Path (Join-Path $work "dex3\classes.dex")))

Write-Output ""
Write-Output "=== d8 with directory input ==="
$dexDir2 = (Join-Path $work "dex4").Replace("\","/")
New-Item -ItemType Directory -Path $dexDir2 -Force | Out-Null
$objFwd = $obj.Replace("\","/")
& $d8 --lib $androidJar --min-api 24 --output $dexDir2 $objFwd 2>&1 | Select-Object -First 10
Write-Output ("d8 exit: " + $LASTEXITCODE)
Write-Output ("classes.dex: " + (Test-Path (Join-Path $work "dex4\classes.dex")))
