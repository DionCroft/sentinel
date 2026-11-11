$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"
$d8 = Join-Path $bt "d8.bat"

function TryD8($label, $files) {
    $dex = Join-Path $work "test_$label"
    if (Test-Path $dex) { Remove-Item $dex -Recurse -Force }
    New-Item -ItemType Directory -Path $dex -Force | Out-Null
    & $d8 --lib $androidJar --min-api 24 --output $dex $files 2>&1 | Out-Null
    $ok = Test-Path (Join-Path $dex "classes.dex")
    Write-Output ("$label : " + $files.Count + " files -> dex=" + $ok)
    return $ok
}

$all = Get-ChildItem $obj -Filter "*.class" -Recurse | ForEach-Object { $_.FullName }
$noInner = $all | Where-Object { $_ -notmatch '\$' }
$noMainInner = $all | Where-Object { $_ -notmatch 'MainActivity\$' }

Write-Output "=== Test 1: all classes ==="
TryD8 "all" $all
Write-Output "=== Test 2: no anonymous inner classes ==="
TryD8 "noInner" $noInner
Write-Output "=== Test 3: no MainActivity inner ==="
TryD8 "noMainInner" $noMainInner

# Test 4: just MainActivity and its inners
$mainFiles = $all | Where-Object { $_ -match 'MainActivity' }
Write-Output "=== Test 4: MainActivity + inners ==="
TryD8 "mainOnly" $mainFiles

# Test 5: just one simple class
$ap = Join-Path $obj "com\cadmus\wifisentinel\Ap.class"
Write-Output "=== Test 5: Ap.class only ==="
TryD8 "ap" @($ap)
