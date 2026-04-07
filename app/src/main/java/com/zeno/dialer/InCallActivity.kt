package com.zeno.dialer

import android.os.Bundle
import android.content.Intent
import android.Manifest
import android.telephony.SmsManager
import android.telecom.Call
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeno.dialer.ui.Accent
import com.zeno.dialer.ui.AccentMuted
import com.zeno.dialer.ui.BgElevated
import com.zeno.dialer.ui.BgSurface
import com.zeno.dialer.ui.Border
import com.zeno.dialer.ui.ContactAvatar
import com.zeno.dialer.ui.TextPrimary
import com.zeno.dialer.ui.TextSecondary
import com.zeno.dialer.ui.theme.DialerTheme
class InCallActivity : ComponentActivity() {

    private val vm: InCallViewModel by viewModels()
    private var pendingQuickReply: String? = null

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "SMS permission required for quick reply", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        val msg = pendingQuickReply ?: return@registerForActivityResult
        sendQuickReply(msg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        lifecycleScope.launch {
            CallStateHolder.info.collect { info ->
                val s = info?.state
                // Wait for full DISCONNECTED (not DISCONNECTING) to avoid premature finish.
                // info == null means all calls were cleared (CallStateHolder.clear() was called).
                if (info == null || s == Call.STATE_DISCONNECTED) {
                    // Just close the in-call screen — the system will return
                    // the user to whatever app they were using before the call.
                    finish()
                }
            }
        }

        setContent {
            DialerTheme {
                InCallScreen(
                    vm = vm,
                    onQuickReply = { message ->
                        pendingQuickReply = message
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            sendQuickReply(message)
                        } else {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    }
                )
            }
        }
    }

    // Register hardware toolbar button callbacks while this activity is foreground.
    // This ensures End/Call keys work even if MainActivity was never started or was destroyed.
    override fun onResume() {
        super.onResume()
        com.zeno.dialer.service.ToolbarButtonHandler.onCallPressed = {
            val s = CallStateHolder.info.value?.state
            if (s == Call.STATE_RINGING) CallStateHolder.answer()
        }
        com.zeno.dialer.service.ToolbarButtonHandler.onEndPressed = {
            val s = CallStateHolder.info.value?.state
            if (s == Call.STATE_RINGING) CallStateHolder.reject() else CallStateHolder.hangup()
        }
    }

    override fun onStop() {
        super.onStop()
        // Clear so MainActivity can re-register its own callbacks when it resumes.
        com.zeno.dialer.service.ToolbarButtonHandler.onCallPressed = null
        com.zeno.dialer.service.ToolbarButtonHandler.onEndPressed = null
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val state = CallStateHolder.info.value?.state
            when (event.keyCode) {
                KeyEvent.KEYCODE_CALL,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (state == Call.STATE_RINGING) CallStateHolder.answer()
                    return true
                }
                KeyEvent.KEYCODE_ENDCALL -> {
                    // BB toolbar red button — always end/reject.
                    if (state == Call.STATE_RINGING) CallStateHolder.reject()
                    else CallStateHolder.hangup()
                    return true
                }
                // KEYCODE_BACK is intentionally NOT handled here.
                // It should navigate back (Back key ≠ End call).
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun sendQuickReply(message: String) {
        val number = CallStateHolder.info.value?.number.orEmpty()
        if (number.isBlank()) return
        try {
            SmsManager.getDefault().sendTextMessage(number, null, message, null, null)
            CallStateHolder.reject()
            Toast.makeText(this, "Message sent", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show()
        }
    }
}

// ── In-call Compose UI ──────────────────────────────────────────────────────

@Composable
private fun InCallScreen(vm: InCallViewModel, onQuickReply: (String) -> Unit) {
    // Ensures we can close immediately on button taps (without waiting for state propagation).
    // This reduces "stuck on Call Ended" flashes.
    val info       by CallStateHolder.info.collectAsStateWithLifecycle()
    val secondCall by CallStateHolder.secondCall.collectAsStateWithLifecycle()
    val elapsed    by vm.elapsedSeconds.collectAsStateWithLifecycle()

    LaunchedEffect(info?.state) {
        info?.state?.let { vm.onStateChanged(it) }
    }

    val state = info?.state ?: Call.STATE_DISCONNECTED
    if (state == Call.STATE_DISCONNECTED || state == Call.STATE_DISCONNECTING) return

    val displayName = info?.displayName ?: "Unknown"
    val number = info?.number.orEmpty()

    val context = LocalContext.current

    // Audio controls via InCallService (the correct Telecom API for default dialers).
    val svc = com.zeno.dialer.service.MyInCallService.instance
    var micMuted  by remember { mutableStateOf(svc?.isMuted()     ?: false) }
    var speakerOn by remember { mutableStateOf(svc?.isSpeakerOn() ?: false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Top content: avatar + name + number ──────────────────────────
        Spacer(Modifier.height(48.dp))

        val pulseTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by pulseTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200),
                repeatMode = RepeatMode.Reverse
            ),
            label = "avatar_pulse"
        )
        val shouldPulse = state == Call.STATE_RINGING

        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(if (shouldPulse) pulseScale else 1f),
            contentAlignment = Alignment.Center
        ) {
            ContactAvatar(
                name     = displayName,
                photoUri = info?.photoUri,
                size     = 80
            )
        }

