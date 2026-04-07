package com.zeno.dialer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Voicemail
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zeno.dialer.ui.BgPage
import com.zeno.dialer.ui.BgElevated
import com.zeno.dialer.ui.TextPrimary
import com.zeno.dialer.ui.TextSecondary
import com.zeno.dialer.ui.AccentGreen
import com.zeno.dialer.ui.theme.DialerTheme

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DialerTheme {
                SettingsRoot(onBack = { finish() })
            }
        }
    }
}

// ── Constants ────────────────────────────────────────────────────────────────

private const val PREFS_NAME = "zeno_settings"
private const val KEY_FLIP_TO_SILENCE = "flip_to_silence"
private const val KEY_CALLER_ID_ANNOUNCE = "caller_id_announce"
private const val KEY_VISUAL_VOICEMAIL = "visual_voicemail"
private const val KEY_PORTRAIT_MODE = "keep_portrait_mode"
private const val KEY_THEME = "choose_theme"
private const val KEY_SORT_BY = "sort_by"
private const val KEY_NAME_FORMAT = "name_format"
private const val KEY_QUICK_RESPONSE_PREFIX = "quick_response_"
private const val KEY_ASSISTED_DIALING = "assisted_dialing"
private const val KEY_CALLER_SPAM_ID = "see_caller_spam_id"
private const val KEY_FILTER_SPAM = "filter_spam_calls"
private const val KEY_END_CALL_ANYWHERE = "end_call_from_any_app"

private val DEFAULT_QUICK_RESPONSES = listOf(
    "Can't talk now. What's up?",
    "I'll call you right back.",
    "I'll call you later.",
    "Can't talk now. Call me later?"
)

// ── Navigation ───────────────────────────────────────────────────────────────

private enum class SettingsPage {
    MAIN, FLIP_TO_SILENCE, CALLER_ID_ANNOUNCEMENT, VOICEMAIL, CONTACT_RINGTONES,
    CALLING_CARD, QUICK_RESPONSES, DISPLAY_OPTIONS, CALLER_ID_SPAM, ASSISTED_DIALING
}

@Composable
private fun SettingsRoot(onBack: () -> Unit) {
    var currentPage by remember { mutableStateOf(SettingsPage.MAIN) }
    val goMain = { currentPage = SettingsPage.MAIN }

    when (currentPage) {
        SettingsPage.MAIN -> SettingsScreen(onBack = onBack, onNavigate = { currentPage = it })
        SettingsPage.FLIP_TO_SILENCE -> FlipToSilenceScreen(onBack = goMain)
        SettingsPage.CALLER_ID_ANNOUNCEMENT -> CallerIdAnnouncementScreen(onBack = goMain)
        SettingsPage.VOICEMAIL -> VoicemailScreen(onBack = goMain)
        SettingsPage.CONTACT_RINGTONES -> ContactRingtonesScreen(onBack = goMain)
        SettingsPage.CALLING_CARD -> CallingCardScreen(onBack = goMain)
        SettingsPage.QUICK_RESPONSES -> QuickResponsesScreen(onBack = goMain)
        SettingsPage.DISPLAY_OPTIONS -> DisplayOptionsScreen(onBack = goMain)
        SettingsPage.CALLER_ID_SPAM -> CallerIdSpamScreen(onBack = goMain)
        SettingsPage.ASSISTED_DIALING -> AssistedDialingScreen(onBack = goMain)
    }
}

// ── Main settings screen ─────────────────────────────────────────────────────

