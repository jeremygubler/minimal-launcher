package dev.minimal.launcher

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Process
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.minimal.launcher.data.AppInfo
import dev.minimal.launcher.data.Favorite
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.NotificationPreview
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.data.PrivateSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.UUID

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.launcherApp
    private val store = app.settings
    private val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }

    val settings: StateFlow<LauncherSettings> = store.state

    /** Alle Apps inkl. ausgeblendeter, mit eigenen Namen, alphabetisch sortiert. */
    val allApps: StateFlow<List<AppInfo>> = combine(app.apps.apps, store.state) { apps, s ->
        apps.map { a -> s.renamed[a.key]?.let { a.copy(label = it) } ?: a }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val visibleApps: StateFlow<List<AppInfo>> = combine(allApps, store.state) { apps, s ->
        apps.filter { it.key !in s.hidden && !it.isPrivate }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Apps im vertraulichen Profil – nur vorhanden, solange es entsperrt ist. */
    val privateApps: StateFlow<List<AppInfo>> = allApps.map { apps -> apps.filter { it.isPrivate } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val privateSpace: StateFlow<PrivateSpace?> = app.apps.privateSpace

    fun setPrivateSpaceLocked(locked: Boolean) = app.apps.setPrivateSpaceLocked(locked)
    fun openPrivateSpaceSettings() = app.apps.openPrivateSpaceSettings()
    fun diagnostics(): String = app.apps.diagnostics()

    val notifications: StateFlow<Map<String, List<NotificationPreview>>> =
        NotificationStore.items.map { list -> list.groupBy { it.appKey } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val _homePressed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val homePressed: SharedFlow<Unit> = _homePressed

    init {
        viewModelScope.launch {
            store.state.map { it.iconPack to it.themedIcons }.distinctUntilChanged().collect { (pack, themed) ->
                withContext(Dispatchers.IO) { app.icons.configure(pack, themed) }
            }
        }
        viewModelScope.launch {
            if (store.value.firstRunDone) return@launch
            val apps = app.apps.apps.first { it.isNotEmpty() }
            val favorites = defaultFavorites(apps)
            store.update { it.copy(firstRunDone = true, favorites = it.favorites.ifEmpty { favorites }) }
        }
    }

    private fun defaultFavorites(apps: List<AppInfo>): List<Favorite> {
        val pm = getApplication<Application>().packageManager
        val me = Process.myUserHandle()
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")),
        )
        return intents.mapNotNull { intent ->
            val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
                ?: return@mapNotNull null
            apps.firstOrNull { it.packageName == pkg && it.user == me }
        }.distinctBy { it.key }.map { Favorite(id = UUID.randomUUID().toString(), apps = listOf(it.key)) }
    }

    fun onHomePressed() {
        _homePressed.tryEmit(Unit)
    }

    val usage: StateFlow<Map<String, Double>> = app.usage.scores

    fun launch(appInfo: AppInfo) {
        app.usage.record(appInfo.key)
        app.apps.launch(appInfo)
    }

    fun clearUsage() = app.usage.clear()

    suspend fun allShortcuts(): List<ShortcutInfo> = withContext(Dispatchers.IO) { app.apps.allShortcuts() }
    fun openAppInfo(appInfo: AppInfo) = app.apps.openAppInfo(appInfo)

    suspend fun shortcuts(appInfo: AppInfo): List<ShortcutInfo> =
        withContext(Dispatchers.IO) { app.apps.shortcuts(appInfo) }

    fun shortcutIcon(shortcut: ShortcutInfo) = app.apps.shortcutIcon(shortcut)
    fun startShortcut(shortcut: ShortcutInfo) = app.apps.startShortcut(shortcut)

    fun update(block: (LauncherSettings) -> LauncherSettings) = store.update(block)

    // --- Favoriten ---------------------------------------------------------

    fun toggleFavorite(appInfo: AppInfo) = store.update { s ->
        if (s.isFavorite(appInfo.key)) {
            s.copy(favorites = s.favorites.filterNot { !it.isFolder && it.apps.firstOrNull() == appInfo.key })
        } else {
            s.copy(favorites = s.favorites + Favorite(UUID.randomUUID().toString(), listOf(appInfo.key)))
        }
    }

    fun setSwipeApp(appKey: String, swipeKey: String?) = store.update { s ->
        s.copy(favorites = s.favorites.map {
            if (!it.isFolder && it.apps.firstOrNull() == appKey) it.copy(swipeApp = swipeKey) else it
        })
    }

    fun createFolder(name: String, appKeys: List<String>) = store.update { s ->
        s.copy(favorites = s.favorites + Favorite(UUID.randomUUID().toString(), appKeys, name = name))
    }

    fun addToFolder(folderId: String, appKey: String) = store.update { s ->
        s.copy(favorites = s.favorites.map {
            if (it.id == folderId && appKey !in it.apps) it.copy(apps = it.apps + appKey) else it
        })
    }

    fun removeFromFolder(folderId: String, appKey: String) = store.update { s ->
        s.copy(favorites = s.favorites.map { if (it.id == folderId) it.copy(apps = it.apps - appKey) else it })
    }

    fun renameFolder(folderId: String, name: String) = store.update { s ->
        s.copy(favorites = s.favorites.map { if (it.id == folderId) it.copy(name = name) else it })
    }

    fun removeFavorite(id: String) = store.update { s -> s.copy(favorites = s.favorites.filterNot { it.id == id }) }

    fun moveFavorite(id: String, delta: Int) = store.update { s ->
        val list = s.favorites.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) return@update s
        list.add(to, list.removeAt(from))
        s.copy(favorites = list)
    }

    fun setFavoriteOrder(ids: List<String>) = store.update { s ->
        val byId = s.favorites.associateBy { it.id }
        val ordered = ids.mapNotNull { byId[it] }
        s.copy(favorites = ordered + s.favorites.filter { it.id !in ids })
    }

    // --- Apps --------------------------------------------------------------

    fun hide(appInfo: AppInfo) = store.update { s ->
        s.copy(
            hidden = s.hidden + appInfo.key,
            favorites = s.favorites.mapNotNull { f ->
                val apps = f.apps - appInfo.key
                if (apps.isEmpty() && !f.isFolder) null else f.copy(apps = apps)
            },
        )
    }

    fun unhide(key: String) = store.update { it.copy(hidden = it.hidden - key) }

    fun rename(appInfo: AppInfo, name: String?) = store.update { s ->
        val clean = name?.trim().orEmpty()
        if (clean.isEmpty() || clean == appInfo.originalLabel) {
            s.copy(renamed = s.renamed - appInfo.key)
        } else {
            s.copy(renamed = s.renamed + (appInfo.key to clean))
        }
    }

    // --- Widgets -----------------------------------------------------------

    fun addWidget(id: Int) = store.update { it.copy(widgets = it.widgets + id) }
    fun removeWidget(id: Int) = store.update { it.copy(widgets = it.widgets - id) }

    fun moveWidget(id: Int, delta: Int) = store.update { s ->
        val list = s.widgets.toMutableList()
        val from = list.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in list.indices) return@update s
        list.add(to, list.removeAt(from))
        s.copy(widgets = list)
    }
}
