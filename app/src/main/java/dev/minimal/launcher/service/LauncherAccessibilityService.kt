package dev.minimal.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/** Wird nur für globale Aktionen (Sperren, Benachrichtigungen) verwendet. */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        @Volatile
        private var instance: LauncherAccessibilityService? = null

        val isRunning: Boolean get() = instance != null

        fun perform(action: Int): Boolean = instance?.performGlobalAction(action) ?: false
    }
}