@Composable
private fun SettingsScreen(onBack: () -> Unit, onNavigate: (SettingsPage) -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(BgPage)
    ) {
        SettingsTopBar(title = "Settings", onBack = onBack)

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Call Assist
            item { SectionHeader("Call Assist") }
            item {
                SettingsNavItem(icon = Icons.Default.Block, title = "Caller ID & spam",
                    onClick = { onNavigate(SettingsPage.CALLER_ID_SPAM) })
            }

            // General
            item { SectionHeader("General") }
            item {
                SettingsNavItem(icon = Icons.Default.Settings, title = "Accessibility",
                    onClick = {
                        launchSafe(context,
                            Intent("android.telecom.action.SHOW_CALL_ACCESSIBILITY_SETTINGS"),
                            fallbackAction = Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    })
            }
            item {
                SettingsNavItem(icon = Icons.Default.Phone, title = "Assisted dialing",
                    onClick = { onNavigate(SettingsPage.ASSISTED_DIALING) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.Block, title = "Blocked numbers",
                    onClick = {
                        launchSafe(context,
                            Intent("android.telecom.action.MANAGE_BLOCKED_NUMBERS"),
                            fallbackAction = Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            fallbackData = "package:${context.packageName}")
                    })
            }
            item {
                SettingsNavItem(icon = Icons.Default.Call, title = "Calls",
                    onClick = { launchSafe(context, Intent("android.settings.CALL_SETTINGS")) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.QuestionAnswer, title = "Quick responses",
                    onClick = { onNavigate(SettingsPage.QUICK_RESPONSES) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.MusicNote, title = "Sounds and vibration",
                    onClick = { launchSafe(context, Intent(Settings.ACTION_SOUND_SETTINGS)) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.Voicemail, title = "Voicemail",
                    onClick = { onNavigate(SettingsPage.VOICEMAIL) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.MusicNote, title = "Contact ringtones",
                    onClick = { onNavigate(SettingsPage.CONTACT_RINGTONES) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.Person, title = "Calling card",
                    onClick = { onNavigate(SettingsPage.CALLING_CARD) })
            }

            // Advanced
            item { SectionHeader("Advanced") }
            item {
                SettingsNavItem(icon = Icons.Default.Notifications, title = "Caller ID announcement",
                    onClick = { onNavigate(SettingsPage.CALLER_ID_ANNOUNCEMENT) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.Settings, title = "Display options",
                    onClick = { onNavigate(SettingsPage.DISPLAY_OPTIONS) })
            }
            item {
                SettingsNavItem(icon = Icons.Default.Phone, title = "Flip To Silence",
                    onClick = { onNavigate(SettingsPage.FLIP_TO_SILENCE) })
            }
            item {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                var endCallAnywhere by remember { mutableStateOf(prefs.getBoolean(KEY_END_CALL_ANYWHERE, false)) }
                ToggleRow(
                    title = "End call from any app",
                    subtitle = "Hardware end button hangs up even outside the dialer",
                    checked = endCallAnywhere,
                    onToggle = { endCallAnywhere = it; prefs.edit().putBoolean(KEY_END_CALL_ANYWHERE, it).apply() }
                )
            }

            // About
            item { SectionHeader("About") }
            item {
                SettingsNavItem(icon = Icons.Default.Info, title = "About Zeno Dialer",
                    subtitle = "Version ${getAppVersion(context)}",
                    onClick = {
                        launchSafe(context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${context.packageName}")
                            })
                    })
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Caller ID & Spam ─────────────────────────────────────────────────────────

@Composable
private fun CallerIdSpamScreen(onBack: () -> Unit) {
    val prefs = rememberPrefs()
    var seeCallerId by remember { mutableStateOf(prefs.getBoolean(KEY_CALLER_SPAM_ID, true)) }
    var filterSpam by remember { mutableStateOf(prefs.getBoolean(KEY_FILTER_SPAM, false)) }

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Caller ID & spam", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        ToggleRow(
            title = "See caller and spam ID",
            subtitle = "Identify business and spam numbers",
            checked = seeCallerId,
            onToggle = { seeCallerId = it; prefs.edit().putBoolean(KEY_CALLER_SPAM_ID, it).apply() }
        )

        Spacer(Modifier.height(8.dp))

        ToggleRow(
            title = "Filter spam calls",
            subtitle = "Prevent suspected spam calls from disturbing you",
            checked = filterSpam,
            onToggle = { filterSpam = it; prefs.edit().putBoolean(KEY_FILTER_SPAM, it).apply() }
        )

        Spacer(Modifier.height(24.dp))

        InfoRow("Zeno Dialer will attempt to show you useful information when you make or receive a call, such as a name for a number not in your contacts or a warning when an incoming call is suspected to be spam.")
    }
}

// ── Assisted Dialing ─────────────────────────────────────────────────────────

@Composable
private fun AssistedDialingScreen(onBack: () -> Unit) {
    val prefs = rememberPrefs()
    var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_ASSISTED_DIALING, false)) }

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Assisted dialing", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        ToggleRow(
            title = "Assisted dialing",
            checked = enabled,
            onToggle = { enabled = it; prefs.edit().putBoolean(KEY_ASSISTED_DIALING, it).apply() }
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Default home country", color = TextPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text("Automatically detected", color = TextSecondary, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))
        SettingsDivider()
        Spacer(Modifier.height(16.dp))

        InfoRow("Assisted dialing predicts and adds a country code when you call while traveling abroad.")
    }
}

// ── Quick Responses ──────────────────────────────────────────────────────────

@Composable
private fun QuickResponsesScreen(onBack: () -> Unit) {
    val prefs = rememberPrefs()
    val responses = remember {
        (0..3).map { i ->
            mutableStateOf(
                prefs.getString("${KEY_QUICK_RESPONSE_PREFIX}$i", DEFAULT_QUICK_RESPONSES[i])
                    ?: DEFAULT_QUICK_RESPONSES[i]
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Edit quick responses", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        responses.forEachIndexed { index, state ->
            var text by state

            BasicTextField(
                value = text,
                onValueChange = { newText ->
                    text = newText
                    prefs.edit().putString("${KEY_QUICK_RESPONSE_PREFIX}$index", newText).apply()
                },
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontSize = 15.sp
                ),
                cursorBrush = SolidColor(AccentGreen),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            )

            if (index < responses.lastIndex) {
                SettingsDivider()
            }
        }
    }
}

// ── Display Options ──────────────────────────────────────────────────────────

@Composable
private fun DisplayOptionsScreen(onBack: () -> Unit) {
    val prefs = rememberPrefs()
    var portraitMode by remember { mutableStateOf(prefs.getBoolean(KEY_PORTRAIT_MODE, true)) }
    var themeChoice by remember { mutableIntStateOf(prefs.getInt(KEY_THEME, 0)) }
    var sortBy by remember { mutableIntStateOf(prefs.getInt(KEY_SORT_BY, 0)) }
    var nameFormat by remember { mutableIntStateOf(prefs.getInt(KEY_NAME_FORMAT, 0)) }

    val themes = listOf("System default", "Light", "Dark")
    val sortOptions = listOf("First name", "Last name")
    val nameOptions = listOf("First name first", "Last name first")

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Display options", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        // Appearance
        SectionLabel("Appearance")
        PickerRow(
            title = "Choose theme",
            currentValue = themes[themeChoice],
            options = themes,
            selectedIndex = themeChoice,
            onSelect = { themeChoice = it; prefs.edit().putInt(KEY_THEME, it).apply() }
        )

        Spacer(Modifier.height(16.dp))

        // Controls
        SectionLabel("Controls")
        ToggleRow(
            title = "Keep portrait mode on calls",
            subtitle = "Prevents accidental auto-rotation when on a call",
            checked = portraitMode,
            onToggle = { portraitMode = it; prefs.edit().putBoolean(KEY_PORTRAIT_MODE, it).apply() }
        )

        Spacer(Modifier.height(8.dp))
        SettingsDivider()
        Spacer(Modifier.height(16.dp))

        // Contacts
        SectionLabel("Contacts")
        PickerRow(
            title = "Sort by",
            currentValue = sortOptions[sortBy],
            options = sortOptions,
            selectedIndex = sortBy,
            onSelect = { sortBy = it; prefs.edit().putInt(KEY_SORT_BY, it).apply() }
        )
        Spacer(Modifier.height(8.dp))
        PickerRow(
            title = "Name format",
            currentValue = nameOptions[nameFormat],
            options = nameOptions,
            selectedIndex = nameFormat,
            onSelect = { nameFormat = it; prefs.edit().putInt(KEY_NAME_FORMAT, it).apply() }
        )
    }
}

// ── Flip To Silence ──────────────────────────────────────────────────────────

@Composable
private fun FlipToSilenceScreen(onBack: () -> Unit) {
    val prefs = rememberPrefs()
    var enabled by remember { mutableStateOf(prefs.getBoolean(KEY_FLIP_TO_SILENCE, false)) }

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Flip To Silence", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        ToggleRow(
            title = "Flip To Silence",
            subtitle = "To silence an incoming call, place your device face down on a flat surface",
            checked = enabled,
            onToggle = { enabled = it; prefs.edit().putBoolean(KEY_FLIP_TO_SILENCE, it).apply() }
        )
    }
}

