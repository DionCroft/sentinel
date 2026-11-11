$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"
$proj = "C:\Users\viper\Projects\sentinel"
$work = Join-Path $proj "build\work"
$obj = Join-Path $work "obj"
$d8 = Join-Path $bt "d8.bat"

$topLevel = @("Ap","BaseSentinelActivity","HistoryRecord","MainActivity","RadarView","RiskEngine","Scanner","Store","SurveyView","Triangulation","WifiMath","R")
foreach ($t in $topLevel) {
    $dex = Join-Path $work "t_$t"
    if (Test-Path $dex) { Remove-Item $dex -Recurse -Force }
    New-Item -ItemType Directory -Path $dex -Force | Out-Null
    # Include the class + its inner classes
    $files = Get-ChildItem $obj -Filter "*.class" -Recurse | Where-Object { $_.BaseName -eq $t -or $_.BaseName -like "$t`$" } | ForEach-Object { $_.FullName }
    & $d8 --lib $androidJar --min-api 24 --output $dex $files 2>&1 | Out-Null
    $ok = Test-Path (Join-Path $dex "classes.dex")
    Write-Output ("$t ($($files.Count) files) -> dex=" + $ok)
}
