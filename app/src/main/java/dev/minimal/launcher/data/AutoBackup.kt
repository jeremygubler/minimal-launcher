package dev.minimal.launcher.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.time.LocalDate

/**
 * Tägliche Sicherung der Einstellungen in einen vom Nutzer gewählten Ordner (Storage Access Framework).
 * Es werden die letzten [KEEP] Sicherungen behalten.
 */
object AutoBackup {
    private const val PREFIX = "minimal-launcher-"
    private const val KEEP = 7
    private const val INTERVAL_MS = 24L * 60 * 60 * 1000

    fun lastRun(context: Context): Long =
        context.getSharedPreferences("backup", Context.MODE_PRIVATE).getLong("last", 0)

    /** Sichert, wenn ein Ordner gewählt ist und die letzte Sicherung älter als 24 h ist. */
    fun maybeRun(context: Context, store: SettingsStore) {
        val folder = store.value.backupFolder ?: return
        if (System.currentTimeMillis() - lastRun(context) < INTERVAL_MS) return
        runNow(context, folder, store.exportJson())
    }

    fun runNow(context: Context, folder: String, json: String): Boolean = try {
        val resolver = context.contentResolver
        val tree = Uri.parse(folder)
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val name = PREFIX + LocalDate.now() + ".json"

        children(resolver, tree, rootId).filter { it.second == name }.forEach { delete(resolver, tree, it.first) }
        val doc = DocumentsContract.createDocument(resolver, parent, "application/json", name)
            ?: error("Datei konnte nicht angelegt werden")
        resolver.openOutputStream(doc)?.use { it.write(json.toByteArray()) } ?: error("Kein Schreibzugriff")

        children(resolver, tree, rootId)
            .filter { it.second.startsWith(PREFIX) && it.second.endsWith(".json") }
            .sortedByDescending { it.second }
            .drop(KEEP)
            .forEach { delete(resolver, tree, it.first) }

        context.getSharedPreferences("backup", Context.MODE_PRIVATE).edit()
            .putLong("last", System.currentTimeMillis()).apply()
        true
    } catch (e: Exception) {
        false
    }

    /** Lesbarer Ordnername aus der Tree-URI, z. B. „Documents/Backups“. */
    fun folderLabel(folder: String): String =
        Uri.decode(Uri.parse(folder).lastPathSegment ?: folder).substringAfter(':').ifEmpty { "Hauptordner" }

    private fun children(resolver: ContentResolver, tree: Uri, rootId: String): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        return resolver.query(uri, projection, null, null, null)?.use { c ->
            buildList { while (c.moveToNext()) add(c.getString(0) to (c.getString(1) ?: "")) }
        } ?: emptyList()
    }

    private fun delete(resolver: ContentResolver, tree: Uri, docId: String) {
        try {
            DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, docId))
        } catch (_: Exception) {
        }
    }
}
