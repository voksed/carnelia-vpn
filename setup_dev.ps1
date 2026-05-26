#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Устанавливает все зависимости для работы с null vpn исходником.
    Поддерживает: Android SDK, JDK, ADB, Android Studio (опционально)
.USAGE
    .\setup_dev.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = "SilentlyContinue"

function Write-Step([string]$msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-OK([string]$msg) { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn([string]$msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }
function Write-Info([string]$msg) { Write-Host "    ... $msg" -ForegroundColor Gray }

Write-Host "`n====== null vpn — установка окружения разработчика ======`n" -ForegroundColor Magenta

# ===== 1. Проверка Java / JDK =====
Write-Step "JDK (нужен JDK 17+)"
$javaVersion = java -version 2>&1 | Select-String "version"
if ($javaVersion) {
    Write-OK "Java найдена: $javaVersion"
} else {
    Write-Warn "JDK не найден. Устанавливаем через winget..."
    winget install --id Microsoft.OpenJDK.17 --accept-package-agreements --accept-source-agreements
}

# Проверяем JAVA_HOME
if (-not $env:JAVA_HOME) {
    # Ищем Android Studio JBR
    $asJbr = "C:\Program Files\Android\Android Studio\jbr"
    if (Test-Path $asJbr) {
        Write-OK "Android Studio JBR найден: $asJbr"
        Write-Warn "Рекомендуем: `$env:JAVA_HOME = '$asJbr'"
    }
}

# ===== 2. Android SDK / Command Line Tools =====
Write-Step "Android SDK Command Line Tools"
$sdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
$cmdlineTools = "$sdkRoot\cmdline-tools\latest\bin\sdkmanager.bat"

if (Test-Path $cmdlineTools) {
    Write-OK "sdkmanager найден: $cmdlineTools"
} else {
    Write-Warn "Android SDK не найден по $sdkRoot"
    Write-Info "Скачайте командные инструменты Android:"
    Write-Info "  https://developer.android.com/studio#command-tools"
    Write-Info "Или установите Android Studio:"
    Write-Info "  https://developer.android.com/studio"
    Write-Info ""
    Write-Info "После установки выполните:"
    Write-Info "  sdkmanager --licenses"
    Write-Info "  sdkmanager 'platform-tools' 'platforms;android-34' 'build-tools;34.0.0'"
}

# ===== 3. ADB =====
Write-Step "ADB (Android Debug Bridge)"
$adbPaths = @(
    "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk\platform-tools\adb.exe",
    "C:\Program Files\Android\Android Studio\platform-tools\adb.exe"
)
$adbFound = $false
foreach ($p in $adbPaths) {
    if (Test-Path $p) {
        Write-OK "ADB найден: $p"
        $adbFound = $true
        break
    }
}
if (-not $adbFound) {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCmd) {
        Write-OK "ADB в PATH: $($adbCmd.Source)"
        $adbFound = $true
    }
}
if (-not $adbFound) {
    Write-Warn "ADB не найден. Установите platform-tools через sdkmanager или Android Studio."
}

# ===== 4. Проверка Gradle Wrapper =====
Write-Step "Gradle Wrapper"
$gradlew = "D:\carneliavpn\carnelia-vpn-fork\android\gradlew.bat"
if (Test-Path $gradlew) {
    Write-OK "gradlew.bat найден"
} else {
    Write-Warn "gradlew.bat не найден по $gradlew"
}

# ===== 5. Принятие лицензий SDK =====
Write-Step "Лицензии Android SDK"
if (Test-Path $cmdlineTools) {
    Write-Info "Принимаем лицензии..."
    echo "y`ny`ny`ny`ny`ny`ny`ny" | & $cmdlineTools --licenses 2>&1 | Select-String "accepted|declined|Review" | ForEach-Object { Write-Info $_ }
    Write-OK "Лицензии обработаны"
} else {
    Write-Warn "sdkmanager недоступен, лицензии не приняты. Сборка может завершиться ошибкой."
}

# ===== 6. Проверка local.properties =====
Write-Step "local.properties"
$localProps = "D:\carneliavpn\carnelia-vpn-fork\android\local.properties"
if (Test-Path $localProps) {
    $content = Get-Content $localProps -Raw
    if ($content -match "sdk\.dir") {
        Write-OK "local.properties настроен"
    } else {
        Write-Warn "sdk.dir не задан в local.properties"
        Add-Content $localProps "`nsdk.dir=$($sdkRoot -replace '\\', '\\\\')"
        Write-OK "sdk.dir добавлен"
    }
} else {
    $sdkEscaped = $sdkRoot -replace '\\', '\\\\'
    "sdk.dir=$sdkEscaped" | Set-Content $localProps
    Write-OK "local.properties создан"
}

# ===== 7. Gradle кэш и предзагрузка =====
Write-Step "Предзагрузка Gradle зависимостей"
Push-Location "D:\carneliavpn\carnelia-vpn-fork\android"
Write-Info "Запускаем gradle dependencies (может занять 5-15 минут)..."
& cmd.exe /c "gradlew.bat dependencies --no-daemon -q 2>&1" | Tail -n 20
Write-OK "Зависимости загружены в кэш"
Pop-Location

# ===== Итог =====
Write-Host "`n====== Итог ======" -ForegroundColor Magenta
Write-Host @"
  Для сборки APK запустите:
    .\build_and_deploy.ps1

  Для сборки без установки:
    .\build_and_deploy.ps1 -BuildOnly

  APK после сборки окажется в:
    D:\carneliavpn\null-vpn.apk
    D:\carneliavpn\carnelia-vpn-fork\android\app\build\outputs\apk\vanilla\release\

  Редактор: Android Studio (https://developer.android.com/studio)
  Исходники: D:\carneliavpn\clean-src\
"@
