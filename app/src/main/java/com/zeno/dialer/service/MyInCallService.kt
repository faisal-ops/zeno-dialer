package com.zeno.dialer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.telecom.DisconnectCause
import android.telecom.Call
import android.telecom.InCallService
import com.zeno.dialer.CallStateHolder
import com.zeno.dialer.InCallActivity
import com.zeno.dialer.MainActivity
import com.zeno.dialer.R

// v2 suffix so the new IMPORTANCE_LOW channel replaces the old IMPORTANCE_HIGH one.
private const val CHANNEL_ID      = "zeno_active_call_v2"
private const val NOTIFICATION_ID = 1001
private const val MISSED_CHANNEL_ID = "zeno_missed_calls_v1"
private const val MISSED_NOTIFICATION_ID = 1002

class MyInCallService : InCallService() {

    companion object {
        /** Live reference to the running service — null when no call is active. */
        @Volatile var instance: MyInCallService? = null
    }

    private lateinit var notifManager: NotificationManager
    private val lastStateByCall = mutableMapOf<Call, Int>()

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            lastStateByCall[call] = state
            CallStateHolder.update(call, this@MyInCallService)
            updateNotification()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) {
            CallStateHolder.update(call, this@MyInCallService)
            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        notifManager = getSystemService(NotificationManager::class.java)
        notifManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Active call",
                NotificationManager.IMPORTANCE_LOW    // silent — never pops up as heads-up
            ).apply {
                description = "Shows the ongoing call and quick controls"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
        )
        notifManager.createNotificationChannel(
            NotificationChannel(
                MISSED_CHANNEL_ID,
                "Missed calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows missed and rejected incoming calls"
                setShowBadge(true)
            }
        )
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        lastStateByCall[call] = call.state
        CallStateHolder.update(call, this)
        call.registerCallback(callCallback)
        updateNotification()

        startActivity(
            Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    // ── Audio controls (called from InCallActivity) ───────────────────────────

    fun applyMute(muted: Boolean)    = setMuted(muted)
    fun applySpeaker(on: Boolean)    = setAudioRoute(
        if (on) android.telecom.CallAudioState.ROUTE_SPEAKER
        else    android.telecom.CallAudioState.ROUTE_EARPIECE
    )
    fun isMuted():     Boolean = callAudioState?.isMuted ?: false
    fun isSpeakerOn(): Boolean = callAudioState?.route == android.telecom.CallAudioState.ROUTE_SPEAKER

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        maybeShowMissedCallNotification(call)
        lastStateByCall.remove(call)
        CallStateHolder.remove(call)
        if (calls.isEmpty()) {
            CallStateHolder.clear()
            notifManager.cancel(NOTIFICATION_ID)
        } else {
            updateNotification()
        }
    }

    private fun maybeShowMissedCallNotification(call: Call) {
        val wasIncoming = call.details.callDirection == Call.Details.DIRECTION_INCOMING
        if (!wasIncoming) return

        val lastState = lastStateByCall[call]
        val disconnectCode = call.details.disconnectCause?.code
        val shouldShow = lastState == Call.STATE_RINGING || when (disconnectCode) {
            DisconnectCause.MISSED,
            DisconnectCause.REJECTED,
            DisconnectCause.CANCELED -> true
            else -> false
        }
        if (!shouldShow) return

        val name = call.details.contactDisplayName?.toString()
            ?.takeIf { it.isNotBlank() }
            ?: "Missed call"
        val number = call.details.handle?.schemeSpecificPart ?: ""
        val contentText = if (number.isNotBlank()) number else "Tap to open dialer"

        val openDialerIntent = PendingIntent.getActivity(
            this, 2,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, MISSED_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setContentTitle(name)
            .setContentText(contentText)
            .setContentIntent(openDialerIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_MISSED_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        notifManager.notify(MISSED_NOTIFICATION_ID, notification)
    }

    // ── Notification ─────────────────────────────────────────────────────────

    private fun updateNotification() {
        val info = CallStateHolder.info.value ?: return

        // Only suppress the notification for RINGING (incoming) — InCallActivity is already
        // on screen showing the answer/reject UI. For outgoing calls (DIALING, CONNECTING)
        // and active/held calls, show the notification so the user can return from any app.
        if (info.state == Call.STATE_RINGING) {
            notifManager.cancel(NOTIFICATION_ID)
            return
        }

        val stateText = when (info.state) {
            Call.STATE_ACTIVE     -> "Active call"
            Call.STATE_HOLDING    -> "Call on hold"
            Call.STATE_DIALING    -> "Calling…"
            Call.STATE_CONNECTING -> "Connecting…"
            else                  -> "Call"
        }

        // Tap notification → open InCallActivity
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, InCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "End call" action → broadcast to CallActionReceiver
        val hangupIntent = PendingIntent.getBroadcast(
            this, 1,
            Intent(CallActionReceiver.ACTION_HANGUP).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_call)
            .setContentTitle(info.displayName)
            .setContentText(stateText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)               // don't re-alert on every state update
            .setCategory(Notification.CATEGORY_CALL)
            .setVisibility(Notification.VISIBILITY_PUBLIC)  // show on lock screen
            .setColor(0xFF4CAF50.toInt())         // green tint
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.drawable.ic_notification_call),
                    "End call",
                    hangupIntent
                ).build()
            )
            .build()

        notifManager.notify(NOTIFICATION_ID, notification)
    }
}
