package com.zeno.dialer.data

import android.content.Context
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract

class RecentsRepo(private val context: Context) {

    fun search(query: String, missedOnly: Boolean = false, limit: Int = 20): List<Contact> {
        val conditions = mutableListOf<String>()
        val args       = mutableListOf<String>()

        if (query.isNotBlank()) {
            val like = "%$query%"
            conditions.add(
                "(${CallLog.Calls.CACHED_NAME} LIKE ? OR ${CallLog.Calls.NUMBER} LIKE ?)"
            )
            args.add(like); args.add(like)
        }
        if (missedOnly) {
            conditions.add("${CallLog.Calls.TYPE} = ${CallLog.Calls.MISSED_TYPE}")
        }

        val selection = conditions.joinToString(" AND ").ifEmpty { null }

        val cursor = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_PHOTO_URI
                ),
                selection,
                args.toTypedArray().ifEmpty { null },
                "${CallLog.Calls.DATE} DESC"
            )
        } catch (e: SecurityException) {
            null
        } ?: return emptyList()

        val recents     = mutableListOf<Contact>()
        val seenNumbers = mutableSetOf<String>()

        cursor.use {
            val nameCol  = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numCol   = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val dateCol  = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val typeCol  = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val photoCol = it.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)

            while (it.moveToNext() && recents.size < limit) {
                val number = it.getString(numCol).orEmpty().trim()
                if (number.isEmpty() || number in seenNumbers) continue
                seenNumbers.add(number)

                val cachedName  = it.getString(nameCol).orEmpty().trim()
                val cachedPhoto = if (photoCol >= 0) it.getString(photoCol) else null

                // Always do a live lookup to resolve contact ID (and name/photo if missing).
                val (lookupName, lookupPhoto, lookupId) = phoneLookup(number)
                val resolvedName  = cachedName.ifEmpty { lookupName }
                val resolvedPhoto = cachedPhoto ?: lookupPhoto

                recents.add(
                    Contact(
                        id           = lookupId,
                        name         = resolvedName ?: number,
                        number       = number,
                        isRecent     = true,
                        lastCallTime = it.getLong(dateCol),
                        callType     = it.getInt(typeCol),
                        photoUri     = resolvedPhoto
                    )
                )
            }
        }

        // Apply fuzzy re-ranking when a query is present
        return if (query.isBlank()) recents else FuzzySearch.rank(query, recents)
    }

    /** All individual call log entries for a specific number (no deduplication). */
    fun getHistoryForNumber(number: String, limit: Int = 100): List<Contact> {
        if (number.isBlank()) return emptyList()
        val cursor = try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.DATE,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.CACHED_PHOTO_URI
                ),
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number),
                "${CallLog.Calls.DATE} DESC"
            )
        } catch (e: SecurityException) { null } ?: return emptyList()

        val history = mutableListOf<Contact>()
        cursor.use {
            val nameCol  = it.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numCol   = it.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val dateCol  = it.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val typeCol  = it.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val photoCol = it.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
            while (it.moveToNext() && history.size < limit) {
                val num        = it.getString(numCol).orEmpty().trim()
                val cachedName = it.getString(nameCol).orEmpty().trim()
                val cachedPhoto = if (photoCol >= 0) it.getString(photoCol) else null
                val (lookupName, lookupPhoto, lookupId) = phoneLookup(num)
                val resolvedName  = cachedName.ifEmpty { lookupName }
                val resolvedPhoto = cachedPhoto ?: lookupPhoto
                history.add(Contact(
                    id           = lookupId,
                    name         = resolvedName ?: num,
                    number       = num,
                    isRecent     = true,
                    lastCallTime = it.getLong(dateCol),
                    callType     = it.getInt(typeCol),
                    photoUri     = resolvedPhoto
                ))
            }
        }
        return history
    }

    /** Deletes all call log entries for [number]. Returns the number of rows deleted. */
    fun deleteByNumber(number: String): Int {
        if (number.isBlank()) return 0
        return try {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ?",
                arrayOf(number)
            ) ?: 0
        } catch (_: Exception) { 0 }
    }

    /** Returns (displayName, photoThumbnailUri, contactId) from ContactsContract for a given number. */
    private fun phoneLookup(number: String): Triple<String?, String?, Long> {
        if (number.isBlank()) return Triple(null, null, 0L)
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(number)
            )
            context.contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                    ContactsContract.PhoneLookup.CONTACT_ID
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) Triple(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getLong(2)
                )
                else Triple(null, null, 0L)
            } ?: Triple(null, null, 0L)
        } catch (_: Exception) {
            Triple(null, null, 0L)
        }
    }
}
