package dev.minimal.launcher.data

import java.time.LocalDate

/** Tagesabsicht: gilt nur am Tag, an dem sie gesetzt wurde. */
object DailyIntention {
    /** Absicht von heute oder null. */
    fun today(settings: LauncherSettings, today: LocalDate): String? =
        settings.dailyIntention.takeIf { it.isNotBlank() && settings.dailyIntentionDate == today.toString() }

    /** Soll heute nach der Absicht gefragt werden? */
    fun shouldAsk(settings: LauncherSettings, today: LocalDate, pro: Boolean): Boolean =
        pro && settings.dailyIntentionEnabled && today(settings, today) == null

    fun set(settings: LauncherSettings, text: String, today: LocalDate): LauncherSettings =
        settings.copy(dailyIntention = text.trim(), dailyIntentionDate = today.toString(), dailyIntentionDone = false)
}
