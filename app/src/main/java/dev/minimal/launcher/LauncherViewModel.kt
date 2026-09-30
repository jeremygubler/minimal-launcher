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
import dev.minimal.launcher.data.FavoritePage
import dev.minimal.launcher.data.PageSchedule
import dev.minimal.launcher.data.PageScheduler
import dev.minimal.launcher.data.ScreenTime
import dev.minimal.launcher.data.Weather
import dev.minimal.launcher.data.WeatherInfo
import dev.minimal.launcher.data.LauncherSettings
import dev.minimal.launcher.data.NotificationPreview
import dev.minimal.launcher.data.NotificationStore
import dev.minimal.launcher.data.PrivateSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import java.time.LocalDateTime
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
        // Zeitplan jede Minute prüfen (zur vollen Minute).
        viewModelScope.launch {
            while (true) {
                checkSchedule()
                delay(60_000 - System.currentTimeMillis() % 60_000)
            }
        }
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
        checkSchedule()
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
    fun startShortcutById(appInfo: AppInfo, id: String) = app.apps.startShortcutById(appInfo, id)

    fun setSwipeLeftShortcut(appKey: String, id: String?, label: String?) = store.update { s ->
        s.copy(favorites = s.favorites.map {
            if (it.page == s.activePage && !it.isFolder && it.apps.firstOrNull() == appKey) {
                it.copy(swipeLeftShortcut = id, swipeLeftLabel = label)
            } else {
                it
            }
        })
    }

    fun update(block: (LauncherSettings) -> LauncherSettings) = store.update(block)

    // --- Favoriten ---------------------------------------------------------

    fun toggleFavorite(appInfo: AppInfo) = store.update { s ->
        if (s.isFavorite(appInfo.key)) {
            val page = s.activePage
            s.copy(favorites = s.favorites.filterNot {
                it.page == page && !it.isFolder && it.apps.firstOrNull() == appInfo.key
            })
        } else {
            s.copy(favorites = s.favorites + Favorite(UUID.randomUUID().toString(), listOf(appInfo.key), page = s.activePage))
        }
    }

    fun setSwipeApp(appKey: String, swipeKey: String?) = store.update { s ->
        s.copy(favorites = s.favorites.map {
            if (it.page == s.activePage && !it.isFolder && it.apps.firstOrNull() == appKey) it.copy(swipeApp = swipeKey) else it
        })
    }

    fun addContactFavorite(uri: String, name: String) = store.update { s ->
        if (s.pageFavorites().any { it.contactUri == uri }) return@update s
        s.copy(
            favorites = s.favorites + Favorite(
                UUID.randomUUID().toString(), emptyList(), name = name, contactUri = uri, page = s.activePage,
            ),
        )
    }

    fun createFolder(name: String, appKeys: List<String>) = store.update { s ->
        s.copy(favorites = s.favorites + Favorite(UUID.randomUUID().toString(), appKeys, name = name, page = s.activePage))
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

    /** Verschiebt einen Favoriten innerhalb seiner Seite um eine Position. */
    fun moveFavorite(id: String, delta: Int) = store.update { s ->
        val fav = s.favorites.firstOrNull { it.id == id } ?: return@update s
        val samePage = s.favorites.filter { it.page == fav.page }
        val from = samePage.indexOf(fav)
        val to = from + delta
        if (to !in samePage.indices) return@update s
        val reordered = samePage.toMutableList().apply { add(to, removeAt(from)) }
        s.copy(favorites = s.favorites.filter { it.page != fav.page } + reordered)
    }

    // --- Seiten ------------------------------------------------------------

    /** Zuletzt vom Zeitplan bestimmte Seite – gewechselt wird nur, wenn sich diese ändert. */
    private var lastScheduledPage: String? = null

    /**
     * Wechselt automatisch die Seite, sobald ein Zeitfenster beginnt oder endet.
     * Manuelles Wechseln bleibt dazwischen möglich.
     */
    fun checkSchedule(now: LocalDateTime = LocalDateTime.now()) {
        val s = store.value
        if (!s.autoPages || s.pages.size < 2) {
            lastScheduledPage = null
            return
        }
        val target = PageScheduler.pageFor(s, now)
        if (target != lastScheduledPage) {
            lastScheduledPage = target
            setCurrentPage(target)
        }
    }

    fun setPageSchedule(id: String, schedule: PageSchedule?) {
        store.update { s -> s.copy(pages = s.pages.map { if (it.id == id) it.copy(schedule = schedule) else it }) }
        lastScheduledPage = null
        checkSchedule()
    }

    fun setAutoPages(enabled: Boolean) {
        store.update { it.copy(autoPages = enabled) }
        lastScheduledPage = null
        checkSchedule()
    }

    fun setCurrentPage(id: String) = store.update { if (it.currentPage == id) it else it.copy(currentPage = id) }

    fun addPage(name: String) = store.update { s ->
        val page = FavoritePage(UUID.randomUUID().toString(), name.ifBlank { "Seite ${s.pages.size + 1}" })
        s.copy(pages = s.pages + page, currentPage = page.id)
    }

    fun renamePage(id: String, name: String) = store.update { s ->
        s.copy(pages = s.pages.map { if (it.id == id) it.copy(name = name.ifBlank { it.name }) else it })
    }

    /** Löscht eine Seite; ihre Favoriten wandern auf die erste verbleibende Seite. */
    fun removePage(id: String) = store.update { s ->
        if (s.pages.size <= 1) return@update s
        val remaining = s.pages.filterNot { it.id == id }
        val target = remaining.first().id
        s.copy(
            pages = remaining,
            favorites = s.favorites.map { if (it.page == id) it.copy(page = target) else it },
            currentPage = if (s.currentPage == id) target else s.currentPage,
        )
    }

    fun movePage(id: String, delta: Int) = store.update { s ->
        val from = s.pages.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in s.pages.indices) return@update s
        s.copy(pages = s.pages.toMutableList().apply { add(to, removeAt(from)) })
    }

    fun moveFavoriteToPage(favoriteId: String, pageId: String) = store.update { s ->
        val fav = s.favorites.firstOrNull { it.id == favoriteId } ?: return@update s
        s.copy(favorites = s.favorites.filterNot { it.id == favoriteId } + fav.copy(page = pageId))
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
                if (apps.isEmpty() && !f.isFolder && !f.isContact) null else f.copy(apps = apps)
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

    // --- Fokus-Modus ------------------------------------------------------

    fun toggleFocusApp(key: String) = store.update { s ->
        s.copy(focusApps = if (key in s.focusApps) s.focusApps - key else s.focusApps + key)
    }

    fun setFocusManual(enabled: Boolean) = store.update { it.copy(focusManual = enabled) }

    fun setFocusSchedule(schedule: PageSchedule?) = store.update { it.copy(focusSchedule = schedule) }

    suspend fun weather(force: Boolean = false): WeatherInfo? {
        val s = store.value
        if (!s.showWeather) return null
        return Weather.load(getApplication(), s.weatherCity, force)
    }

    fun setNote(text: String) = store.update { it.copy(note = text.trim()) }

    suspend fun screenTimeToday(): Map<String, Long> =
        withContext(Dispatchers.IO) { ScreenTime.today(getApplication()) }

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
