package dev.minimal.launcher

import android.app.KeyguardManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Intent
import android.os.Build
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.widget.Toast
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
import dev.minimal.launcher.data.AutoBackup
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()
    lateinit var widgetHost: AppWidgetHost
        private set
    private val widgetManager by lazy { AppWidgetManager.getInstance(this) }

    private var pendingWidgetId = -1
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null

    private var pendingUnlock: (() -> Unit)? = null

    private val confirmCredential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) pendingUnlock?.invoke()
        pendingUnlock = null
    }

    /** Fingerabdruck/Gesicht/PIN abfragen, dann [onSuccess] ausführen (App-Sperre). */
    private fun unlockThen(title: String, onSuccess: () -> Unit) {
        val keyguard = getSystemService(KeyguardManager::class.java)
        if (keyguard == null || !keyguard.isDeviceSecure) {
            Toast.makeText(this, "Keine Displaysperre eingerichtet – App-Sperre ist wirkungslos", Toast.LENGTH_SHORT).show()
            onSuccess()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val prompt = BiometricPrompt.Builder(this)
                .setTitle(title)
                .setSubtitle("Entsperren zum Öffnen")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
                )
                .build()
            prompt.authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
            })
        } else {
            @Suppress("DEPRECATION")
            val intent = keyguard.createConfirmDeviceCredentialIntent(title, "Entsperren zum Öffnen") ?: run {
                onSuccess()
                return
            }
            pendingUnlock = onSuccess
            confirmCredential.launch(intent)
        }
    }

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
            override fun authenticate(title: String, onSuccess: () -> Unit) = unlockThen(title, onSuccess)
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
        if (intent.action == Intent.ACTION_MAIN) {
            // Fenster hatte schon den Fokus und wurde nicht aus einer anderen App nach vorne geholt
            // → Home wurde auf dem Startbildschirm gedrückt.
            val alreadyOnHome = hasWindowFocus() &&
                (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) == 0
            vm.onHomePressed(alreadyOnHome)
        }
    }

    override fun onStart() {
        super.onStart()
        vm.checkSchedule()
        DeviceCompat.rebindNotificationListener(this)
        // Tägliche Sicherung (nur wenn ein Ordner gewählt ist).
        lifecycleScope.launch(Dispatchers.IO) { AutoBackup.maybeRun(this@MainActivity, launcherApp.settings) }
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
