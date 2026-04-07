package com.zeno.dialer

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton bridge between [com.zeno.dialer.service.MyInCallService]
 * (which owns the live [Call] objects from Telecom) and [InCallActivity].
 *
 * Supports up to two simultaneous calls:
 *   - [info]       : foreground (active / ringing) call — drives the primary UI.
 *   - [secondCall] : background / held call — shown as a compact banner.
 *
 * Thread-safe: MutableStateFlow updates are always safe from any thread.
 */
data class ActiveCallInfo(
    val call: Call,
    val state: Int,
    val displayName: String,
    val number: String,
    val photoUri: String? = null
)

object CallStateHolder {

    private val _info       = MutableStateFlow<ActiveCallInfo?>(null)
    private val _secondCall = MutableStateFlow<ActiveCallInfo?>(null)

    /** Foreground / primary call. */
    val info: StateFlow<ActiveCallInfo?> = _info.asStateFlow()

    /** Background / held call (non-null when there are two simultaneous calls). */
    val secondCall: StateFlow<ActiveCallInfo?> = _secondCall.asStateFlow()

    // ── Update / remove ──────────────────────────────────────────────────────

    /**
     * Called by [MyInCallService] whenever call state or details change.
     * Pass a non-null [context] on the first call so a photo lookup can be performed;
     * subsequent updates reuse the already-resolved photo.
     */
    fun update(call: Call, context: Context? = null) {
        val number = call.details.handle?.schemeSpecificPart.orEmpty()

        // Try system call details first, then fall back to contacts DB lookup
        val telecomName = listOfNotNull(
            call.details.callerDisplayName?.takeIf { it.isNotBlank() },
            call.details.contactDisplayName?.takeIf { it.isNotBlank() }
        ).firstOrNull()

        val primary   = _info.value
        val secondary = _secondCall.value

        when {
            // Update existing primary call in-place, reusing photo.
            primary?.call === call -> {
                val lookup = if (context != null && primary.photoUri == null)
                    lookupContact(context, number) else null
                val resolvedName = telecomName
                    ?: lookup?.name?.takeIf { it.isNotBlank() }
                    ?: primary.displayName
                _info.value = primary.copy(
                    state       = call.state,
                    displayName = resolvedName,
                    photoUri    = primary.photoUri ?: lookup?.photoUri
                )
            }
            // Update existing secondary call in-place.
            secondary?.call === call -> {
                val lookup = if (context != null && secondary.photoUri == null)
                    lookupContact(context, number) else null
                val resolvedName = telecomName
                    ?: lookup?.name?.takeIf { it.isNotBlank() }
                    ?: secondary.displayName
                _secondCall.value = secondary.copy(
                    state       = call.state,
                    displayName = resolvedName,
                    photoUri    = secondary.photoUri ?: lookup?.photoUri
                )
            }
            // New call — no primary yet.
            primary == null -> {
                val lookup = if (context != null) lookupContact(context, number) else null
                val name = telecomName ?: lookup?.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown" }
                _info.value = ActiveCallInfo(call, call.state, name, number, lookup?.photoUri)
            }
            // Second new call — slot it as secondary.
            secondary == null -> {
                val lookup = if (context != null) lookupContact(context, number) else null
                val name = telecomName ?: lookup?.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown" }
                _secondCall.value = ActiveCallInfo(call, call.state, name, number, lookup?.photoUri)
            }
            // Third call — replace primary (edge case; most carriers don't support 3-way add).
            else -> {
                val lookup = if (context != null) lookupContact(context, number) else null
                val name = telecomName ?: lookup?.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown" }
                _info.value = ActiveCallInfo(call, call.state, name, number, lookup?.photoUri)
            }
        }
    }

    /** Remove a specific call (called from [MyInCallService.onCallRemoved]). */
    fun remove(call: Call) {
        when {
            _info.value?.call === call -> {
                // Promote secondary to primary when foreground call ends.
                _info.value       = _secondCall.value
                _secondCall.value = null
            }
            _secondCall.value?.call === call -> {
                _secondCall.value = null
            }
        }
    }

    fun clear() {
        _info.value       = null
        _secondCall.value = null
    }

    // ── Multi-call actions ───────────────────────────────────────────────────

    /**
     * Swap foreground and background calls.
     * Puts the current primary on hold and resumes the secondary.
     */
    fun swap() {
        val p = _info.value ?: return
        val s = _secondCall.value ?: return
        p.call.hold()
        s.call.unhold()
        _info.value       = s
        _secondCall.value = p
    }

    // ── Primary call actions ─────────────────────────────────────────────────

    fun answer()  { _info.value?.call?.answer(0 /* VideoProfile.STATE_AUDIO_ONLY */) }
    fun reject()  { _info.value?.call?.reject(false, null) }
    fun hangup()  { _info.value?.call?.disconnect() }
    fun hold()    { _info.value?.call?.hold() }
    fun unhold()  { _info.value?.call?.unhold() }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Lookup contact name + photo from the system contacts by phone number. */
    private data class ContactLookup(val name: String?, val photoUri: String?)

    private fun lookupContact(context: Context, number: String): ContactLookup {
        if (number.isBlank()) return ContactLookup(null, null)
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
                    ContactsContract.PhoneLookup.PHOTO_URI
                ),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    ContactLookup(
                        name = cursor.getString(0),
                        photoUri = cursor.getString(1) ?: cursor.getString(2)
                    )
                } else ContactLookup(null, null)
            } ?: ContactLookup(null, null)
        } catch (_: Exception) { ContactLookup(null, null) }
    }

    @Suppress("DEPRECATION")
    private fun lookupPhoto(context: Context, number: String): String? =
        lookupContact(context, number).photoUri
}