// ── Caller ID Announcement ───────────────────────────────────────────────────

@Composable
private fun CallerIdAnnouncementScreen(onBack: () -> Unit) {
    val prefs = rememberPrefs()
    var selected by remember { mutableIntStateOf(prefs.getInt(KEY_CALLER_ID_ANNOUNCE, 0)) }
    val options = listOf("Never", "Always", "Only when using a headset")

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Caller ID announcement", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        Text("Announce caller ID", color = TextPrimary, fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(4.dp))
        Text(options[selected], color = TextSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp))

        Spacer(Modifier.height(12.dp))

        options.forEachIndexed { index, label ->
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clickable { selected = index; prefs.edit().putInt(KEY_CALLER_ID_ANNOUNCE, index).apply() }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected == index,
                    onClick = { selected = index; prefs.edit().putInt(KEY_CALLER_ID_ANNOUNCE, index).apply() },
                    colors = RadioButtonDefaults.colors(selectedColor = AccentGreen, unselectedColor = TextSecondary)
                )
                Spacer(Modifier.width(12.dp))
                Text(label, color = if (selected == index) TextPrimary else TextSecondary, fontSize = 15.sp)
            }
        }

        Spacer(Modifier.height(24.dp))
        InfoRow("The caller\u2019s name and number will be read out loud for incoming calls.")
    }
}

// ── Voicemail ────────────────────────────────────────────────────────────────

