package dev.minimal.launcher

import dev.minimal.launcher.data.Weather
import dev.minimal.launcher.data.WeatherInfo
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    @Test
    fun parsesForecast() {
        val json = JSONObject(
            """{"current":{"temperature_2m":17.6,"weather_code":2},
               "daily":{"temperature_2m_max":[21.4],"temperature_2m_min":[11.8]}}"""
        )
        val info = Weather.parseForecast(json, "Zürich")
        assertEquals(WeatherInfo(17.6, 2, 11.8, 21.4, "Zürich"), info)
        assertEquals("🌤️ 18° · Teilweise bewölkt · 12°/21°", info.summary())
    }

    @Test
    fun parsesGeocode() {
        val json = JSONObject("""{"results":[{"name":"Zürich","latitude":47.37,"longitude":8.55}]}""")
        assertEquals(Triple(47.37, 8.55, "Zürich"), Weather.parseGeocode(json))
        assertNull(Weather.parseGeocode(JSONObject("{}")))
    }

    @Test
    fun roundsCoordinatesForPrivacy() {
        val url = Weather.forecastUrl(47.376887, 8.541694)
        assertTrue(url.contains("latitude=47.38&longitude=8.54"))
    }

    @Test
    fun mapsWeatherCodes() {
        assertEquals("Sonnig", Weather.describe(0))
        assertEquals("Gewitter mit Hagel", Weather.describe(99))
        assertEquals("❄️", Weather.emoji(73))
    }
}
