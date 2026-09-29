package dev.minimal.launcher

import android.app.Application
import android.content.Context
import dev.minimal.launcher.data.AppRepository
import dev.minimal.launcher.data.IconLoader
import dev.minimal.launcher.data.SettingsStore

class LauncherApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var icons: IconLoader
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        apps = AppRepository(this)
        icons = IconLoader(this)
    }
}

val Context.launcherApp: LauncherApp
    get() = applicationContext as LauncherApp
