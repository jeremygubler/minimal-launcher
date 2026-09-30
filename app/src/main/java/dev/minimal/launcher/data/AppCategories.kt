package dev.minimal.launcher.data

import android.content.pm.ApplicationInfo

/** App-Kategorien, wie sie Android aus den App-Angaben liefert. */
object AppCategories {
    val all: List<Pair<Int, String>> = listOf(
        ApplicationInfo.CATEGORY_SOCIAL to "Social",
        ApplicationInfo.CATEGORY_VIDEO to "Video",
        ApplicationInfo.CATEGORY_GAME to "Spiele",
        ApplicationInfo.CATEGORY_NEWS to "News",
        ApplicationInfo.CATEGORY_AUDIO to "Musik & Audio",
        ApplicationInfo.CATEGORY_IMAGE to "Fotos",
        ApplicationInfo.CATEGORY_MAPS to "Karten & Navigation",
        ApplicationInfo.CATEGORY_PRODUCTIVITY to "Produktivität",
    )

    fun label(category: Int): String = all.firstOrNull { it.first == category }?.second ?: "Sonstige"
}
