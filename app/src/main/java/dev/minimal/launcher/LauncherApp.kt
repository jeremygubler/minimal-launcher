package dev.minimal.launcher

import android.app.Application
import android.content.Context
import dev.minimal.launcher.data.AppRepository
import dev.minimal.launcher.data.CrashLog
import dev.minimal.launcher.data.IconLoader
import dev.minimal.launcher.data.SettingsStore
import dev.minimal.launcher.data.UsageStore

class LauncherApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var apps: AppRepository
        private set
    lateinit var icons: IconLoader
        private set
    lateinit var usage: UsageStore
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        settings = SettingsStore(this)
        icons = IconLoader(this)
        usage = UsageStore(this)
        apps = AppRepository(this, icons)
    }
}

val Context.launcherApp: LauncherApp
    get() = applicationContext as LauncherApp
