package dev.minimal.launcher.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Beobachtet Kopfhörer, Laden, Bluetooth-Geräte und WLAN für kontextbasierte Seiten.
 * Alles bleibt auf dem Gerät; Bluetooth/WLAN nur mit den jeweiligen Berechtigungen.
 */
object ContextMonitor {
    private val _state = MutableStateFlow(ContextState())
    val state: StateFlow<ContextState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var started = false
    private var bluetoothReady = false
    private var wifiRegistered = false

    fun hasBluetoothPermission(context: Context) = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    fun hasWifiPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        val app = context.applicationContext
        if (!started) {
            started = true
            startAudio(app)
            startCharging(app)
        }
        refresh(app)
    }

    /** Nach neu erteilten Berechtigungen aufrufen. */
    fun refresh(context: Context) {
        val app = context.applicationContext
        if (!bluetoothReady && hasBluetoothPermission(app)) startBluetooth(app)
        if (!wifiRegistered && hasWifiPermission(app)) startWifi(app)
    }

    // --- Kopfhörer ------------------------------------------------------------

    private val headphoneTypes = buildSet {
        add(AudioDeviceInfo.TYPE_WIRED_HEADSET)
        add(AudioDeviceInfo.TYPE_WIRED_HEADPHONES)
        add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
        add(AudioDeviceInfo.TYPE_USB_HEADSET)
        if (Build.VERSION.SDK_INT >= 31) add(AudioDeviceInfo.TYPE_BLE_HEADSET)
    }

    private fun startAudio(app: Context) {
        val am = app.getSystemService(AudioManager::class.java) ?: return
        fun update() {
            val connected = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in headphoneTypes }
            _state.update { it.copy(headphones = connected) }
        }
        am.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = update()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = update()
        }, handler)
        update()
    }

    // --- Laden ------------------------------------------------------------------

    private fun startCharging(app: Context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: return
                val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                val charging = plugged || status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                _state.update { it.copy(charging = charging) }
            }
        }
        ContextCompat.registerReceiver(
            app, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let { receiver.onReceive(app, it) }
    }

    // --- Bluetooth ----------------------------------------------------------------

    @SuppressLint("MissingPermission")
    private fun deviceName(device: BluetoothDevice?): String? = try {
        device?.name
    } catch (e: SecurityException) {
        null
    }

    @SuppressLint("MissingPermission")
    private fun startBluetooth(app: Context) {
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        bluetoothReady = true
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent ?: return
                val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                val name = deviceName(device) ?: return
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> _state.update { it.copy(bluetooth = it.bluetooth + name) }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> _state.update { it.copy(bluetooth = it.bluetooth - name) }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Bereits verbundene Audio-Geräte (Auto, Kopfhörer) beim Start einlesen.
        for (profile in listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)) {
            try {
                adapter.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        val names = try {
                            proxy.connectedDevices.mapNotNull { deviceName(it) }
                        } catch (e: SecurityException) {
                            emptyList()
                        }
                        _state.update { it.copy(bluetooth = it.bluetooth + names) }
                        adapter.closeProfileProxy(p, proxy)
                    }

                    override fun onServiceDisconnected(p: Int) = Unit
                }, profile)
            } catch (_: Exception) {
            }
        }
    }

    /** Namen gekoppelter Bluetooth-Geräte (für die Auswahl in den Einstellungen). */
    @SuppressLint("MissingPermission")
    fun bondedDevices(context: Context): List<String> {
        if (!hasBluetoothPermission(context)) return emptyList()
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty().mapNotNull { deviceName(it) }.distinct().sorted()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    // --- WLAN -------------------------------------------------------------------------

    private fun cleanSsid(raw: String?): String? =
        raw?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != WifiManager.UNKNOWN_SSID && it != "<unknown ssid>" }

    private fun startWifi(app: Context) {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return
        val wifiManager = app.getSystemService(WifiManager::class.java)
        wifiRegistered = true

        fun onCaps(caps: NetworkCapabilities) {
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                _state.update { it.copy(wifi = null) }
                return
            }
            val fromCaps = if (Build.VERSION.SDK_INT >= 29) (caps.transportInfo as? WifiInfo)?.ssid else null
            @Suppress("DEPRECATION")
            val ssid = cleanSsid(fromCaps) ?: cleanSsid(wifiManager?.connectionInfo?.ssid)
            _state.update { it.copy(wifi = ssid) }
        }

        val callback = if (Build.VERSION.SDK_INT >= 31) {
            object : ConnectivityManager.NetworkCallback(ConnectivityManager.NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(caps)
                override fun onLost(network: Network) = _state.update { it.copy(wifi = null) }
            }
        } else {
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = onCaps(caps)
                override fun onLost(network: Network) = _state.update { it.copy(wifi = null) }
            }
        }
        try {
            cm.registerDefaultNetworkCallback(callback, handler)
        } catch (e: Exception) {
            wifiRegistered = false
        }
    }
}
