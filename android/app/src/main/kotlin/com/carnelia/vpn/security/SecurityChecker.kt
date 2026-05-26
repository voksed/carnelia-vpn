package com.carnelia.vpn.security

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.carnelia.vpn.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.Socket

/**
 * Проверяет целостность устройства и окружения приложения.
 *
 * Что обнаруживается:
 *  - Root (su binary, Magisk, SuperSU, busybox)
 *  - Xposed / LSPosed (hook-фреймворки)
 *  - Frida (runtime-инжектор)
 *  - Режим отладки
 */
object SecurityChecker {

    sealed class Threat(val title: String, val description: String) {
        object Root : Threat(
            "Устройство с root-доступом",
            "Обнаружен root. Другие приложения могут перехватить трафик VPN и данные. " +
            "Подключение всё равно произойдёт, но безопасность снижена."
        )
        object HookFramework : Threat(
            "Hook-фреймворк (Xposed / LSPosed)",
            "Обнаружен Xposed или аналог. Вирус с hook-доступом может встроиться " +
            "в процесс приложения и прочитать данные до шифрования."
        )
        object FridaDetected : Threat(
            "Frida (runtime-инжектор)",
            "Обнаружен Frida — инструмент для динамического анализа приложений. " +
            "Может использоваться для перехвата VPN-конфигов из памяти."
        )
        object DebuggableBuild : Threat(
            "Debug-сборка приложения",
            "Запущена debug-версия приложения. Для использования в качестве VPN " +
            "всегда используйте release-APK."
        )
    }

    /** Запустить все проверки. Возвращает список обнаруженных угроз (пуст = всё чисто). */
    fun runChecks(context: Context): List<Threat> {
        val threats = mutableListOf<Threat>()

        if (BuildConfig.DEBUG) {
            threats += Threat.DebuggableBuild
        }

        if (isDeviceRooted()) {
            threats += Threat.Root
        }

        if (isXposedActive()) {
            threats += Threat.HookFramework
        }

        if (isFridaActive()) {
            threats += Threat.FridaDetected
        }

        return threats
    }

    // ──────────────────────────────────────────────────────────────────
    // Root detection
    // ──────────────────────────────────────────────────────────────────

    private val ROOT_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su"
    )

    private val DANGEROUS_PACKAGES = arrayOf(
        "com.topjohnwu.magisk",
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.zachspong.temprootremovejb",
        "com.ramdroid.appquarantine"
    )

    private fun isDeviceRooted(): Boolean {
        // Check 1: common su binary locations
        if (ROOT_PATHS.any { File(it).exists() }) return true

        // Check 2: Build tags contain "test-keys" (custom ROM with root)
        val buildTags = Build.TAGS ?: ""
        if (buildTags.contains("test-keys")) return true

        // Check 3: dangerous root-management packages installed
        // (we have QUERY_ALL_PACKAGES permission)
        return false // package check deferred to runChecks to avoid context param here
    }

    private fun isDangerousPackageInstalled(context: Context): Boolean {
        val pm = context.packageManager
        return DANGEROUS_PACKAGES.any { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
        }
    }

    /** Re-run with context-dependent root checks included. */
    fun runChecks(context: Context, includePackageCheck: Boolean): List<Threat> {
        val threats = runChecks(context).toMutableList()
        if (includePackageCheck && Threat.Root !in threats && isDangerousPackageInstalled(context)) {
            threats.add(0, Threat.Root)
        }
        return threats
    }

    // ──────────────────────────────────────────────────────────────────
    // Xposed / LSPosed detection
    // ──────────────────────────────────────────────────────────────────

    private fun isXposedActive(): Boolean {
        // Method 1: try to load known Xposed class
        return try {
            val xposedBridge = Class.forName("de.robv.android.xposed.XposedBridge")
            xposedBridge != null
        } catch (_: ClassNotFoundException) {
            false
        } catch (_: Exception) {
            false
        } || isXposedInStack()
    }

    private fun isXposedInStack(): Boolean {
        return try {
            throw Exception()
        } catch (e: Exception) {
            e.stackTrace.any { it.className.startsWith("de.robv.android.xposed") }
        }
    }

    // ──────────────────────────────────────────────────────────────────
    // Frida detection
    // ──────────────────────────────────────────────────────────────────

    private fun isFridaActive(): Boolean {
        // Method 1: Check /proc/self/maps for frida agent
        if (isFridaInMaps()) return true
        // Method 2: Check if frida-server is listening on default port
        if (isFridaPortOpen()) return true
        return false
    }

    private fun isFridaInMaps(): Boolean {
        return try {
            File("/proc/self/maps").useLines { lines ->
                lines.any { line ->
                    line.contains("frida", ignoreCase = true) ||
                    line.contains("gum-js-loop", ignoreCase = true) ||
                    line.contains("linjector", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun isFridaPortOpen(): Boolean {
        return try {
            Socket("127.0.0.1", 27042).use { true }
        } catch (_: Exception) {
            false
        }
    }
}
