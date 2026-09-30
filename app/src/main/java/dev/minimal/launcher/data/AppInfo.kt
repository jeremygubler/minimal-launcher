package dev.minimal.launcher.data

import android.content.ComponentName
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
import java.text.Normalizer

data class AppInfo(
    val key: String,
    val label: String,
    val originalLabel: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWork: Boolean,
    val isPrivate: Boolean = false,
    /** Zeitpunkt der Erstinstallation (für die „Neu“-Markierung). */
    val installTime: Long = 0L,
    /** App-Kategorie laut Android (ApplicationInfo.CATEGORY_*), -1 = unbekannt. */
    val category: Int = -1,
    val info: LauncherActivityInfo,
) {
    val notificationKey: String get() = notificationKey(packageName, user)

    val letter: String
        get() {
            val first = label.trim().firstOrNull() ?: return "#"
            val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
                .firstOrNull()?.uppercaseChar() ?: return "#"
            return if (base in 'A'..'Z') base.toString() else "#"
        }

    companion object {
        fun key(component: ComponentName, user: UserHandle) =
            "${component.flattenToString()}#${user.hashCode()}"

        fun notificationKey(packageName: String, user: UserHandle) = "$packageName#${user.hashCode()}"
    }
}
