package dev.minimal.launcher.util

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateUtils
import androidx.core.content.ContextCompat

data class CalendarEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

object CalendarEvents {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Nächster (oder gerade laufender) Termin innerhalb der nächsten 24 Stunden. */
    fun next(context: Context, now: Long = System.currentTimeMillis()): CalendarEvent? {
        if (!hasPermission(context)) return null
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - DateUtils.DAY_IN_MILLIS)
            ContentUris.appendId(it, now + DateUtils.DAY_IN_MILLIS)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.SELF_ATTENDEE_STATUS,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                val events = buildList {
                    while (c.moveToNext()) {
                        if (c.getInt(5) == CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                        val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                        add(CalendarEvent(c.getLong(0), title, c.getLong(2), c.getLong(3), c.getInt(4) == 1))
                    }
                }
                // Termine mit Uhrzeit bevorzugen; ganztägige nur, wenn sie heute sind.
                events.firstOrNull { !it.allDay && it.end > now && it.begin < now + DateUtils.DAY_IN_MILLIS }
                    ?: events.firstOrNull { it.allDay && it.end > now && it.begin <= now }
            }
        } catch (e: Exception) {
            null
        }
    }

    fun open(context: Context, event: CalendarEvent) {
        val intent = Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event.id))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.begin)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.end)
        SystemActions.start(context, intent)
    }
}
