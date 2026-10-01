package dev.minimal.launcher.data

import dev.minimal.launcher.util.tr

/**
 * Kanso-Stile: ruhige Volltonhintergründe mit abgestimmten Text- und Akzentfarben.
 * Farben als ARGB-Ints, damit die Datenschicht ohne Compose auskommt.
 */
enum class KansoStyle(
    private val de: String,
    private val en: String,
    val background: Long,
    val text: Long,
    val accent: Long,
    val dark: Boolean,
) {
    NONE("Aus – Hintergrundbild", "Off – wallpaper", 0, 0, 0, true),
    SUMI("Sumi · Tusche", "Sumi · ink", 0xFF15130F, 0xFFF2EDE4, 0xFFC9A66B, true),
    WASHI("Washi · Papier", "Washi · paper", 0xFFF1ECE2, 0xFF1E1B16, 0xFF9C4A3A, false),
    MATCHA("Matcha · Tee", "Matcha · tea", 0xFF1F261F, 0xFFE6EBDD, 0xFFA8C28A, true),
    YORU("Yoru · Nacht", "Yoru · night", 0xFF0F1520, 0xFFE4E8F0, 0xFF8FA8D8, true),
    SAKURA("Sakura · Kirschblüte", "Sakura · blossom", 0xFFF6ECEC, 0xFF2A1F22, 0xFFA24A64, false);

    val label: String get() = tr(de, en)
    val active: Boolean get() = this != NONE
}
