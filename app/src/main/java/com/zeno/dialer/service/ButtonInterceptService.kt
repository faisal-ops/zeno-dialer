package com.zeno.dialer.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telecom.TelecomManager
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * AccessibilityService that intercepts the BB toolbar Call and End hardware
 * buttons. These keys (KEYCODE_CALL / KEYCODE_ENDCALL) are normally consumed
 * by the system before reaching the activity — this service catches them first.
 *
 * The user must enable this service in Settings → Accessibility.
 */
class ButtonInterceptService : AccessibilityService() {

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_CALL -> {
                    val callback = ToolbarButtonHandler.onCallPressed
                    if (callback != null) {
                        callback.invoke()
                    } else {
                        openDefaultDialer()
                    }
                    return true
                }
                KeyEvent.KEYCODE_ENDCALL -> {
                    val callback = ToolbarButtonHandler.onEndPressed
                    if (callback != null) {
                        // A callback is registered — either InCallActivity or MainActivity is in foreground
                        callback.invoke()
                    } else {
                        // No callback — app is in background. Only end call if setting allows it.
                        val prefs = getSharedPreferences("zeno_settings", Context.MODE_PRIVATE)
                        if (prefs.getBoolean("end_call_from_any_app", false)) {
                            com.zeno.dialer.CallStateHolder.hangup()
                        }
                    }
                    return true
                }
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used — we only need key event interception
    }

    override fun onInterrupt() {}

    private fun openDefaultDialer() {
        val defaultDialerPkg = getSystemService(TelecomManager::class.java)?.defaultDialerPackage
        val fallbackIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:")).apply {
            if (!defaultDialerPkg.isNullOrBlank()) setPackage(defaultDialerPkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(fallbackIntent)
        }
    }

    companion object {
        const val EXTRA_CALL_BUTTON_PRESSED = "call_button_pressed"
    }
}
