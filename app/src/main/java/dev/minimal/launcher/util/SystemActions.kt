package dev.minimal.launcher.util

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import dev.minimal.launcher.service.LauncherAccessibilityService

object SystemActions {

    fun expandNotifications(context: Context) {
        if (LauncherAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)) return
        invokeStatusBar(context, "expandNotificationsPanel")
    }

    fun expandQuickSettings(context: Context) {
        if (LauncherAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)) return
        invokeStatusBar(context, "expandSettingsPanel")
    }

    fun lockScreen(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            LauncherAccessibilityService.perform(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        ) return
        Toast.makeText(
            context,
            "Zum Sperren per Doppeltipp bitte „Minimal Launcher – Gesten“ in den Bedienungshilfen aktivieren",
            Toast.LENGTH_LONG,
        ).show()
        start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    @SuppressLint("WrongConstant", "PrivateApi")
    private fun invokeStatusBar(context: Context, method: String) {
        try {
            val service = context.getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod(method).invoke(service)
        } catch (_: Exception) {
        }
    }

    fun webSearch(context: Context, query: String) {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (!start(context, intent)) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
        }
    }

    fun storeSearch(context: Context, query: String) {
        if (!start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query))))) {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(query))))
        }
    }

    fun openClock(context: Context) = start(context, Intent(AlarmClock.ACTION_SHOW_ALARMS))

    fun openCalendar(context: Context) = start(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/" + System.currentTimeMillis())),
    )

    fun nextAlarm(context: Context): Long? =
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime

    fun chooseWallpaper(context: Context) =
        start(context, Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Hintergrundbild wählen"))

    fun openHomeSettings(context: Context) = start(context, Intent(Settings.ACTION_HOME_SETTINGS))

    fun openNotificationAccess(context: Context) =
        start(context, Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))

    fun openAccessibility(context: Context) = start(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun uninstall(context: Context, packageName: String) =
        start(context, Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")))

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val info = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return info?.activityInfo?.packageName == context.packageName
    }

    fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
