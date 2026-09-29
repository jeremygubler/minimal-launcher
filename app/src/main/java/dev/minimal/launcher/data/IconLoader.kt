package dev.minimal.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Lädt App-Icons mit zwei Cache-Stufen: Arbeitsspeicher und Dateien im Cache-Ordner.
 * Der Datei-Cache sorgt dafür, dass Icons nach einem Neustart sofort da sind.
 */
class IconLoader(private val context: Context) {
    private val memory = LruCache<String, ImageBitmap>(400)
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

    fun setIconPack(packageName: String?) {
        if (initialized && iconPackName == packageName) return
        initialized = true
        iconPackName = packageName
        iconPack = packageName?.let { IconPack.load(context, it) }
        // Datei-Cache nur leeren, wenn sich das Icon-Pack seit dem letzten Start geändert hat.
        val marker = File(dir, "pack.txt")
        val stored = if (marker.exists()) marker.readText() else ""
        if (stored != packageName.orEmpty()) {
            dir.listFiles()?.forEach { it.delete() }
            marker.writeText(packageName.orEmpty())
        }
        memory.evictAll()
        _version.value += 1
    }

    /** Nach Installation oder Update einer App deren Icons verwerfen. */
    fun invalidatePackage(packageName: String) {
        memory.snapshot().keys.filter { it.startsWith("$packageName/") }.forEach { memory.remove(it) }
        dir.listFiles { f -> f.name.startsWith("${packageName}__") }?.forEach { it.delete() }
        _version.value += 1
    }

    private fun fileFor(app: AppInfo) =
        File(dir, "${app.packageName}__${app.key.hashCode().toUInt()}.png")

    suspend fun load(app: AppInfo): ImageBitmap = withContext(Dispatchers.IO) {
        memory.get(app.key)?.let { return@withContext it }

        val file = fileFor(app)
        val fromDisk = if (file.exists()) {
            try {
                BitmapFactory.decodeFile(file.path)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val bitmap = fromDisk ?: render(app).also { bmp ->
            // Erst speichern, wenn das Icon-Pack geladen ist – sonst landen falsche Icons im Cache.
            if (initialized) try {
                file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } catch (_: Exception) {
            }
        }
        bitmap.asImageBitmap().also { if (initialized) memory.put(app.key, it) }
    }

    private fun render(app: AppInfo): Bitmap {
        val fromPack = iconPack?.drawableFor(app.component)?.let {
            if (app.isWork) context.packageManager.getUserBadgedIcon(it, app.user) else it
        }
        val drawable = fromPack ?: app.info.getBadgedIcon(densityDpi)
        return drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }
}