        Spacer(Modifier.height(14.dp))

        Text(
            text = displayName,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        if (number.isNotBlank() && number != displayName) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = number,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state == Call.STATE_ACTIVE) {
            Text(
                text = elapsed.toCallDuration(),
                color = Accent,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center
            )
        } else {
            Text(
                text = stateLabel(state),
                color = stateColor(state),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        }

        // ── Second call banner (shown when two calls are active) ─────────
        if (secondCall != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryCallBanner(
                info     = secondCall!!,
                onSwitch = { CallStateHolder.swap() }
            )
        }

        Spacer(Modifier.weight(1f))

        // ── Bottom action area ───────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (state) {
                Call.STATE_RINGING -> {
                    var showQuickReplies by remember { mutableStateOf(false) }

                    if (!showQuickReplies) {
                        // Message button
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(BgElevated)
                                .clickable { showQuickReplies = true }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Message,
                                contentDescription = "Message",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Message",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(32.dp))

                        // Decline / Answer
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CallActionButton(
                                    icon = Icons.Default.CallEnd,
                                    backgroundColor = Color(0xFFCC4444),
                                    iconColor = Color.White,
                                    onClick = { CallStateHolder.reject() }
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Decline",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CallActionButton(
                                    icon = Icons.Default.Call,
                                    backgroundColor = AccentMuted,
                                    iconColor = Color.White,
                                    onClick = { CallStateHolder.answer() }
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "Answer",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    } else {
                        // Quick replies list
                        val prefs = context.getSharedPreferences("zeno_settings", android.content.Context.MODE_PRIVATE)
                        val quickReplies = (0..3).map { i ->
                            prefs.getString("quick_response_$i", null) ?: listOf(
                                "Can't talk now. What's up?",
                                "I'll call you right back.",
                                "I'll call you later.",
                                "Can't talk now. Call me later?"
                            )[i]
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                        ) {
                            quickReplies.forEach { msg ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onQuickReply(msg) }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Message,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = msg,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CallActionButton(
                                        icon = Icons.Default.CallEnd,
                                        backgroundColor = Color(0xFFCC4444),
                                        iconColor = Color.White,
                                        onClick = { CallStateHolder.reject() }
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text("Decline", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CallActionButton(
                                        icon = Icons.Default.Call,
                                        backgroundColor = AccentMuted,
                                        iconColor = Color.White,
                                        onClick = { CallStateHolder.answer() }
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text("Answer", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                else -> {
                    // ── Row 1: Mute, Keypad, Speaker ─────────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmallActionButton(
                            icon    = if (micMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            label   = "Mute",
                            color   = if (micMuted) Color(0xFFFFAA00) else TextSecondary,
                            bgColor = if (micMuted) Color(0xFF332200) else BgElevated,
                            onClick = {
                                micMuted = !micMuted
                                com.zeno.dialer.service.MyInCallService.instance?.applyMute(micMuted)
                            }
                        )
                        SmallActionButton(
                            icon = Icons.Default.Dialpad,
                            label = "Keypad",
                            color = TextSecondary,
                            onClick = {
                                // Keep InCallActivity in back stack — user returns via Back key.
                                context.startActivity(
                                    Intent(context, MainActivity::class.java).apply {
                                        putExtra("open_keypad", true)
                                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    }
                                )
                            }
                        )
                        SmallActionButton(
                            icon    = Icons.AutoMirrored.Filled.VolumeUp,
                            label   = "Speaker",
                            color   = if (speakerOn) Color(0xFF66CCFF) else TextSecondary,
                            bgColor = if (speakerOn) Color(0xFF1A2A3A) else BgElevated,
                            onClick = {
                                speakerOn = !speakerOn
                                com.zeno.dialer.service.MyInCallService.instance?.applySpeaker(speakerOn)
                            }
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // ── Row 2: Add Call, End Call, Hold ───────────────────
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 32.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SmallActionButton(
                            icon = Icons.Default.PersonAdd,
                            label = "Add Call",
                            color = TextSecondary,
                            onClick = {
                                // Hold current call, open dialer to place a second call.
                                // InCallActivity stays in back stack — user returns via Back key.
                                CallStateHolder.hold()
                                context.startActivity(
                                    Intent(context, MainActivity::class.java).apply {
                                        putExtra("open_keypad", true)
                                        flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                                    }
                                )
                            }
                        )
                        SmallActionButton(
                            icon = Icons.Default.CallEnd,
                            label = "End Call",
                            color = MaterialTheme.colorScheme.error,
                            bgColor = Color(0xFF3A1A1A),
                            onClick = { CallStateHolder.hangup() }
                        )
                        SmallActionButton(
                            icon    = Icons.Default.Pause,
                            label   = if (state == Call.STATE_HOLDING) "Unhold" else "Hold",
                            color   = if (state == Call.STATE_HOLDING) Color(0xFFFFDD00) else TextSecondary,
                            bgColor = if (state == Call.STATE_HOLDING) Color(0xFF2A2200) else BgElevated,
                            onClick = {
                                if (state == Call.STATE_HOLDING) CallStateHolder.unhold()
                                else CallStateHolder.hold()
                            }
                        )
                    }
                }
            }
        }
    }
}

// ── Secondary call banner ─────────────────────────────────────────────────────

@Composable
private fun SecondaryCallBanner(info: ActiveCallInfo, onSwitch: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .border(1.dp, Border, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .background(BgSurface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text       = info.displayName,
                color      = MaterialTheme.colorScheme.onSurface,
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis
            )
            Text(
                text     = stateLabel(info.state),
                color    = stateColor(info.state),
                fontSize = 11.sp
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(BgElevated)
                .clickable { onSwitch() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.Default.SwapCalls,
                contentDescription = "Switch call",
                tint               = Accent,
                modifier           = Modifier.size(18.dp)
            )
        }
    }
}

// ── Call action button (large circle) ────────────────────────────────────────

@Composable
private fun CallActionButton(
    icon: ImageVector,
    backgroundColor: Color,
    iconColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(26.dp)
        )
    }
}

// ── Small action button (mid-call controls) ──────────────────────────────────

@Composable
private fun SmallActionButton(
    icon: ImageVector,
    label: String,
    color: Color,
    bgColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val resolvedBg = if (bgColor == Color.Unspecified) BgElevated else bgColor
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(64.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(resolvedBg)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun stateLabel(state: Int): String = when (state) {
    Call.STATE_RINGING       -> "Incoming call"
    Call.STATE_DIALING       -> "Calling..."
    Call.STATE_CONNECTING    -> "Connecting..."
    Call.STATE_ACTIVE        -> "Active"
    Call.STATE_HOLDING       -> "On Hold"
    Call.STATE_DISCONNECTING -> "Ending..."
    Call.STATE_DISCONNECTED  -> "Call Ended"
    else -> ""
}

private fun stateColor(state: Int): Color = when (state) {
    Call.STATE_RINGING                          -> Color(0xFFD4A033)
    Call.STATE_ACTIVE                           -> Color(0xFF6BCB77)
    Call.STATE_HOLDING                          -> Color(0xFF8E8E93)
    Call.STATE_DISCONNECTED,
    Call.STATE_DISCONNECTING                    -> Color(0xFFCC4444)
    else                                        -> Color(0xFF5A9EC7)
}
