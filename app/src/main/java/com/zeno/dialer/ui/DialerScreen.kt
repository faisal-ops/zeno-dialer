package com.zeno.dialer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.telecom.Call
import com.zeno.dialer.ActiveCallInfo
import com.zeno.dialer.CallStateHolder
import com.zeno.dialer.InCallActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.zeno.dialer.ui.theme.LocalDialerColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zeno.dialer.DialerUiState
import com.zeno.dialer.DialerViewModel
import com.zeno.dialer.data.Contact
import com.zeno.dialer.data.FilterMode
import kotlin.math.abs

// ── Static palette ───────────────────────────────────────────────────────────

internal val BgPage: Color
    @Composable get() = LocalDialerColors.current.bgPage

internal val BgSurface: Color
    @Composable get() = LocalDialerColors.current.bgSurface

internal val BgElevated: Color
    @Composable get() = LocalDialerColors.current.bgElevated

internal val TextPrimary: Color
    @Composable get() = LocalDialerColors.current.textPrimary

internal val TextSecondary: Color
    @Composable get() = LocalDialerColors.current.textSecondary

internal val TextHint: Color
    @Composable get() = LocalDialerColors.current.textHint

internal val Border: Color
    @Composable get() = LocalDialerColors.current.border

internal val FocusBorder: Color
    @Composable get() = LocalDialerColors.current.focusBorder

internal val SurfaceActive: Color
    @Composable get() = LocalDialerColors.current.surfaceActive

internal val Accent: Color
    @Composable get() = LocalDialerColors.current.accent

internal val AccentMuted: Color
    @Composable get() = LocalDialerColors.current.accentMuted

internal val AccentGreen: Color
    @Composable get() = LocalDialerColors.current.accent

internal val AccentGreenBright = Color(0xFF69F0AE)

internal val avatarPalette = listOf(
    Color(0xFF5B6ABF), Color(0xFF00796B), Color(0xFF2E7D32),
    Color(0xFF7B1FA2), Color(0xFFC62828), Color(0xFF0277BD),
    Color(0xFFEF6C00), Color(0xFF00838F), Color(0xFF6A1B9A),
    Color(0xFF4E342E),
)
internal fun avatarColor(name: String): Color =
    avatarPalette[abs(name.hashCode()) % avatarPalette.size]

// ── Tabs ─────────────────────────────────────────────────────────────────────

enum class DialerTab(val label: String, val icon: ImageVector) {
    FAVORITES("Favorites", Icons.Default.Favorite),
    HOME("Home", Icons.Default.Home),
    KEYPAD("Keypad", Icons.Default.Dialpad)
}

// ── Root screen ──────────────────────────────────────────────────────────────

@Composable
fun DialerScreen(viewModel: DialerViewModel, requestedTab: DialerTab? = null) {
    val state      by viewModel.uiState.collectAsStateWithLifecycle()
    val activeCall by CallStateHolder.info.collectAsStateWithLifecycle()
    val tab        = tabFromIndex(state.currentTabIndex)
    var showMenu   by remember { mutableStateOf(false) }
    val context    = LocalContext.current

    SideEffect { viewModel.setKeypadActive(tab == DialerTab.KEYPAD) }

    LaunchedEffect(requestedTab) {
        requestedTab?.let { viewModel.setCurrentTab(tabToIndex(it)) }
    }

    // Show banner when there is an ongoing/held call but InCallActivity is not in front.
    val showCallBanner = activeCall != null && activeCall!!.state !in listOf(
        Call.STATE_DISCONNECTED, Call.STATE_DISCONNECTING
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BgPage)
        ) {
            if (showCallBanner) {
                ReturnToCallBanner(
                    info = activeCall!!,
                    onClick = {
                        context.startActivity(
                            Intent(context, InCallActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                            }
                        )
                    },
                    onEndCall = { CallStateHolder.hangup() }
                )
            }

            Crossfade(
                targetState   = tab,
                modifier      = Modifier.weight(1f),
                animationSpec = tween(200),
                label         = "tab_transition"
            ) { currentTab ->
                when (currentTab) {
                    DialerTab.FAVORITES -> FavoritesContent(state, viewModel, onMenuOpen = { showMenu = true })
                    DialerTab.HOME   -> HomeContent(
                        state,
                        viewModel,
                        onMenuOpen = { showMenu = true },
                        onEditNumber = { number ->
                            viewModel.setQueryDirect(number)
                            viewModel.setCurrentTab(tabToIndex(DialerTab.KEYPAD))
                        }
                    )
                    DialerTab.KEYPAD -> KeypadContent(state, viewModel)
                }
            }

            BottomNavBar(current = tab, onSelect = { viewModel.setCurrentTab(tabToIndex(it)) })
        }

        // ── Scrim ────────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showMenu,
            enter   = fadeIn(tween(200)),
            exit    = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showMenu = false }
            )
        }

        // ── Drawer panel ─────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showMenu,
            enter   = slideInHorizontally(tween(300, easing = EaseInOutCubic)) { -it },
            exit    = slideOutHorizontally(tween(250, easing = EaseInOutCubic)) { -it }
        ) {
            PhoneMenuDrawer(
                onDismiss = { showMenu = false },
                viewModel = viewModel
            )
        }
    }
}

