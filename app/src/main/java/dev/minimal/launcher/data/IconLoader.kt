package dev.minimal.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import java.util.UUID
import androidx.annotation.RequiresApi
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lädt App-Icons mit zwei Cache-Stufen: Arbeitsspeicher und Dateien im Cache-Ordner.
 * Der Datei-Cache sorgt dafür, dass Icons nach einem Neustart sofort da sind.
 */
class IconLoader(private val context: Context) {
    /** Nach Speichergröße begrenzt (höchstens 1/8 des App-Speichers, max. 24 MB). */
    private val memory = object : LruCache<String, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).coerceAtMost(24L * 1024).toInt(),
    ) {
        override fun sizeOf(key: String, value: ImageBitmap) = (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }
    private val sizePx = (56 * context.resources.displayMetrics.density).toInt()
    private val densityDpi = context.resources.displayMetrics.densityDpi
    private val dir = File(context.cacheDir, "icons").apply { mkdirs() }

    @Volatile
    private var iconPack: IconPack? = null

    @Volatile
    private var iconPackName: String? = null

    @Volatile
    private var initialized = false

    private val _version = MutableStateFlow(0)
    /** Wird erhöht, wenn Icons neu geladen werden müssen (Icon-Pack oder App geändert). */
    val version: StateFlow<Int> = _version.asStateFlow()

    @Volatile
    private var themed = false

    /** Eigene Icons: App-Schlüssel → Quelle. */
    @Volatile
    private var custom: Map<String, String> = emptyMap()
    private val extraPacks = HashMap<String, IconPack?>()
    private val customDir = File(context.filesDir, "custom_icons").apply { mkdirs() }
    private val previews = LruCache<String, ImageBitmap>(300)

    fun setCustomIcons(map: Map<String, String>) {
        val changed = (custom.keys + map.keys).filter { custom[it] != map[it] }
        custom = map
        if (changed.isEmpty()) return
        memory.snapshot().keys.filter { k -> changed.any { k.startsWith("$it|") } }.forEach { memory.remove(it) }
        _version.update { it + 1 }
    }

    private fun packFor(pkg: String): IconPack? =
        if (pkg == iconPackName && iconPack != null) iconPack
        else synchronized(extraPacks) { extraPacks.getOrPut(pkg) { IconPack.load(context, pkg) } }

    private fun customDrawable(spec: String): Drawable? = when {
        spec.startsWith("file:") -> BitmapFactory.decodeFile(File(customDir, spec.removePrefix("file:")).path)
            ?.let { BitmapDrawable(context.resources, it) }
        spec.startsWith("pack:") -> {
            val rest = spec.removePrefix("pack:")
            packFor(rest.substringBefore('/'))?.drawableByName(rest.substringAfter('/'))
        }
        else -> null
    }

    /** Vorschau eines Icons aus einem Pack (für die Auswahl). */
    suspend fun packPreview(pkg: String, name: String): ImageBitmap? = withContext(Dispatchers.IO) {
        val key = "$pkg/$name"
        previews.get(key) ?: packFor(pkg)?.drawableByName(name)
            ?.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)?.asImageBitmap()?.also { previews.put(key, it) }
    }

    suspend fun packIconNames(pkg: String): List<String> = withContext(Dispatchers.IO) { packFor(pkg)?.iconNames().orEmpty() }

    /** Bild aus der Galerie quadratisch zuschneiden, speichern und als Quelle zurückgeben. */
    suspend fun importImage(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val side = minOf(source.width, source.height)
            val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
            val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
            val name = UUID.randomUUID().toString() + ".png"
            File(customDir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
            "file:$name"
        } catch (e: Exception) {
            null
        }
    }

    fun deleteCustomFile(spec: String?) {
        if (spec?.startsWith("file:") == true) File(customDir, spec.removePrefix("file:")).delete()
    }

    /** Icon-Pack und Designsymbole (einfarbige Icons in Systemfarben, Android 13+) setzen. */
    fun configure(packageName: String?, themedIcons: Boolean) {
        if (initialized && iconPackName == packageName && themed == themedIcons) return
        initialized = true
        if (iconPackName != packageName || iconPack == null) {
            iconPack = packageName?.let { IconPack.load(context, it) }
        }
        iconPackName = packageName
        themed = themedIcons
        // Datei-Cache nur leeren, wenn sich die Einstellung seit dem letzten Start geändert hat.
        val marker = File(dir, "pack.txt")
        val config = "${packageName.orEmpty()}|$themedIcons"
        val stored = if (marker.exists()) marker.readText() else ""
        if (stored != config) {
            dir.listFiles()?.forEach { it.delete() }
            marker.writeText(config)
        }
        memory.evictAll()
        _version.update { it + 1 }
    }

    /** Nach Installation oder Update einer App deren Icons verwerfen. */
    fun invalidatePackage(packageName: String) {
        memory.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { memory.remove(it) }
        dir.listFiles { f -> f.name.startsWith("${packageName}__") }?.forEach { it.delete() }
        _version.update { it + 1 }
    }

    private fun style(dark: Boolean) = if (themed) (if (dark) "td" else "tl") else "n"

    private fun fileFor(app: AppInfo, style: String) =
        File(dir, "${app.packageName}__${(app.key + style).hashCode().toUInt()}.png")

    /** [dark] bestimmt die Farben der Designsymbole. */
    suspend fun load(app: AppInfo, dark: Boolean = true): ImageBitmap = withContext(Dispatchers.IO) {
        val style = style(dark)
        val memKey = "${app.key}|$style"
        memory.get(memKey)?.let { return@withContext it }

        // Eigene Icons nicht im Datei-Cache ablegen – sie werden direkt aus der Quelle gerendert.
        val useDisk = app.key !in custom
        val file = fileFor(app, style)
        val fromDisk = if (useDisk && file.exists()) {
            try {
                BitmapFactory.decodeFile(file.path)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val bitmap = fromDisk ?: render(app, dark).also { bmp ->
            // Erst speichern, wenn das Icon-Pack geladen ist – sonst landen falsche Icons im Cache.
            if (initialized && useDisk) try {
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Exception) {
            }
        }
        bitmap.asImageBitmap().also { if (initialized) memory.put(memKey, it) }
    }

    private fun render(app: AppInfo, dark: Boolean): Bitmap {
        custom[app.key]?.let(::customDrawable)?.let { d ->
            val badged = if (app.isWork || app.isPrivate) context.packageManager.getUserBadgedIcon(d, app.user) else d
            return badged.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }
        iconPack?.drawableFor(app.component)?.let { packIcon ->
            val d = if (app.isWork) context.packageManager.getUserBadgedIcon(packIcon, app.user) else packIcon
            return d.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        }
        if (themed && Build.VERSION.SDK_INT >= 33) {
            val base = app.info.getIcon(densityDpi)
            val mono = (base as? AdaptiveIconDrawable)?.monochrome
            if (mono != null) {
                val bmp = themedIcon(mono, dark)
                if (!app.isWork && !app.isPrivate) return bmp
                return context.packageManager
                    .getUserBadgedIcon(BitmapDrawable(context.resources, bmp), app.user)
                    .toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            }
        }
        return app.info.getBadgedIcon(densityDpi).toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    /** Einfarbiges Icon auf rundem Hintergrund in den Material-You-Farben – wie beim Pixel Launcher. */
    @RequiresApi(33)
    private fun themedIcon(mono: Drawable, dark: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val bg = context.getColor(if (dark) android.R.color.system_neutral1_800 else android.R.color.system_accent1_100)
        val fg = context.getColor(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_700)
        val half = sizePx / 2f
        canvas.drawCircle(half, half, half, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })
        canvas.clipPath(Path().apply { addCircle(half, half, half, Path.Direction.CW) })
        val layer = (mono.constantState?.newDrawable(context.resources) ?: mono).mutate()
        layer.setTint(fg)
        // Die Ebenen adaptiver Icons sind 1,5-mal so groß wie der sichtbare Bereich.
        val inset = sizePx / 4
        layer.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
        layer.draw(canvas)
        return bmp
    }
}
