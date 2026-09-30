package dev.minimal.launcher.util

import java.util.Locale

/**
 * Zweisprachigkeit ohne Ressourcen-IDs: Deutsch für deutschsprachige Systeme, sonst Englisch.
 * Wird bei jedem Aufruf ausgewertet – ein Sprachwechsel greift, sobald die Oberfläche neu zeichnet.
 */
val isGerman: Boolean get() = Locale.getDefault().language == "de"

fun tr(de: String, en: String): String = if (isGerman) de else en
