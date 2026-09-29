package dev.minimal.launcher.data

import java.time.LocalDateTime

object Focus {
    /** Ist der Fokus-Modus gerade aktiv (manuell oder per Zeitplan)? */
    fun isActive(settings: LauncherSettings, now: LocalDateTime): Boolean =
        settings.focusManual || settings.focusSchedule?.matches(now) == true

    /** Soll diese App gerade gebremst werden? */
    fun isBlocked(settings: LauncherSettings, appKey: String, now: LocalDateTime): Boolean =
        appKey in settings.focusApps && isActive(settings, now)
}