@Composable
private fun VoicemailScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = rememberPrefs()
    var visualVoicemail by remember { mutableStateOf(prefs.getBoolean(KEY_VISUAL_VOICEMAIL, false)) }

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Voicemail", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        // Notifications
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable {
                    launchSafe(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    })
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Notifications", color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.height(8.dp))

        ToggleRow(
            title = "Visual voicemail",
            subtitle = "The carrier may not support visual voicemail",
            checked = visualVoicemail,
            onToggle = { visualVoicemail = it; prefs.edit().putBoolean(KEY_VISUAL_VOICEMAIL, it).apply() }
        )

        Spacer(Modifier.height(16.dp))

        // Advanced Settings
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { launchSafe(context, Intent("android.settings.CALL_SETTINGS")) }
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Advanced Settings", color = TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Contact Ringtones ────────────────────────────────────────────────────────

@Composable
private fun ContactRingtonesScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxSize().background(BgPage),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SettingsTopBar(title = "Contact ringtones", onBack = onBack)
        Spacer(Modifier.weight(0.3f))

        Box(
            modifier = Modifier.size(120.dp).clip(CircleShape).background(BgElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.MusicNote, null, tint = AccentGreen, modifier = Modifier.size(48.dp))
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = "Add a custom ringtone to individual contacts to help you recognize who\u2019s calling. To manage the default ringtone for this device, visit Settings.",
            color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(Modifier.height(32.dp))

        Box(
            modifier = Modifier.clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                .clickable {
                    launchSafe(context, Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI))
                }
                .padding(horizontal = 24.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add contact ringtone", color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.weight(0.5f))
    }
}

// ── Calling Card ─────────────────────────────────────────────────────────────

@Composable
private fun CallingCardScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(BgPage)) {
        SettingsTopBar(title = "Calling card", onBack = onBack)
        Spacer(Modifier.height(16.dp))

        // Illustration
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(16.dp)).background(BgElevated).padding(vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Person, null, tint = AccentGreen, modifier = Modifier.size(64.dp))
        }

        Spacer(Modifier.height(24.dp))

        // Your calling card
        Text("Your calling card", color = TextSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        CardRow(
            text = "How you\u2019ll appear to others when making or receiving calls",
            buttonLabel = "Create",
            onClick = {
                launchSafe(context,
                    Intent(Intent.ACTION_VIEW, ContactsContract.Profile.CONTENT_URI),
                    fallbackAction = Intent.ACTION_VIEW,
                    fallbackData = ContactsContract.Contacts.CONTENT_URI.toString())
            }
        )

        Spacer(Modifier.height(24.dp))

        // Contact calling card
        Text("Contact calling card", color = TextSecondary, fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(8.dp))
        CardRow(
            text = "How you see your contact\u2019s name and image when they call",
            buttonLabel = "Create",
            onClick = {
                launchSafe(context, Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
            }
        )
    }
}

// ── Shared components ────────────────────────────────────────────────────────

@Composable
private fun SettingsTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).clickable { onBack() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary, modifier = Modifier.size(22.dp))
        }
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title, color = AccentGreen, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun SettingsNavItem(
    icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(BgElevated),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, title, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp)
            if (subtitle != null) {
                Text(subtitle, color = TextSecondary, fontSize = 12.sp)
            }
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String, subtitle: String? = null, checked: Boolean, onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 16.sp)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = TextSecondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked, onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = AccentGreen,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline, uncheckedTrackColor = BgElevated
            )
        )
    }
}

@Composable
private fun InfoRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.Info, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, color = TextSecondary, fontSize = 13.sp)
    }
}

@Composable
private fun CardRow(text: String, buttonLabel: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(12.dp)).background(BgElevated)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = TextSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.inverseSurface)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(buttonLabel, color = MaterialTheme.colorScheme.inverseOnSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun PickerRow(
    title: String, currentValue: String, options: List<String>,
    selectedIndex: Int, onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextPrimary, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(currentValue, color = TextSecondary, fontSize = 13.sp)
            }
        }

        if (expanded) {
            options.forEachIndexed { index, label ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { onSelect(index); expanded = false }
                        .padding(horizontal = 32.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedIndex == index,
                        onClick = { onSelect(index); expanded = false },
                        colors = RadioButtonDefaults.colors(selectedColor = AccentGreen, unselectedColor = TextSecondary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = if (selectedIndex == index) TextPrimary else TextSecondary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun rememberPrefs(): SharedPreferences {
    val context = LocalContext.current
    return remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun launchSafe(
    context: Context, intent: Intent, fallbackAction: String? = null, fallbackData: String? = null
) {
    runCatching { context.startActivity(intent) }
        .onFailure {
            if (fallbackAction != null) {
                runCatching {
                    context.startActivity(Intent(fallbackAction).apply {
                        if (fallbackData != null) data = Uri.parse(fallbackData)
                    })
                }
            }
        }
}

private fun getAppVersion(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
    } catch (_: Exception) { "1.0" }
}
