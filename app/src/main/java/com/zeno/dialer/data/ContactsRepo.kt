package com.zeno.dialer.data

import android.content.Context
import android.provider.ContactsContract

class ContactsRepo(private val context: Context) {

    // In-memory cache so we only hit the DB once per process lifetime.
    // Contacts rarely change mid-session; a restart refreshes the cache.
    @Volatile private var cache: List<Contact>? = null

    fun search(query: String): List<Contact> {
        val all = cache ?: loadAll().also { cache = it }
        if (query.isBlank()) return all.take(15)
        return FuzzySearch.rank(query, all).take(15)
    }

    /** Force-reload the cache (call after the user edits a contact). */
    fun invalidate() { cache = null }

    /** Return contacts marked as starred/favorite in the system Contacts app. */
    fun getStarredContacts(): List<Contact> {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1"
        val cursor = try {
            context.contentResolver.query(uri, projection, selection, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")
        } catch (_: SecurityException) { null } ?: return emptyList()

        val contacts = mutableListOf<Contact>()
        val seenIds = mutableSetOf<Long>()
        cursor.use {
            val idCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                if (id in seenIds) continue
                seenIds.add(id)
                val number = it.getString(numCol).orEmpty().trim()
                if (number.isEmpty()) continue
                val name = it.getString(nameCol).orEmpty().trim().ifEmpty { number }
                val photo = if (photoCol >= 0) it.getString(photoCol) else null
                contacts.add(Contact(id = id, name = name, number = number, photoUri = photo))
            }
        }
        return contacts
    }

    private fun loadAll(): List<Contact> {
        val prefs = context.getSharedPreferences("zeno_settings", Context.MODE_PRIVATE)
        val sortBy = prefs.getInt("sort_by", 0) // 0=first name, 1=last name
        val nameFormat = prefs.getInt("name_format", 0) // 0=first name first, 1=last name first

        val uri        = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI
        )

        val cursor = try {
            context.contentResolver.query(
                uri, projection, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC"
            )
        } catch (e: SecurityException) {
            null
        } ?: return emptyList()

        val contacts = mutableListOf<Contact>()
        val seenIds  = mutableSetOf<Long>()

        cursor.use {
            val idCol    = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameCol  = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numCol   = it.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val photoCol = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

            while (it.moveToNext() && contacts.size < 500) {
                val id = it.getLong(idCol)
                if (id in seenIds) continue
                seenIds.add(id)

                val number = it.getString(numCol).orEmpty().trim()
                if (number.isEmpty()) continue

                val rawName = it.getString(nameCol).orEmpty().trim().ifEmpty { number }
                val name = if (nameFormat == 1 && rawName != number) {
                    val parts = rawName.split(" ", limit = 2)
                    if (parts.size == 2) "${parts[1]}, ${parts[0]}" else rawName
                } else rawName
                val photo = if (photoCol >= 0) it.getString(photoCol) else null

                contacts.add(Contact(id = id, name = name, number = number, photoUri = photo))
            }
        }

        // Sort by last name if preference is set
        return if (sortBy == 1) {
            contacts.sortedBy { c ->
                val parts = c.name.split(" ")
                if (parts.size > 1) parts.last() else c.name
            }
        } else {
            contacts
        }
    }
}
