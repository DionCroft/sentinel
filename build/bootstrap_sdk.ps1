# bootstrap_sdk.ps1
# Downloads the Android SDK command-line tools and installs the minimal
# build-tools + platform needed to compile the APK. Idempotent.
param(
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$BuildTools = "35.0.0",
    [string]$Platform = "android-34"
)

$ErrorActionPreference = "Stop"
$toolsDir = Join-Path $SdkRoot "cmdline-tools"
$latestDir = Join-Path $toolsDir "latest"
$binSdk = Join-Path $latestDir "bin"

if (-not (Test-Path $SdkRoot)) {
    New-Item -ItemType Directory -Path $SdkRoot -Force | Out-Null
}

# 1. Download command-line tools if not already present.
$zipPath = Join-Path $env:TEMP "cmdtools.zip"
if (-not (Test-Path (Join-Path $latestDir "bin"))) {
    Write-Host "Downloading Android command-line tools..."
    $url = "https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip"
    Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing
    Write-Host "Extracting..."
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    # The zip extracts to "cmdline-tools\latest" structure already.
    if (-not (Test-Path (Join-Path $latestDir "bin"))) {
        # If it extracted with a top-level folder, move it.
        $extracted = Get-ChildItem $toolsDir -Directory | Select-Object -First 1
        if ($extracted) {
            Move-Item (Join-Path $extracted.FullName "latest" -ErrorAction SilentlyContinue) $latestDir -Force -ErrorAction SilentlyContinue
        }
    }
    Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
}

# 2. Accept licenses.
$yesPath = Join-Path $env:TEMP "sdk_licenses_yes.txt"
Set-Content -Path $yesPath -Value "y`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`ny`n" -Encoding ASCII

$yesStream = [System.IO.File]::OpenRead($yesPath)
$sdkManager = Join-Path $binSdk "sdkmanager.bat"

Write-Host "Installing build-tools and platform (this may take a few minutes)..."
$proc = Start-Process -FilePath $sdkManager -ArgumentList "--sdk_root=$SdkRoot", "``\"$BuildTools``\"", "``\"$Platform``\"" -NoNewWindow -PassThru -RedirectStandardInput $yesPath

# Wait for completion
$proc.WaitForExit()
Write-Host "sdkmanager exit code: $($proc.ExitCode)"

Write-Host "Done. SDK root: $SdkRoot"