private fun tabToIndex(tab: DialerTab): Int = when (tab) {
    DialerTab.FAVORITES -> 0
    DialerTab.HOME -> 1
    DialerTab.KEYPAD -> 2
}

private fun tabFromIndex(index: Int): DialerTab = when (index) {
    0 -> DialerTab.FAVORITES
    2 -> DialerTab.KEYPAD
    else -> DialerTab.HOME
}

// ── HOME tab ─────────────────────────────────────────────────────────────────

@Composable
private fun HomeContent(
    state: DialerUiState,
    viewModel: DialerViewModel,
    onMenuOpen: () -> Unit,
    onEditNumber: (String) -> Unit
) {
    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!text.isNullOrBlank()) viewModel.setQueryDirect(text)
        }
    }

    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(Modifier.height(12.dp))

        SearchBar(
            displayQuery  = state.displayQuery,
            onMenuClick   = onMenuOpen,
            onVoiceSearch = {
                voiceLauncher.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                 RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                        putExtra(RecognizerIntent.EXTRA_PROMPT, "Search contacts…")
                    }
                )
            },
            onContactsOpen = {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).apply {
                        data = ContactsContract.Contacts.CONTENT_URI
                    }
                )
            },
            modifier      = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(12.dp))

        FilterChipRow(current = state.filterMode, onSelect = { viewModel.setFilter(it) })

        Spacer(Modifier.height(4.dp))

        ResultsList(
            results       = state.results,
            selectedIndex = state.selectedIndex,
            modifier      = Modifier.weight(1f).fillMaxWidth(),
            onTap         = { i -> viewModel.selectItem(i) },
            onDoubleTap   = { i -> viewModel.selectItem(i); viewModel.callItem(i) },
            onCallNumber  = { number -> viewModel.callNumber(number) },
            onEditNumber  = onEditNumber,
            onAddFavorite = { contact -> viewModel.pinFavorite(contact) },
            onToggleBlocked = { contact ->
                val normalized = contact.number.filter { it.isDigit() }
                if (normalized in state.blockedNumbers) viewModel.removeBlockedNumber(contact.number)
                else viewModel.addBlockedNumber(contact.number)
            },
            isBlocked = { contact -> contact.number.filter { it.isDigit() } in state.blockedNumbers },
            onDelete      = { number -> viewModel.deleteCallLogForNumber(number) },
            toggleExpandFlow = viewModel.toggleExpandEvent
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavoritesContent(state: DialerUiState, viewModel: DialerViewModel, onMenuOpen: () -> Unit) {
    val listState = rememberLazyListState()
    val suggestions = state.favoriteSuggestions
    val selIdx = state.favoritesSuggestionIndex

    LaunchedEffect(selIdx, suggestions.size) {
        if (suggestions.isEmpty()) return@LaunchedEffect
        val flatPos = 3 + selIdx.coerceIn(0, suggestions.lastIndex)
        val visible = listState.layoutInfo.visibleItemsInfo
        if (visible.isEmpty()) {
            listState.scrollToItem(flatPos)
            return@LaunchedEffect
        }
        val first = visible.first().index
        val last = visible.last().index
        when {
            flatPos < first -> listState.animateScrollToItem(flatPos)
            flatPos > last -> listState.animateScrollToItem((flatPos - 1).coerceAtLeast(0))
        }
    }

    // Single LazyColumn so the entire screen — pinned row, section labels,
    // and suggestion contacts — all scroll together as one unit.
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {

        // ── Header ────────────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable { onMenuOpen() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu", tint = TextSecondary)
                }
                Text("Favorites", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(44.dp))
            }
        }

        // ── Pinned section ────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(10.dp))
            Text(
                text     = "Pinned",
                color    = TextSecondary,
                style    = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (state.pinnedFavorites.isEmpty()) {
                Text(
                    text     = "Long-press any contact below to pin them here",
                    color    = TextHint,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                FavoritesRow(
                    favorites    = state.pinnedFavorites,
                    onTap        = { c -> viewModel.callNumber(c.number) },
                    onLongPress  = { c -> viewModel.unpinFavorite(c.number) }
                )
            }
        }

        // ── Suggestions section label ─────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            Text(
                text     = "Suggestions",
                color    = TextSecondary,
                style    = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(4.dp))
        }

        // ── Suggestion contacts (inline — no nested LazyColumn) ───────────────
        if (state.favoriteSuggestions.isEmpty()) {
            item {
                Text(
                    text     = "No suggestions yet",
                    color    = TextHint,
                    style    = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        } else {
            itemsIndexed(suggestions, key = { _, c -> c.number }) { idx, contact ->
                SuggestionRow(
                    contact = contact,
                    selected = idx == selIdx,
                    isPinned = state.pinnedFavorites.any { it.number == contact.number },
                    onCall  = { viewModel.callNumber(contact.number) },
                    onPin   = { viewModel.pinFavorite(contact) },
                    onUnpin = { viewModel.unpinFavorite(contact.number) }
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SuggestionRow(
    contact: Contact,
    selected: Boolean,
    isPinned: Boolean,
    onCall: () -> Unit,
    onPin: () -> Unit,
    onUnpin: () -> Unit
) {
    val rowBg = if (selected) SurfaceActive else Color.Transparent
    val nameColor = if (selected) Accent else TextPrimary
    val secondaryColor = if (selected) AccentMuted else TextSecondary
    val pinTint = when {
        isPinned -> Accent
        selected -> AccentMuted
        else -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(50.dp)
                    .background(Accent)
            )
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(
                    start = if (selected) 10.dp else 12.dp,
                    end = 8.dp,
                    top = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text     = contact.name,
                    color    = nameColor,
                    style    = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text     = contact.number,
                    color    = secondaryColor,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { if (isPinned) onUnpin() else onPin() }
                    .border(1.dp, Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Favorite,
                    contentDescription = if (isPinned) "Unpin" else "Pin",
                    tint               = pinTint,
                    modifier           = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable { onCall() }
                    .border(1.dp, Border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Phone,
                    contentDescription = "Call",
                    tint               = AccentMuted,
                    modifier           = Modifier.size(16.dp)
                )
            }
        }
    }
}

// ── KEYPAD tab ───────────────────────────────────────────────────────────────

private val dialpadKeys = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
    listOf('*', '0', '#')
)

@Composable
private fun KeypadContent(state: DialerUiState, viewModel: DialerViewModel) {
    val matched  = state.results.firstOrNull()
    val hasInput = state.query.isNotBlank()
    val canCall  = hasInput

    val view = LocalView.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Derive button cell size: fit 3 columns in available width.
        // Also cap height so the 4-row grid + action row never exceeds available height.
        val hPad       = 16.dp
        val cellWidth  = (maxWidth - hPad * 2) / 3
        // Reserve 64dp for number display + ~64dp for action row
        val gridHeight  = maxHeight - 64.dp - 64.dp - 8.dp
        val cellHeight  = gridHeight / 4
        // Use the smaller dimension so buttons stay square and nothing overflows
        val cellSize   = minOf(cellWidth, cellHeight)

        val digitFs   = (cellSize.value * 0.35f).coerceIn(20f, 36f).sp
        val callBtnSz = (cellSize * 0.85f).coerceIn(48.dp, 72.dp)
        val backBtnSz = (cellSize * 0.72f).coerceIn(40.dp, 60.dp)

        Column(
            modifier            = Modifier.fillMaxSize().padding(bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Number display ────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text       = if (hasInput) state.query else "",
                        color      = TextPrimary,
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Light,
                        textAlign  = TextAlign.Center,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    if (matched != null && hasInput) {
                        Text(
                            text      = matched.name,
                            color     = TextSecondary,
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines  = 1,
                            overflow  = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ── Dialpad grid ──────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = hPad),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
            ) {
                dialpadKeys.forEach { row ->
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        row.forEach { digit ->
                            DialpadButton(
                                digit   = digit,
                                size    = cellSize * 0.86f,
                                digitFs = digitFs,
                                onClick = { viewModel.typeUnicode(digit.code) }
                            )
                        }
                    }
                }
            }

            // ── Bottom row: spacer | Call | Backspace ─────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = hPad),
                horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                // Placeholder to balance the call button
                Spacer(Modifier.size(backBtnSz))

                // Call button
                Box(
                    modifier = Modifier
                        .size(callBtnSz)
                        .clip(CircleShape)
                        .background(if (canCall) AccentGreen else MaterialTheme.colorScheme.surfaceContainerLowest)
                        .clickable(enabled = canCall) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.callSelected()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.Default.Phone,
                        contentDescription = "Call",
                        tint               = if (canCall) Color.White else TextHint,
                        modifier           = Modifier.size(callBtnSz * 0.42f)
                    )
                }

                // Backspace button
                Box(
                    modifier = Modifier
                        .size(backBtnSz)
                        .clip(CircleShape)
                        .background(BgSurface)
                        .border(0.75.dp, Border, CircleShape)
                        .clickable(enabled = hasInput) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            viewModel.deleteChar()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Delete",
                        tint               = if (hasInput) TextSecondary else TextHint,
                        modifier           = Modifier.size(backBtnSz * 0.42f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DialpadButton(
    digit: Char,
    size: Dp,
    digitFs: TextUnit,
    onClick: () -> Unit
) {
    val view = LocalView.current
    val isLightTheme = BgPage.luminance() > 0.5f
    val keyBg = if (isLightTheme) Color(0xFFF1F3F4) else Color(0xFF20232B)
    val keyBorder = if (isLightTheme) Color(0xFFDADCE0) else Color(0xFF343844)
    val keyText = if (isLightTheme) Color(0xFF202124) else TextPrimary
    Box(
        modifier         = Modifier
            .size(size)
            .shadow(
                elevation = if (isLightTheme) 1.5.dp else 0.dp,
                shape = CircleShape,
                clip = false
            )
            .clip(CircleShape)
            .background(keyBg)
            .border(1.dp, keyBorder, CircleShape)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text       = digit.toString(),
            color      = keyText,
            fontSize   = digitFs,
            fontWeight = if (isLightTheme) FontWeight.Normal else FontWeight.Light
        )
    }
}

// ── Bottom nav bar ───────────────────────────────────────────────────────────

@Composable
private fun BottomNavBar(current: DialerTab, onSelect: (DialerTab) -> Unit) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .background(BgSurface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        DialerTab.entries.forEach { tab ->
            NavItem(
                icon     = tab.icon,
                label    = tab.label,
                selected = current == tab,
                onClick  = { onSelect(tab) }
            )
        }
    }
}

@Composable
private fun NavItem(
    icon:     ImageVector,
    label:    String,
    selected: Boolean,
    onClick:  () -> Unit
) {
    val iconColor = if (selected) TextPrimary else TextSecondary
    val textColor = if (selected) TextPrimary else TextSecondary

    Column(
        modifier            = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() }
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = label,
                tint               = iconColor,
                modifier           = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text  = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 13.sp,
                letterSpacing = 0.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal
            )
        )
        if (selected) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(24.dp)
                    .height(2.dp)
                    .background(Accent, RoundedCornerShape(1.dp))
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}

// ── Search bar ───────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(
    displayQuery:  String,
    onMenuClick:   () -> Unit,
    onVoiceSearch: () -> Unit,
    onContactsOpen: () -> Unit = {},
    modifier:      Modifier = Modifier
) {
    Box(
        modifier         = modifier
            .height(50.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(BgElevated)
            .border(1.dp, Border, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier          = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onMenuClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint               = TextSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Text(
                text     = if (displayQuery.isEmpty()) "Search contacts" else displayQuery,
                color    = if (displayQuery.isEmpty()) TextSecondary else TextPrimary,
                style    = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onVoiceSearch() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Mic,
                    contentDescription = "Voice search",
                    tint               = TextSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }

            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable { onContactsOpen() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector        = Icons.Default.Contacts,
                    contentDescription = "Contacts",
                    tint               = TextSecondary,
                    modifier           = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ── Filter chips ─────────────────────────────────────────────────────────────

private data class ChipDef(val mode: FilterMode, val label: String)
private val filterChips = listOf(
    ChipDef(FilterMode.ALL,      "All"),
    ChipDef(FilterMode.MISSED,   "Missed"),
    ChipDef(FilterMode.CONTACTS, "Contacts"),
    ChipDef(FilterMode.RECENTS,  "Recents"),
)

@Composable
private fun FilterChipRow(current: FilterMode, onSelect: (FilterMode) -> Unit) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filterChips, key = { it.mode.name }) { chip ->
            val sel = chip.mode == current

            val bgColor = if (sel) SurfaceActive else Color.Transparent
            val borderColor = if (sel) Accent else Border
            val textColor = if (sel) Accent else TextSecondary

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgColor)
                    .border(0.75.dp, borderColor, RoundedCornerShape(20.dp))
                    .clickable { onSelect(chip.mode) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text  = chip.label,
                    color = textColor,
                    style = if (sel) MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            else MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Normal)
                )
            }
        }
    }
}

