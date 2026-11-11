Write-Output "=== JAVA_HOME ==="
Write-Output $env:JAVA_HOME
Write-Output "=== java -version ==="
& (Join-Path $env:JAVA_HOME "bin\java.exe") -version 2>&1
Write-Output "=== C:\Program Files\Java ==="
Get-ChildItem "C:\Program Files\Java" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
Write-Output "=== Adoptium ==="
Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Name
Write-Output "=== JAVA_HOME javac -version ==="
& (Join-Path $env:JAVA_HOME "bin\javac.exe") -version 2>&1
