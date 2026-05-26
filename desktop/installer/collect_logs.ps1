$outDir = Join-Path $PSScriptRoot "logs"
New-Item -ItemType Directory -Path $outDir -Force | Out-Null

$buildXray = Join-Path $PSScriptRoot "..\build\xray.exe"
if (Test-Path $buildXray) {
    Write-Host "Saving xray version output"
    & $buildXray -version 2>&1 | Out-File (Join-Path $outDir "xray_version.txt") -Encoding UTF8
} else {
    Write-Warning "xray binary not found at $buildXray"
}

Write-Host "Starting CarneliaVPN (GUI) in background"
Start-Process 'D:\Programs\CarneliaVPN\CarneliaVPN.exe'
Start-Sleep -Seconds 2

Write-Host "Searching for xray config/log in AppData..."
$candidates = @(
    $env:APPDATA,
    $env:LOCALAPPDATA
)
$found = $false
foreach ($root in $candidates) {
    try {
        $cfg = Get-ChildItem -Path $root -Recurse -Filter xray_config.json -ErrorAction SilentlyContinue -Force -Depth 6 | Select-Object -First 1
        if ($cfg) {
            Write-Host "Found config: $($cfg.FullName)"
            Copy-Item -Path $cfg.FullName -Destination (Join-Path $outDir "xray_config.json") -Force
            $found = $true
        }
        $log = Get-ChildItem -Path $root -Recurse -Filter xray.log -ErrorAction SilentlyContinue -Force -Depth 6 | Select-Object -First 1
        if ($log) {
            Write-Host "Found log: $($log.FullName)"
            Copy-Item -Path $log.FullName -Destination (Join-Path $outDir "xray.log") -Force
            $found = $true
        }
    } catch {
        # ignore
    }
}

if (-not $found) { Write-Host "No xray config/log found in AppData. Start the app and click Connect to generate logs." }
else { Write-Host "Logs saved to $outDir" }
