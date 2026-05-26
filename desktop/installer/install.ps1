param(
    [string]$BuildDir = "..\\build",
    [string]$InstallDir = "$env:LOCALAPPDATA\\Programs\\CarneliaVPN"
)

Write-Host "Installing CarneliaVPN from $BuildDir to $InstallDir"

$src = Join-Path $PSScriptRoot $BuildDir
$dst = $InstallDir

if (-Not (Test-Path $src)) {
    Write-Error "Build directory not found: $src"
    exit 1
}

New-Item -ItemType Directory -Path $dst -Force | Out-Null

Write-Host "Copying files..."
Copy-Item -Path (Join-Path $src '*') -Destination $dst -Recurse -Force

# Create Start Menu shortcut
$shell = New-Object -ComObject WScript.Shell
$startMenuDir = Join-Path $env:APPDATA "Microsoft\Windows\Start Menu\Programs\CarneliaVPN"
New-Item -ItemType Directory -Path $startMenuDir -Force | Out-Null
$shortcutPath = Join-Path $startMenuDir "CarneliaVPN.lnk"
$target = Join-Path $dst "CarneliaVPN.exe"

if (Test-Path $target) {
    $lnk = $shell.CreateShortcut($shortcutPath)
    $lnk.TargetPath = $target
    $lnk.WorkingDirectory = $dst
    $lnk.Save()
    Write-Host "Shortcut created: $shortcutPath"
} else {
    Write-Warning "Executable not found at $target; shortcut not created."
}

# Create uninstall script
$uninstallPath = Join-Path $dst "uninstall.ps1"
@"
Write-Host 'Uninstalling CarneliaVPN...'
Remove-Item -Path '$dst' -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path '$shortcutPath' -Force -ErrorAction SilentlyContinue
Write-Host 'Uninstalled.'
"@ | Out-File -FilePath $uninstallPath -Encoding UTF8

Write-Host "Installation complete. Installed to: $dst"
Write-Host "Run '$uninstallPath' to remove the installation."
