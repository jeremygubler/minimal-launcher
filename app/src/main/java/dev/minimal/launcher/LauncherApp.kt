package dev.minimal.launcher

import android.app.Application
import android.content.Context
import dev.minimal.launcher.data.AppRepository
import dev.minimal.launcher.data.ContextMonitor
import dev.minimal.launcher.pro.Pro
import dev.minimal.launcher.data.CrashLog
import dev.minimal.launcher.data.IconLoader
import dev.minimal.launcher.data.SettingsStore
import dev.minimal.launcher.data.DigestStore
import dev.minimal.launcher.data.FocusSessionLog
import dev.minimal.launcher.data.IntentionLog
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
    lateinit var intentions: IntentionLog
        private set
    lateinit var focusSessions: FocusSessionLog
        private set
    lateinit var digest: DigestStore
        private set

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        ContextMonitor.start(this)
        Pro.init(this)
        settings = SettingsStore(this)
        icons = IconLoader(this)
        usage = UsageStore(this)
        intentions = IntentionLog(this)
        focusSessions = FocusSessionLog(this)
        digest = DigestStore(this)
        apps = AppRepository(this, icons)
    }
}

val Context.launcherApp: LauncherApp
    get() = applicationContext as LauncherApp
