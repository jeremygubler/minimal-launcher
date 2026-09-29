package dev.minimal.launcher.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

data class ContactResult(val id: Long, val lookupKey: String, val name: String, val photo: Uri?) {
    val uri: Uri get() = ContactsContract.Contacts.getLookupUri(id, lookupKey)
}

object ContactSearch {
    fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun search(context: Context, query: String, limit: Int = 5): List<ContactResult> {
        val q = query.trim()
        if (q.length < 2 || !hasPermission(context)) return emptyList()
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(q))
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext() && size < limit) {
                        val name = c.getString(2) ?: continue
                        add(ContactResult(c.getLong(0), c.getString(1) ?: "", name, c.getString(3)?.let(Uri::parse)))
                    }
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun open(context: Context, contact: ContactResult) {
        SystemActions.start(context, Intent(Intent.ACTION_VIEW, contact.uri))
    }
}
