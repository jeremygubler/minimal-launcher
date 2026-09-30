package dev.minimal.launcher.data

import dev.minimal.launcher.util.tr
import android.content.pm.ApplicationInfo

/** App-Kategorien, wie sie Android aus den App-Angaben liefert. */
object AppCategories {
    val all: List<Pair<Int, String>> get() = listOf(
        ApplicationInfo.CATEGORY_SOCIAL to "Social",
        ApplicationInfo.CATEGORY_VIDEO to "Video",
        ApplicationInfo.CATEGORY_GAME to tr("Spiele", "Games"),
        ApplicationInfo.CATEGORY_NEWS to "News",
        ApplicationInfo.CATEGORY_AUDIO to tr("Musik & Audio", "Music & audio"),
        ApplicationInfo.CATEGORY_IMAGE to tr("Fotos", "Photos"),
        ApplicationInfo.CATEGORY_MAPS to tr("Karten & Navigation", "Maps & navigation"),
        ApplicationInfo.CATEGORY_PRODUCTIVITY to tr("Produktivität", "Productivity"),
    )

    fun label(category: Int): String = all.firstOrNull { it.first == category }?.second ?: tr("Sonstige", "Other")
}
