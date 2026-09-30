package dev.minimal.launcher.data

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/** Unterstützt Icon-Packs im ADW/Nova-Format (appfilter.xml). */
class IconPack private constructor(
    val packageName: String,
    private val res: Resources,
    private val byComponent: Map<String, String>,
    private val byPackage: Map<String, String>,
) {
    @SuppressLint("DiscouragedApi")
    fun drawableFor(component: ComponentName): Drawable? {
        val name = byComponent[component.flattenToString()] ?: byPackage[component.packageName] ?: return null
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return try {
            res.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }

    @SuppressLint("DiscouragedApi")
    fun drawableByName(name: String): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName)
        if (id == 0) return null
        return try {
            res.getDrawable(id, null)
        } catch (e: Exception) {
            null
        }
    }

    @Volatile
    private var names: List<String>? = null

    /** Alle Icon-Namen des Packs (aus drawable.xml, sonst aus appfilter.xml). */
    @SuppressLint("DiscouragedApi")
    fun iconNames(): List<String> {
        names?.let { return it }
        val fromCatalog = try {
            val xmlId = res.getIdentifier("drawable", "xml", packageName)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("drawable.xml"), "utf-8")
                }
            }
            buildList {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        parser.getAttributeValue(null, "drawable")?.let(::add)
                    }
                    event = parser.next()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
        val result = fromCatalog.ifEmpty { byComponent.values.toList() }.distinct().sorted()
        names = result
        return result
    }

    companion object {
        private val ACTIONS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )

        /** Liefert installierte Icon-Packs als (Paketname, Anzeigename). */
        fun installed(context: Context): List<Pair<String, String>> {
            val pm = context.packageManager
            return ACTIONS.flatMap { pm.queryIntentActivities(Intent(it), 0) }
                .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase() }
        }

        @SuppressLint("DiscouragedApi")
        fun load(context: Context, packageName: String): IconPack? = try {
            val res = context.packageManager.getResourcesForApplication(packageName)
            val xmlId = res.getIdentifier("appfilter", "xml", packageName)
            val parser: XmlPullParser = if (xmlId != 0) {
                res.getXml(xmlId)
            } else {
                XmlPullParserFactory.newInstance().newPullParser().apply {
                    setInput(res.assets.open("appfilter.xml"), "utf-8")
                }
            }
            val byComponent = HashMap<String, String>()
            val byPackage = HashMap<String, String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    val comp = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (comp != null && drawable != null) {
                        val inner = comp.substringAfter("{", "").substringBefore("}", "")
                        ComponentName.unflattenFromString(inner)?.let { cn ->
                            byComponent[cn.flattenToString()] = drawable
                            byPackage.putIfAbsent(cn.packageName, drawable)
                        }
                    }
                }
                event = parser.next()
            }
            IconPack(packageName, res, byComponent, byPackage)
        } catch (e: Exception) {
            null
        }
    }
}
