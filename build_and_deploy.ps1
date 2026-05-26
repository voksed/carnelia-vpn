#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Собирает null vpn APK и устанавливает на телефон через ADB.
.DESCRIPTION
    1. Запускает Gradle assembleVanillaRelease
    2. Ищет собранный APK
    3. Устанавливает через ADB если телефон подключён
.USAGE
    .\build_and_deploy.ps1                # Собрать и установить
    .\build_and_deploy.ps1 -BuildOnly     # Только собрать
    .\build_and_deploy.ps1 -DeployOnly    # Только установить уже собранный APK
#>

param(
    [switch]$BuildOnly,
    [switch]$DeployOnly,
    [string]$Device = ""  # Serial устройства ADB, пусто = первое найденное
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ===== Пути =====
$ROOT     = "D:\carneliavpn\carnelia-vpn-fork\android"
$GRADLE   = "$ROOT\gradlew.bat"
$APK_DIR  = "$ROOT\app\build\outputs\apk\vanilla\release"
$ADB      = "C:\Users\aslan\AppData\Local\Android\Sdk\platform-tools\adb.exe"

function Write-Step([string]$msg) {
    Write-Host "`n==> $msg" -ForegroundColor Cyan
}

function Write-OK([string]$msg) {
    Write-Host "    OK: $msg" -ForegroundColor Green
}

function Write-Fail([string]$msg) {
    Write-Host "    FAIL: $msg" -ForegroundColor Red
    exit 1
}

# ===== Проверки =====
if (-not (Test-Path $GRADLE)) { Write-Fail "gradlew.bat не найден: $GRADLE" }
if (-not (Test-Path $ADB) -and -not $BuildOnly) {
    Write-Host "    WARN: ADB не найден по пути $ADB" -ForegroundColor Yellow
    Write-Host "    Поиск ADB в PATH..."
    $ADB = (Get-Command adb -ErrorAction SilentlyContinue)?.Source
    if (-not $ADB) { Write-Fail "ADB не найден" }
}

# ===== СБОРКА =====
if (-not $DeployOnly) {
    Write-Step "Сборка APK (assembleVanillaRelease)"
    $t = [System.Diagnostics.Stopwatch]::StartNew()

    Push-Location $ROOT
    try {
        & cmd.exe /c "gradlew.bat assembleVanillaRelease --no-daemon 2>&1"
        if ($LASTEXITCODE -ne 0) { Write-Fail "Gradle завершился с ошибкой (код $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }

    $t.Stop()
    Write-OK "Сборка завершена за $([int]$t.Elapsed.TotalSeconds) сек"
}

# ===== Поиск APK =====
$apkFile = Get-ChildItem -Path $APK_DIR -Filter "*.apk" -ErrorAction SilentlyContinue |
           Where-Object { $_.Name -notlike "*unsigned*" } |
           Sort-Object LastWriteTime -Descending |
           Select-Object -First 1

if (-not $apkFile) {
    # Расширенный поиск по всему дереву outputs
    $apkFile = Get-ChildItem -Recurse -Path "$ROOT\app\build\outputs\apk" -Filter "*release*.apk" -ErrorAction SilentlyContinue |
               Where-Object { $_.Name -notlike "*unsigned*" } |
               Sort-Object LastWriteTime -Descending |
               Select-Object -First 1
}

if (-not $apkFile) { Write-Fail "APK не найден в $APK_DIR" }

Write-OK "APK найден: $($apkFile.FullName)"
Write-Host "    Размер: $([math]::Round($apkFile.Length / 1MB, 1)) МБ"

# Копируем APK в корень workspace для удобства
$dst = "D:\carneliavpn\null-vpn.apk"
Copy-Item $apkFile.FullName $dst -Force
Write-OK "Скопирован в: $dst"

# ===== ADB УСТАНОВКА =====
if ($BuildOnly) {
    Write-Host "`nFin: только сборка, ADB пропущен." -ForegroundColor Yellow
    exit 0
}

Write-Step "ADB: Поиск устройств"

$devices = & $ADB devices 2>&1 | Where-Object { $_ -match "\tdevice$" }
if (-not $devices) {
    Write-Host "`n  Телефон не подключён или ADB не активирован." -ForegroundColor Yellow
    Write-Host "  APK доступен по адресу: $dst"
    exit 0
}

# Выбираем устройство
if ($Device) {
    $target = $Device
} else {
    $target = ($devices -split "\t")[0]
}

Write-OK "Устройство: $target"

Write-Step "ADB: Установка APK"
& $ADB -s $target install -r $apkFile.FullName
if ($LASTEXITCODE -ne 0) { Write-Fail "Установка не удалась" }

Write-OK "null vpn установлен на $target"

Write-Step "ADB: Запуск приложения"
& $ADB -s $target shell am start -n "com.carnelia.vpn/.MainActivity"
Write-OK "Приложение запущено"
