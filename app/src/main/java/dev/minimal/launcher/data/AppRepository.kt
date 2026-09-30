package dev.minimal.launcher.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import dev.minimal.launcher.util.DeviceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PrivateSpace(val user: UserHandle, val locked: Boolean)

class AppRepository(private val context: Context, private val icons: IconLoader) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    /** App-Namen vom letzten Start: Das Laden echter Namen ist der langsame Teil beim Start. */
    private val labelCache = context.getSharedPreferences("label_cache", Context.MODE_PRIVATE)

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    /** Vertrauliches Profil (Android 15+); null, wenn keins eingerichtet oder es ausgeblendet ist. */
    private val _privateSpace = MutableStateFlow<PrivateSpace?>(null)
    val privateSpace: StateFlow<PrivateSpace?> = _privateSpace.asStateFlow()

    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE)
            if (Build.VERSION.SDK_INT >= 35) {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
                addAction(Intent.ACTION_PROFILE_ADDED)
                addAction(Intent.ACTION_PROFILE_REMOVED)
            }
        }
        ContextCompat.registerReceiver(context, profileReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        refresh()
    }

    private fun changed(packageName: String?) {
        packageName?.let { icons.invalidatePackage(it) }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            val entries = activities()
            // 1. Sofort mit zwischengespeicherten Namen anzeigen.
            val cached = entries.map { (info, user) -> build(info, user, labelCache.getString(key(info, user), null)) }
            _apps.value = cached
            // 2. Echte Namen im Hintergrund laden und nur bei Änderungen neu anzeigen.
            val fresh = entries.map { (info, user) -> build(info, user, null) }
            if (fresh.map { it.label } != cached.map { it.label }) _apps.value = fresh
            labelCache.edit().clear().apply {
                fresh.forEach { putString(it.key, it.originalLabel) }
            }.apply()
        }
    }

    private fun profiles(): List<UserHandle> =
        if (Build.VERSION.SDK_INT >= 35) {
            runCatching { launcherApps.profiles }.getOrNull()?.takeIf { it.isNotEmpty() } ?: userManager.userProfiles
        } else {
            userManager.userProfiles
        }

    @Volatile
    private var privateUsers: Set<UserHandle> = emptySet()

    private fun isPrivate(user: UserHandle): Boolean = user in privateUsers

    private fun detectPrivate(user: UserHandle): Boolean =
        Build.VERSION.SDK_INT >= 35 && runCatching {
            launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
        }.getOrDefault(false)

    private fun activities(): List<Pair<LauncherActivityInfo, UserHandle>> {
        val profiles = profiles()
        val privateUser = profiles.firstOrNull { detectPrivate(it) }
        privateUsers = setOfNotNull(privateUser)
        _privateSpace.value = privateUser?.let { PrivateSpace(it, userManager.isQuietModeEnabled(it)) }
        return profiles
            // Gesperrtes vertrauliches Profil: Apps komplett ausblenden.
            .filterNot { it == privateUser && userManager.isQuietModeEnabled(it) }
            .flatMap { user ->
                launcherApps.getActivityList(null, user)
                    .filter { it.componentName.packageName != context.packageName }
                    .map { it to user }
            }
    }

    /** Sperrt/entsperrt das vertrauliche Profil. Beim Entsperren fragt das System nach PIN/Fingerabdruck. */
    fun setPrivateSpaceLocked(locked: Boolean) {
        val space = _privateSpace.value ?: return
        try {
            userManager.requestQuietModeEnabled(locked, space.user)
        } catch (e: Exception) {
            Toast.makeText(context, "Vertrauliches Profil konnte nicht geändert werden", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPrivateSpaceSettings() {
        // getPrivateSpaceSettingsIntent() ist nicht im öffentlichen SDK 35 enthalten, daher per Reflection.
        try {
            val sender = LauncherApps::class.java.getMethod("getPrivateSpaceSettingsIntent")
                .invoke(launcherApps) as? IntentSender
            if (sender != null) {
                context.startIntentSender(sender, null, Intent.FLAG_ACTIVITY_NEW_TASK, Intent.FLAG_ACTIVITY_NEW_TASK, 0)
                return
            }
        } catch (_: Exception) {
        }
        // Fallback: Auf dem Pixel liegt der private Bereich unter „Sicherheit & Datenschutz“.
        try {
            context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    /** Technische Infos für die Fehlersuche (Profile, Berechtigungen). */
    fun diagnostics(): String = buildString {
        appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
        )?.activityInfo?.packageName
        appendLine("Standard-Launcher: $home")
        if (Build.VERSION.SDK_INT >= 35) {
            val granted = ContextCompat.checkSelfPermission(context, "android.permission.ACCESS_HIDDEN_PROFILES") ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            appendLine("ACCESS_HIDDEN_PROFILES: ${if (granted) "erteilt" else "fehlt"}")
        }
        appendLine("Hersteller mit aggressivem Energiesparen: ${DeviceCompat.aggressiveVendor ?: "nein"}")
        appendLine("Akku-Optimierung ausgenommen: ${DeviceCompat.isIgnoringBatteryOptimizations(context)}")
        appendLine("Benachrichtigungszugriff: ${NotificationStore.hasAccess(context)}, Dienst verbunden: ${NotificationStore.service != null}")
        appendLine("Kurzbefehle erlaubt: ${runCatching { launcherApps.hasShortcutHostPermission() }.getOrDefault(false)}")
        appendLine("Profile (UserManager): ${runCatching { userManager.userProfiles.size }.getOrElse { "Fehler: ${it.message}" }}")
        val profiles = runCatching { profiles() }.getOrElse {
            appendLine("Profile (LauncherApps): Fehler ${it.javaClass.simpleName}: ${it.message}")
            emptyList()
        }
        profiles.forEach { user ->
            val type = if (Build.VERSION.SDK_INT >= 35) {
                runCatching { launcherApps.getLauncherUserInfo(user)?.userType }.getOrElse { "Fehler: ${it.message}" }
            } else {
                "-"
            }
            val quiet = runCatching { userManager.isQuietModeEnabled(user) }.getOrNull()
            val count = runCatching { launcherApps.getActivityList(null, user).size }.getOrNull()
            appendLine("• Profil $user: Typ=$type, gesperrt=$quiet, Apps=$count")
        }
        appendLine("Vertrauliches Profil erkannt: ${_privateSpace.value?.let { if (it.locked) "ja, gesperrt" else "ja, entsperrt" } ?: "nein"}")
    }

    private fun key(info: LauncherActivityInfo, user: UserHandle) = AppInfo.key(info.componentName, user)

    private fun build(info: LauncherActivityInfo, user: UserHandle, cachedLabel: String?): AppInfo {
        val label = cachedLabel
            ?: info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName }
        return AppInfo(
            key = key(info, user),
            label = label,
            originalLabel = label,
            packageName = info.componentName.packageName,
            component = info.componentName,
            user = user,
            isWork = user != Process.myUserHandle() && !isPrivate(user),
            isPrivate = isPrivate(user),
            info = info,
        )
    }

    fun launch(app: AppInfo, bounds: Rect? = null) {
        // Pausiertes Arbeitsprofil: statt Fehler das Profil fortsetzen (das System fragt nach).
        if (app.isWork && userManager.isQuietModeEnabled(app.user)) {
            try {
                userManager.requestQuietModeEnabled(false, app.user)
            } catch (_: Exception) {
            }
            return
        }
        try {
            launcherApps.startMainActivity(app.component, app.user, bounds, null)
        } catch (e: Exception) {
            Toast.makeText(context, "App konnte nicht gestartet werden", Toast.LENGTH_SHORT).show()
        }
    }

    fun openAppInfo(app: AppInfo) {
        try {
            launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
        } catch (_: Exception) {
        }
    }

    fun shortcuts(app: AppInfo): List<ShortcutInfo> = try {
        if (!launcherApps.hasShortcutHostPermission()) {
            emptyList()
        } else {
            val query = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setActivity(app.component)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            launcherApps.getShortcuts(query, app.user).orEmpty()
                .filter { it.isEnabled }
                .distinctBy { it.id }
                .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
                .take(6)
        }
    } catch (e: Exception) {
        emptyList()
    }

    /** Alle App-Shortcuts aller sichtbaren Profile (nur als Standard-Launcher verfügbar). */
    fun allShortcuts(): List<ShortcutInfo> = try {
        if (!launcherApps.hasShortcutHostPermission()) {
            emptyList()
        } else {
            val query = LauncherApps.ShortcutQuery().setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
            _apps.value.map { it.user }.distinct().flatMap { user ->
                runCatching { launcherApps.getShortcuts(query, user).orEmpty() }.getOrDefault(emptyList())
            }.filter { it.isEnabled }.distinctBy { Triple(it.`package`, it.id, it.userHandle) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = try {
        launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
    } catch (e: Exception) {
        null
    }

    fun startShortcutById(app: AppInfo, id: String) {
        try {
            launcherApps.startShortcut(app.packageName, id, null, null, app.user)
        } catch (e: Exception) {
            Toast.makeText(context, "Aktion nicht mehr verfügbar", Toast.LENGTH_SHORT).show()
        }
    }

    fun startShortcut(shortcut: ShortcutInfo) {
        try {
            launcherApps.startShortcut(shortcut, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Verknüpfung konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }
}
