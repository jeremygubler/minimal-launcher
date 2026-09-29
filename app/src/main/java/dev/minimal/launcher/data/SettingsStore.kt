package dev.minimal.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONObject

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<LauncherSettings> = _state.asStateFlow()

    val value: LauncherSettings get() = _state.value

    fun update(block: (LauncherSettings) -> LauncherSettings) {
        val next = _state.updateAndGet(block)
        prefs.edit().putString(KEY, next.toJson().toString()).apply()
    }

    fun exportJson(): String = value.toJson().toString(2)

    /** Widgets gehören zu diesem Gerät, deshalb bleiben die aktuellen beim Import erhalten. */
    fun importJson(json: String): Boolean = try {
        val imported = LauncherSettings.fromJson(JSONObject(json), keepWidgets = value.widgets)
        update { imported.copy(firstRunDone = true) }
        true
    } catch (e: Exception) {
        false
    }

    private fun load(): LauncherSettings = try {
        prefs.getString(KEY, null)?.let { LauncherSettings.fromJson(JSONObject(it)) } ?: LauncherSettings()
    } catch (e: Exception) {
        LauncherSettings()
    }

    private companion object {
        const val KEY = "settings"
    }
}
