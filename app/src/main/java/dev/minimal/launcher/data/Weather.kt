package dev.minimal.launcher.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class WeatherInfo(
    val temperature: Double,
    val code: Int,
    val min: Double,
    val max: Double,
    val place: String?,
) {
    val emoji: String get() = Weather.emoji(code)
    val description: String get() = Weather.describe(code)

    fun summary(): String =
        "$emoji ${temperature.roundToInt()}° · $description · ${min.roundToInt()}°/${max.roundToInt()}°"
}

/**
 * Wetter über Open-Meteo (kostenlos, ohne Konto). Nur aktiv, wenn der Nutzer es einschaltet.
 * Standort wird auf zwei Nachkommastellen (~1 km) gerundet, bevor er das Gerät verlässt.
 */
object Weather {
    private const val REFRESH_MS = 30 * 60 * 1000L

    fun hasLocationPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun load(context: Context, city: String, force: Boolean = false): WeatherInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
        val cacheKey = city.trim().lowercase()
        val cached = prefs.getString("data", null)?.let { runCatching { fromCache(JSONObject(it)) }.getOrNull() }
        val fresh = System.currentTimeMillis() - prefs.getLong("time", 0) < REFRESH_MS &&
            prefs.getString("key", null) == cacheKey
        if (!force && fresh && cached != null) return@withContext cached

        val coords = if (cacheKey.isNotEmpty()) geocode(city) else lastLocation(context)?.let { Triple(it.latitude, it.longitude, null) }
        if (coords == null) return@withContext cached
        val (lat, lon, place) = coords
        val info = try {
            parseForecast(JSONObject(get(forecastUrl(lat, lon))), place)
        } catch (e: Exception) {
            null
        } ?: return@withContext cached

        prefs.edit()
            .putString("data", toCache(info).toString())
            .putLong("time", System.currentTimeMillis())
            .putString("key", cacheKey)
            .apply()
        info
    }

    fun forecastUrl(lat: Double, lon: Double): String {
        fun round(v: Double) = "%.2f".format(java.util.Locale.ROOT, v)
        return "https://api.open-meteo.com/v1/forecast?latitude=${round(lat)}&longitude=${round(lon)}" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
            "&forecast_days=1&timezone=auto"
    }

    fun parseForecast(json: JSONObject, place: String?): WeatherInfo {
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        return WeatherInfo(
            temperature = current.getDouble("temperature_2m"),
            code = current.getInt("weather_code"),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0),
            place = place,
        )
    }

    fun parseGeocode(json: JSONObject): Triple<Double, Double, String?>? {
        val first = json.optJSONArray("results")?.optJSONObject(0) ?: return null
        return Triple(first.getDouble("latitude"), first.getDouble("longitude"), first.optString("name").ifEmpty { null })
    }

    private fun geocode(city: String): Triple<Double, Double, String?>? = try {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=1&language=de&name=" + Uri.encode(city.trim())
        parseGeocode(JSONObject(get(url)))
    } catch (e: Exception) {
        null
    }

    private fun lastLocation(context: Context): Location? {
        if (!hasLocationPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        return try {
            listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        } catch (e: SecurityException) {
            null
        }
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", "MinimalLauncher")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun toCache(i: WeatherInfo) = JSONObject().apply {
        put("t", i.temperature); put("c", i.code); put("min", i.min); put("max", i.max)
        i.place?.let { put("p", it) }
    }

    private fun fromCache(o: JSONObject) = WeatherInfo(
        o.getDouble("t"), o.getInt("c"), o.getDouble("min"), o.getDouble("max"), o.optString("p").ifEmpty { null },
    )

    /** WMO-Wettercodes → Symbol. */
    fun emoji(code: Int): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67, in 80..82 -> "🌧️"
        in 71..77, 85, 86 -> "❄️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Sonnig"
        1 -> "Überwiegend sonnig"
        2 -> "Teilweise bewölkt"
        3 -> "Bewölkt"
        45, 48 -> "Nebel"
        in 51..55 -> "Nieselregen"
        56, 57 -> "Gefrierender Niesel"
        61, 63 -> "Regen"
        65 -> "Starker Regen"
        66, 67 -> "Gefrierender Regen"
        in 71..75, 77 -> "Schnee"
        in 80..82 -> "Regenschauer"
        85, 86 -> "Schneeschauer"
        95 -> "Gewitter"
        96, 99 -> "Gewitter mit Hagel"
        else -> "Wetter"
    }
}
