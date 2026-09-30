package dev.minimal.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.minimal.launcher.ui.HomeCallbacks
import dev.minimal.launcher.ui.LauncherRoot
import dev.minimal.launcher.ui.LauncherTheme
import dev.minimal.launcher.util.DeviceCompat

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    lateinit var widgetHost: AppWidgetHost
        private set
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }

    private var pendingWidgetId = -1
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val info = pendingWidgetInfo
        if (result.resultCode == RESULT_OK && info != null) {
            configureOrAdd(pendingWidgetId, info)
        } else {
            discardPendingWidget()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)

        val callbacks = object : HomeCallbacks {
            override fun addWidget(info: AppWidgetProviderInfo) = startAddWidget(info)
            override fun removeWidget(id: Int) {
                widgetHost.deleteAppWidgetId(id)
                vm.removeWidget(id)
            }
            override fun setBlur(enabled: Boolean) = applyBlur(enabled)
            override fun openSettings() {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            LauncherTheme(settings) {
                LauncherRoot(vm = vm, widgetHost = widgetHost, callbacks = callbacks)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN) vm.onHomePressed()
    }

    override fun onStart() {
        super.onStart()
        vm.checkSchedule()
        DeviceCompat.rebindNotificationListener(this)
        try {
            widgetHost.startListening()
        } catch (_: Exception) {
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            widgetHost.stopListening()
        } catch (_: Exception) {
        }
    }

    // --- Widgets -----------------------------------------------------------

    private fun startAddWidget(info: AppWidgetProviderInfo) {
        val id = widgetHost.allocateAppWidgetId()
        pendingWidgetId = id
        pendingWidgetInfo = info
        if (widgetManager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
            configureOrAdd(id, info)
        } else {
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
            )
        }
    }

    private fun configureOrAdd(id: Int, info: AppWidgetProviderInfo) {
        if (info.configure != null) {
            try {
                @Suppress("DEPRECATION")
                widgetHost.startAppWidgetConfigureActivityForResult(this, id, 0, REQUEST_CONFIGURE, null)
                return
            } catch (_: Exception) {
            }
        }
        finishAddWidget(id)
    }

    @Deprecated("Wird für die Widget-Konfiguration benötigt")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CONFIGURE) {
            if (resultCode == RESULT_OK) finishAddWidget(pendingWidgetId) else discardPendingWidget()
        }
    }

    private fun finishAddWidget(id: Int) {
        if (id != -1) vm.addWidget(id)
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    private fun discardPendingWidget() {
        if (pendingWidgetId != -1) widgetHost.deleteAppWidgetId(pendingWidgetId)
        pendingWidgetId = -1
        pendingWidgetInfo = null
    }

    // --- Unschärfe (Android 12+) --------------------------------------------

    private fun applyBlur(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (!windowManager.isCrossWindowBlurEnabled) return
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 80 }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply { blurBehindRadius = 0 }
        }
    }

    companion object {
        const val WIDGET_HOST_ID = 1024
        private const val REQUEST_CONFIGURE = 11
    }
}
