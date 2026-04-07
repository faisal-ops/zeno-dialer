package com.zeno.dialer

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.zeno.dialer.ui.DialerScreen
import com.zeno.dialer.ui.DialerTab
import com.zeno.dialer.ui.theme.DialerTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: DialerViewModel by viewModels()
    private lateinit var keyHandler: KeyHandler
    private val showAccessibilityPrompt = mutableStateOf(false)
    private val requestedTab = mutableStateOf<DialerTab?>(null)
    // True while the system role-picker is on screen — prevents re-launching on the
    // onResume that fires right after the picker is dismissed.
    private var roleRequestInFlight = false
    // True if the user explicitly cancelled/declined this session. We still show a
    // banner (see below) but don't force the system picker again until next launch.
    private var roleDeclinedThisSession = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        viewModel.clearQuery()
        if (grants[android.Manifest.permission.READ_CALL_LOG] == true) {
            viewModel.onPermissionsReady()
        }
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        roleRequestInFlight = false
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            // Role granted — if this is the first setup, close the app
            val prefs = getSharedPreferences("zeno", MODE_PRIVATE)
            if (!prefs.getBoolean("setup_complete", false)) {
                prefs.edit().putBoolean("setup_complete", true).apply()
                finish()
                return@registerForActivityResult
            }
        } else {
            // User declined — don't spam the picker again this session
            roleDeclinedThisSession = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        keyHandler = KeyHandler(
            viewModel = viewModel,
            onFinish = { finish() }
        )

        requestRequiredPermissions()
        observeCallEvents()
        handleDialIntent(intent)
        if (intent?.getBooleanExtra("open_keypad", false) == true) {
            requestedTab.value = DialerTab.KEYPAD
        }
        registerToolbarButtons()

        setContent {
            DialerTheme {
                DialerScreen(viewModel = viewModel, requestedTab = requestedTab.value)
                DefaultDialerOverlay()
                AccessibilityPromptDialog()
            }
        }
    }

    // Clear callbacks when Activity goes to background so that
    // ButtonInterceptService falls back to openDefaultDialer() which
    // properly brings the Activity to the foreground via an Intent.
    override fun onStop() {
        super.onStop()
        com.zeno.dialer.service.ToolbarButtonHandler.onCallPressed = null
        com.zeno.dialer.service.ToolbarButtonHandler.onEndPressed = null
    }

    // Re-register toolbar button callbacks every time MainActivity comes to foreground.
    // This restores them after onStop cleared them.
    override fun onResume() {
        super.onResume()
        viewModel.onPermissionsReady()

        val prefs = getSharedPreferences("zeno", MODE_PRIVATE)

        // First launch flow: permissions → accessibility prompt → default dialer.
        // Only show accessibility prompt after all permissions are granted.
        val allPermissionsGranted = REQUIRED_PERMISSIONS.all {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (allPermissionsGranted
            && !isAccessibilityServiceEnabled()
            && !prefs.getBoolean("accessibility_prompted", false)
            && !showAccessibilityPrompt.value
        ) {
            prefs.edit().putBoolean("accessibility_prompted", true).apply()
            promptAccessibilityService()
            // Don't launch role picker yet — wait for dialog dismissal
        } else if (allPermissionsGranted) {
            launchRolePickerIfNeeded()
        }
        com.zeno.dialer.service.ToolbarButtonHandler.onCallPressed = {
            runOnUiThread { dialOrOpenKeypad() }
        }
        com.zeno.dialer.service.ToolbarButtonHandler.onEndPressed = {
            runOnUiThread {
                val callInfo = CallStateHolder.info.value
                if (callInfo != null &&
                    callInfo.state != android.telecom.Call.STATE_DISCONNECTED &&
                    callInfo.state != android.telecom.Call.STATE_DISCONNECTING
                ) {
                    CallStateHolder.hangup()
                } else {
                    // No active call — close the app (same as physical BACK)
                    finish()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("open_keypad", false)) {
            // Force a change so DialerScreen's LaunchedEffect re-runs even if it's already on Keypad.
            requestedTab.value = null
            requestedTab.value = DialerTab.KEYPAD
        }
        // Call button pressed from background via AccessibilityService
        if (intent.getBooleanExtra(
                com.zeno.dialer.service.ButtonInterceptService.EXTRA_CALL_BUTTON_PRESSED, false
            )) {
            intent.removeExtra(com.zeno.dialer.service.ButtonInterceptService.EXTRA_CALL_BUTTON_PRESSED)
            // Open-only behavior for background Call key press:
            // foreground the dialer without auto-dialing stale selected/top results.
            return
        }
        handleDialIntent(intent)
    }

    /**
     * Handles DIAL / CALL intents — triggered by the system when the BB
     * toolbar green Call button is pressed (the system re-launches/foregrounds
     * the default dialer with a DIAL intent rather than sending a key event).
     *
     * If the intent carries a tel: URI, dial that number.
     * If not (bare DIAL action), dial whatever is currently on screen.
     */
    private fun handleDialIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        if (action == Intent.ACTION_DIAL || action == Intent.ACTION_CALL ||
            action == Intent.ACTION_VIEW
        ) {
            val number = intent.data?.schemeSpecificPart
            if (!number.isNullOrBlank()) {
                placeCall(number)
            } else if (action == Intent.ACTION_DIAL) {
                // Bare DIAL intent (no number) — treat like call button press
                dialOrOpenKeypad()
            }
        }
    }

    /** Dials the selected result if on Home tab with results, otherwise opens keypad. */
    private fun dialOrOpenKeypad() {
        val state = viewModel.uiState.value
        when {
            state.query.isNotBlank() -> viewModel.callSelected()
            viewModel.currentTabIndex == 1 && state.results.isNotEmpty() ->
                viewModel.callSelected()
            viewModel.currentTabIndex == 0 && state.favoriteSuggestions.isNotEmpty() ->
                viewModel.callFavoriteSuggestionSelected()
            else -> viewModel.setCurrentTab(2)
        }
    }

    // ── Call event observer ──────────────────────────────────────────────────

    private fun observeCallEvents() {
        lifecycleScope.launch {
            viewModel.callEvent.collect { number ->
                placeCall(number)
            }
        }
    }

    private fun placeCall(number: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Phone permission required", Toast.LENGTH_SHORT).show()
            requestRequiredPermissions()
            return
        }
        try {
            // TelecomManager.placeCall is the correct API for default dialers.
            // It routes through the Telecom stack directly, bypassing the intent
            // dispatch layer that caused "call not sent" with ACTION_CALL.
            val telecom = getSystemService(TelecomManager::class.java)
            val uri = Uri.fromParts("tel", number, null)
            telecom.placeCall(uri, android.os.Bundle.EMPTY)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not place call: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── Physical keyboard / D-pad / toolbar key routing ──────────────────────
    // dispatchKeyEvent fires BEFORE the Compose view hierarchy, so we always
    // get first pick on every key — BACK, CALL, ENDCALL, DPAD, letters, etc.

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {

            if (viewModel.isOnKeypad) {
                val kcm = event.device?.keyCharacterMap
                    ?: KeyCharacterMap.load(event.deviceId)
                val numberChar = kcm.getNumber(event.keyCode)
                if (keyHandler.handleKeypad(event.keyCode, numberChar.code)) {
                    return true
                }
            }

            val altChar = event.getUnicodeChar(KeyEvent.META_ALT_ON)
                .takeIf { it > 0 }
                ?: event.getUnicodeChar(KeyEvent.META_ALT_LEFT_ON).takeIf { it > 0 }
                ?: 0
            if (keyHandler.handle(event.keyCode, event.unicodeChar, altChar, event.repeatCount)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ── Toolbar button callbacks (via AccessibilityService) ────────────────

    private fun registerToolbarButtons() {
        com.zeno.dialer.service.ToolbarButtonHandler.onCallPressed = {
            runOnUiThread { dialOrOpenKeypad() }
        }
        com.zeno.dialer.service.ToolbarButtonHandler.onEndPressed = {
            runOnUiThread {
                val callInfo = CallStateHolder.info.value
                if (
                    callInfo != null &&
                    callInfo.state != android.telecom.Call.STATE_DISCONNECTED &&
                    callInfo.state != android.telecom.Call.STATE_DISCONNECTING
                ) {
                    CallStateHolder.hangup()
                } else {
                    finish()
                }
            }
        }

        // Accessibility prompt is now shown before the default dialer role request
        // in onResume(), so it's not needed here.
    }

    private fun promptAccessibilityService() {
        showAccessibilityPrompt.value = true
    }

    private fun launchRolePickerIfNeeded() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
            && !roleRequestInFlight
            && !roleDeclinedThisSession
        ) {
            roleRequestInFlight = true
            roleRequestLauncher.launch(
                roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            )
        }
    }

    @Composable
    private fun AccessibilityPromptDialog() {
        if (!showAccessibilityPrompt.value) return

        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showAccessibilityPrompt.value = false
                launchRolePickerIfNeeded()
            },
            icon = {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                androidx.compose.material3.Text(
                    text = "Enable toolbar buttons",
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
                )
            },
            text = {
                androidx.compose.material3.Text(
                    text = "Allow Zeno Dialer to use the Call and End " +
                           "hardware buttons on your keyboard toolbar.\n\n" +
                           "Find \"Zeno Dialer\" in the list and enable it.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showAccessibilityPrompt.value = false
                    startActivity(
                        Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                }) {
                    androidx.compose.material3.Text("Open Settings")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showAccessibilityPrompt.value = false
                    launchRolePickerIfNeeded()
                }) {
                    androidx.compose.material3.Text("Not now")
                }
            }
        )
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("$packageName/") &&
               enabledServices.contains("ButtonInterceptService")
    }

    override fun onDestroy() {
        super.onDestroy()
        com.zeno.dialer.service.ToolbarButtonHandler.onCallPressed = null
        com.zeno.dialer.service.ToolbarButtonHandler.onEndPressed = null
    }

    // ── Default dialer banner ────────────────────────────────────────────────
    // Shown at the top of the screen when the role was declined this session.
    // Tapping it relaunches the system picker.

    @Composable
    private fun DefaultDialerOverlay() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) return

        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                // Phone icon in circle
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
                            androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Default.Phone,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(36.dp)
                    )
                }

                androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))

                androidx.compose.material3.Text(
                    text = "Set Phone as default",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))

                androidx.compose.material3.Text(
                    text = "Phone can only start & receive calls if it's your default phone app",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                androidx.compose.foundation.layout.Spacer(Modifier.size(32.dp))

                // "Set as default" button
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .background(
                            androidx.compose.material3.MaterialTheme.colorScheme.inverseSurface,
                            androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                        )
                        .clickable {
                            if (!roleRequestInFlight) {
                                roleRequestInFlight = true
                                roleDeclinedThisSession = false
                                roleRequestLauncher.launch(
                                    roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                                )
                            }
                        }
                        .padding(horizontal = 28.dp, vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.Text(
                        text = "Set as default",
                        color = androidx.compose.material3.MaterialTheme.colorScheme.inverseOnSurface,
                        style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // ── Permissions ──────────────────────────────────────────────────────────

    private fun requestRequiredPermissions() {
        val needed = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.CALL_PHONE,
            android.Manifest.permission.READ_CONTACTS,
            android.Manifest.permission.READ_CALL_LOG,
            android.Manifest.permission.WRITE_CALL_LOG,
            android.Manifest.permission.READ_PHONE_STATE,
            android.Manifest.permission.POST_NOTIFICATIONS
        )
    }
}
