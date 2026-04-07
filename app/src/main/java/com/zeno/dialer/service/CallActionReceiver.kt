package com.zeno.dialer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zeno.dialer.CallStateHolder

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_HANGUP -> CallStateHolder.hangup()
        }
    }

    companion object {
        const val ACTION_HANGUP = "com.zeno.dialer.action.HANGUP"
    }
}
