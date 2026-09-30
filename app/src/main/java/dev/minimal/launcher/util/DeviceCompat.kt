package dev.minimal.launcher.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.service.NotificationListener
import java.util.Locale

/**
 * Hilfen für Hersteller, die Hintergrunddienste aggressiv beenden
 * (dann verschwinden Benachrichtigungspunkte und Mediensteuerung).
 */
object DeviceCompat {
    private val manufacturer = Build.MANUFACTURER.lowercase(Locale.ROOT)
    private val brand = Build.BRAND.lowercase(Locale.ROOT)

    /** Anzeigename des Herstellers, falls er für aggressives Energiesparen bekannt ist. */
    val aggressiveVendor: String? = when {
        listOf("xiaomi", "redmi", "poco").any { it in manufacturer || it in brand } -> "Xiaomi"
        listOf("oppo", "realme").any { it in manufacturer || it in brand } -> "Oppo/Realme"
        "oneplus" in manufacturer -> "OnePlus"
        listOf("vivo", "iqoo").any { it in manufacturer || it in brand } -> "Vivo"
        listOf("huawei", "honor").any { it in manufacturer || it in brand } -> "Huawei/Honor"
        "samsung" in manufacturer -> "Samsung"
        "asus" in manufacturer -> "Asus"
        "meizu" in manufacturer -> "Meizu"
        else -> null
    }

    /** Bekannte Autostart-/Hintergrund-Seiten der Hersteller (werden der Reihe nach probiert). */
    private val autostartComponents = listOf(
        "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
        "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
        "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
        "com.oneplus.security" to "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
        "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
        "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
        "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
        "com.samsung.android.lool" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        "com.samsung.android.sm" to "com.samsung.android.sm.battery.ui.BatteryActivity",
        "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
        "com.meizu.safe" to "com.meizu.safe.permission.SmartBGActivity",
    )

    fun isIgnoringBatteryOptimizations(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true

    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
        if (!SystemActions.start(context, direct)) {
            SystemActions.start(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    /** Öffnet die Autostart-Seite des Herstellers; notfalls die App-Info. */
    fun openAutostart(context: Context) {
        for ((pkg, cls) in autostartComponents) {
            val intent = Intent().setComponent(ComponentName(pkg, cls))
            val ok = try {
                SystemActions.start(context, intent)
            } catch (e: Exception) {
                false
            }
            if (ok) return
        }
        SystemActions.openAppDetails(context)
    }

    /** Benachrichtigungszugriff erlaubt, aber der Dienst läuft nicht (vom System beendet)? */
    fun isListenerDisconnected(context: Context): Boolean =
        NotificationStore.hasAccess(context) && NotificationStore.service == null

    /** Bittet das System, den Benachrichtigungsdienst neu zu verbinden. */
    fun rebindNotificationListener(context: Context) {
        if (!isListenerDisconnected(context)) return
        try {
            NotificationListenerService.requestRebind(ComponentName(context, NotificationListener::class.java))
        } catch (_: Exception) {
        }
    }
}