// ── Return-to-call banner ─────────────────────────────────────────────────────

@Composable
private fun ReturnToCallBanner(info: ActiveCallInfo, onClick: () -> Unit, onEndCall: () -> Unit) {
    val stateText = when (info.state) {
        Call.STATE_ACTIVE  -> "Active call"
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DIALING -> "Calling…"
        else               -> "In call"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A2E1A))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.weight(1f)
        ) {
            Icon(
                imageVector        = Icons.Default.Phone,
                contentDescription = null,
                tint               = AccentGreenBright,
                modifier           = Modifier.size(18.dp)
            )
            Column {
                Text(
                    text       = info.displayName,
                    color      = Color(0xFFCCFFCC),
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis
                )
                Text(text = stateText, color = Color(0xFF66BB66), fontSize = 11.sp)
            }
        }
        Text(
            text       = "End Call",
            color      = Color(0xFFCC4444),
            fontSize   = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF3A1A1A))
                .clickable { onEndCall() }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

// ── Phone menu drawer ────────────────────────────────────────────────────────

@Composable
private fun PhoneMenuDrawer(onDismiss: () -> Unit, viewModel: DialerViewModel) {
    val context = LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.78f)
                .align(Alignment.CenterStart)
                .background(BgSurface)
                .border(width = 1.dp, color = Border, shape = RoundedCornerShape(0.dp))
                .clickable(enabled = false) { }
        ) {
            Spacer(Modifier.height(40.dp))

            Text(
                text     = "Phone",
                color    = TextPrimary,
                style    = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(12.dp))

            DrawerMenuItem(
                icon  = Icons.Default.Contacts,
                label = "Contacts"
            ) {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)
                )
                onDismiss()
            }

            DrawerMenuItem(
                icon  = Icons.Default.Settings,
                label = "Settings"
            ) {
                context.startActivity(Intent(context, com.zeno.dialer.SettingsActivity::class.java))
                onDismiss()
            }

            DrawerMenuItem(
                icon  = Icons.Default.History,
                label = "Clear call history"
            ) {
                showClearDialog = true
            }

            DrawerMenuItem(
                icon  = Icons.AutoMirrored.Filled.Help,
                label = "Help & feedback"
            ) {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO).apply {
                        data    = Uri.parse("mailto:")
                        putExtra(Intent.EXTRA_EMAIL,   arrayOf("support@zeno.app"))
                        putExtra(Intent.EXTRA_SUBJECT, "Zeno Dialer — Feedback")
                    }
                )
                onDismiss()
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title   = { Text("Clear call history?") },
            text    = { Text("All call log entries will be permanently deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCallHistory()
                    showClearDialog = false
                    onDismiss()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}


@Composable
private fun DrawerMenuItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = label,
            tint               = TextSecondary,
            modifier           = Modifier.size(24.dp)
        )
        Text(
            text  = label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

// ── Favorites row ────────────────────────────────────────────────────────────

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun FavoritesRow(
    favorites: List<Contact>,
    onTap: (Contact) -> Unit,
    onLongPress: ((Contact) -> Unit)? = null
) {
    LazyRow(
        contentPadding        = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(favorites, key = { it.number }) { contact ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier
                    .width(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(
                        onClick = { onTap(contact) },
                        onLongClick = { onLongPress?.invoke(contact) }
                    )
                    .padding(vertical = 4.dp)
            ) {
                ContactAvatar(
                    name     = contact.name,
                    photoUri = contact.photoUri,
                    size     = 46
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = contact.name,
                    color     = TextSecondary,
                    style     = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    maxLines  = 2,
                    overflow  = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    lineHeight = 13.sp
                )
            }
        }
    }
}
