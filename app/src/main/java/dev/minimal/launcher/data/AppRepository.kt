package dev.minimal.launcher.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshJob: Job? = null

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch { _apps.value = load() }
    }

    private fun load(): List<AppInfo> {
        val me = Process.myUserHandle()
        return userManager.userProfiles.flatMap { user ->
            launcherApps.getActivityList(null, user).mapNotNull { info ->
                if (info.componentName.packageName == context.packageName) return@mapNotNull null
                val label = info.label?.toString()?.trim().orEmpty().ifEmpty { info.componentName.packageName }
                AppInfo(
                    key = AppInfo.key(info.componentName, user),
                    label = label,
                    originalLabel = label,
                    packageName = info.componentName.packageName,
                    component = info.componentName,
                    user = user,
                    isWork = user != me,
                    info = info,
                )
            }
        }
    }

    fun launch(app: AppInfo, bounds: Rect? = null) {
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

    fun shortcutIcon(shortcut: ShortcutInfo): Drawable? = try {
        launcherApps.getShortcutIconDrawable(shortcut, context.resources.displayMetrics.densityDpi)
    } catch (e: Exception) {
        null
    }

    fun startShortcut(shortcut: ShortcutInfo) {
        try {
            launcherApps.startShortcut(shortcut, null, null)
        } catch (e: Exception) {
            Toast.makeText(context, "Verknüpfung konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
        }
    }
}
