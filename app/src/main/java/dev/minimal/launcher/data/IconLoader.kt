package dev.minimal.launcher.data

import android.content.Context
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class IconLoader(private val context: Context) {
    private val cache = LruCache<String, ImageBitmap>(400)
    private val sizePx = (56 * context.resources.displayMetrics.density).toInt()
    private val densityDpi = context.resources.displayMetrics.densityDpi

    @Volatile
    private var iconPack: IconPack? = null

    private val _version = MutableStateFlow(0)
    /** Wird erhöht, wenn sich das Icon-Pack ändert, damit Icons neu geladen werden. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun setIconPack(packageName: String?) {
        if (iconPack?.packageName == packageName) return
        iconPack = packageName?.let { IconPack.load(context, it) }
        cache.evictAll()
        _version.value += 1
    }

    suspend fun load(app: AppInfo): ImageBitmap = withContext(Dispatchers.IO) {
        cache.get(app.key) ?: run {
            val fromPack = iconPack?.drawableFor(app.component)?.let {
                if (app.isWork) context.packageManager.getUserBadgedIcon(it, app.user) else it
            }
            val drawable = fromPack ?: app.info.getBadgedIcon(densityDpi)
            drawable.toBitmap(sizePx, sizePx).asImageBitmap().also { cache.put(app.key, it) }
        }
    }
}
