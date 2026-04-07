package com.zeno.dialer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telephony.SmsManager
import com.zeno.dialer.CallHistoryDetailActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.PhoneCallback
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zeno.dialer.data.Contact
import com.zeno.dialer.data.RecentsRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Row types ────────────────────────────────────────────────────────────────

private sealed class ListRow {
    data class DateHeader(val label: String)              : ListRow()
    data class SectionLabel(val title: String)            : ListRow()
    data class Item(val contact: Contact, val idx: Int)   : ListRow()
}

private fun buildRows(results: List<Contact>): List<ListRow> {
    if (results.isEmpty()) return emptyList()

    val recents  = results.filter { it.isRecent }
    val contacts = results.filter { !it.isRecent }
    val rows     = mutableListOf<ListRow>()

    var lastDateLabel = ""
    for (c in recents) {
        val label = c.lastCallTime.toDateLabel()
        if (label != lastDateLabel) {
            rows.add(ListRow.DateHeader(label))
            lastDateLabel = label
        }
        rows.add(ListRow.Item(c, results.indexOf(c)))
    }

    if (contacts.isNotEmpty()) {
        rows.add(ListRow.SectionLabel("Contacts"))
        for (c in contacts) rows.add(ListRow.Item(c, results.indexOf(c)))
    }

    return rows
}

// ── ResultsList ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ResultsList(
    results: List<Contact>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onTap: (Int) -> Unit,
    onDoubleTap: (Int) -> Unit,
    onCallNumber: (String) -> Unit = {},
    onEditNumber: (String) -> Unit = {},
    onAddFavorite: (Contact) -> Unit = {},
    onToggleBlocked: (Contact) -> Unit = {},
    isBlocked: (Contact) -> Boolean = { false },
    onDelete: (String) -> Unit = {},
    toggleExpandFlow: kotlinx.coroutines.flow.SharedFlow<Int>? = null
) {
    val context   = LocalContext.current
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()

    val rows = remember(results) { buildRows(results) }

    val flatPos = remember(selectedIndex, rows) {
        rows.indexOfFirst { it is ListRow.Item && it.idx == selectedIndex }
    }
    LaunchedEffect(flatPos) {
        if (flatPos < 0) return@LaunchedEffect
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

    // ── Expanded row state ───────────────────────────────────────────────────
    var expandedIdx by remember { mutableIntStateOf(-1) }

    // Toggle expand from D-pad center/enter via ViewModel event
    LaunchedEffect(toggleExpandFlow) {
        toggleExpandFlow?.collect { idx ->
            expandedIdx = if (expandedIdx == idx) -1 else idx
        }
    }

    // ── Bottom-sheet state ────────────────────────────────────────────────────
    var actionTarget    by remember { mutableStateOf<Pair<Contact, Int>?>(null) }
    val sheetState      = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Quick-response message state
    var quickMsgTarget by remember { mutableStateOf<Contact?>(null) }
    val quickMsgSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val prefs = context.getSharedPreferences("zeno", Context.MODE_PRIVATE)
    val quickResponses = remember {
        (0..3).map { i ->
            prefs.getString("quick_response_$i", null)
                ?: when (i) {
                    0 -> "Can't talk now. What's up?"
                    1 -> "I'll call you right back."
                    2 -> "I'll call you later."
                    else -> "Can't talk now. Call me later?"
                }
        }
    }

    fun dismissSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            actionTarget  = null
        }
    }

    if (results.isEmpty()) {
        EmptyState(modifier = modifier)
        return
    }

    LazyColumn(state = listState, modifier = modifier) {
        rows.forEach { row ->
            when (row) {

                is ListRow.DateHeader -> item(
                    key = "dh_${row.label}",
                    contentType = "header"
                ) {
                    DateHeaderRow(label = row.label)
                }

                is ListRow.SectionLabel -> item(
                    key = "sl_${row.title}",
                    contentType = "header"
                ) {
                    SectionLabelRow(title = row.title)
                }

                is ListRow.Item -> {
                    val c   = row.contact
                    val idx = row.idx
                    item(
                        key = "${c.id}_${c.number}",
                        contentType = "contact"
                    ) {
                        Column {
                            ContactRow(
                                contact  = c,
                                selected = idx == selectedIndex,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick       = {
                                            expandedIdx = if (expandedIdx == idx) -1 else idx
                                        },
                                        onDoubleClick = { onDoubleTap(idx) },
                                        onLongClick   = { actionTarget = c to idx }
                                    ),
                                onMessageTap = { quickMsgTarget = c },
                                onCallTap = { onCallNumber(c.number) }
                            )

                            AnimatedVisibility(
                                visible = expandedIdx == idx,
                                enter   = expandVertically(),
                                exit    = shrinkVertically()
                            ) {
                                ExpandedPanel(
                                    contact       = c,
                                    onCall        = { onCallNumber(c.number) },
                                    onMessage     = {
                                        context.startActivity(
                                            Intent(Intent.ACTION_SENDTO).apply {
                                                data = Uri.parse("smsto:${c.number}")
                                            }
                                        )
                                    },
                                    onHistory     = {
                                        context.startActivity(
                                            Intent(context, CallHistoryDetailActivity::class.java).apply {
                                                putExtra(CallHistoryDetailActivity.EXTRA_NUMBER, c.number)
                                                putExtra(CallHistoryDetailActivity.EXTRA_NAME, c.name)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Action bottom sheet ───────────────────────────────────────────────────
    actionTarget?.let { (contact, _) ->
        ModalBottomSheet(
            onDismissRequest = { dismissSheet() },
            sheetState       = sheetState,
            containerColor   = BgElevated,
            tonalElevation   = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
            ) {
            // Header
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 48)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text       = contact.name,
                        color      = TextPrimary,
                        style      = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis
                    )
                    Text(
                        text  = contact.number,
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            HorizontalDivider(color = Border)

            // Options
            SheetOption(
                icon  = Icons.Default.Phone,
                label = "Call",
                tint  = AccentGreen
            ) {
                dismissSheet()
                onCallNumber(contact.number)
            }

            if (contact.id > 0L) {
                SheetOption(
                    icon  = Icons.Default.Edit,
                    label = "Edit contact"
                ) {
                    dismissSheet()
                    context.startActivity(
                        Intent(Intent.ACTION_EDIT).apply {
                            data = ContentUris.withAppendedId(
                                ContactsContract.Contacts.CONTENT_URI, contact.id
                            )
                        }
                    )
                }
            } else {
                SheetOption(
                    icon  = Icons.Default.PersonAdd,
                    label = "Add to contacts"
                ) {
                    dismissSheet()
                    context.startActivity(
                        Intent(Intent.ACTION_INSERT).apply {
                            type  = ContactsContract.Contacts.CONTENT_TYPE
                            putExtra(ContactsContract.Intents.Insert.PHONE, contact.number)
                            putExtra(ContactsContract.Intents.Insert.NAME, contact.name.takeIf { it != contact.number })
                        }
                    )
                }
            }

            SheetOption(
                icon  = Icons.Default.Favorite,
                label = "Add to favorites"
            ) {
                dismissSheet()
                onAddFavorite(contact)
            }

            SheetOption(
                icon  = Icons.Default.Block,
                label = if (isBlocked(contact)) "Remove from blocked list" else "Add to blocked list"
            ) {
                dismissSheet()
                onToggleBlocked(contact)
            }

            SheetOption(
                icon  = Icons.Default.ContentCopy,
                label = "Copy number"
            ) {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("phone", contact.number))
                dismissSheet()
            }

            if (contact.isRecent) {
                SheetOption(
                    icon  = Icons.Default.History,
                    label = "View call history"
                ) {
                    dismissSheet()
                    context.startActivity(
                        Intent(context, CallHistoryDetailActivity::class.java).apply {
                            putExtra(CallHistoryDetailActivity.EXTRA_NUMBER, contact.number)
                            putExtra(CallHistoryDetailActivity.EXTRA_NAME, contact.name)
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                    )
                }
            }

            SheetOption(
                icon  = Icons.Default.Delete,
                label = "Delete from log history",
                tint  = Color(0xFFCC4444)
            ) {
                showDeleteConfirm = true
            }

            Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ── Quick-response message sheet ─────────────────────────────────────────
    quickMsgTarget?.let { contact ->
        ModalBottomSheet(
            onDismissRequest = { quickMsgTarget = null },
            sheetState       = quickMsgSheetState,
            containerColor   = BgElevated,
            tonalElevation   = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // Header
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(name = contact.name, photoUri = contact.photoUri, size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "Send message to",
                            color      = TextSecondary,
                            style      = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text       = contact.name,
                            color      = TextPrimary,
                            style      = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines   = 1,
                            overflow   = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(color = Border)

                // Quick responses
                quickResponses.forEach { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                quickMsgTarget = null
                                context.startActivity(
                                    Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("smsto:${contact.number}")
                                        putExtra("sms_body", msg)
                                    }
                                )
                            }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector        = Icons.AutoMirrored.Filled.Message,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.primary,
                            modifier           = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text  = msg,
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Custom message option
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            quickMsgTarget = null
                            context.startActivity(
                                Intent(Intent.ACTION_SENDTO).apply {
                                    data = Uri.parse("smsto:${contact.number}")
                                }
                            )
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = Icons.Default.Edit,
                        contentDescription = null,
                        tint               = TextSecondary,
                        modifier           = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text  = "Write your own…",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    // ── Delete confirmation dialog ────────────────────────────────────────────
    if (showDeleteConfirm) {
        val number = actionTarget?.first?.number ?: ""
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title   = { Text("Delete call log?") },
            text    = { Text("All call history for $number will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    dismissSheet()
                    onDelete(number)
                }) { Text("Delete", color = Color(0xFFCC4444)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

// ── History sub-list ──────────────────────────────────────────────────────────

@Composable
private fun HistoryList(items: List<Contact>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
    ) {
        if (items.isEmpty()) {
            Text(
                text     = "No history found",
                color    = TextSecondary,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            return@Column
        }
        items.forEach { entry ->
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CallTypeIcon(callType = entry.callType, selected = false)
                Spacer(Modifier.width(10.dp))
                Text(
                    text  = callTypeLabel(entry.callType),
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text  = entry.lastCallTime.toTimeAgo(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun callTypeLabel(type: Int) = when (type) {
    CallLog.Calls.MISSED_TYPE   -> "Missed"
    CallLog.Calls.INCOMING_TYPE -> "Incoming"
    CallLog.Calls.OUTGOING_TYPE -> "Outgoing"
    else                        -> "Call"
}

// ── Sheet option row ─────────────────────────────────────────────────────────

@Composable
private fun SheetOption(
    icon: ImageVector,
    label: String,
    tint: Color = TextPrimary,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = null,
            tint               = tint,
            modifier           = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(text = label, color = tint, style = MaterialTheme.typography.bodyMedium)
    }
}

// ── Empty state ──────────────────────────────────────────────────────────────

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier         = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector        = Icons.Default.History,
                contentDescription = null,
                tint               = TextHint,
                modifier           = Modifier.size(56.dp)
            )
            Text(
                text  = "No results",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text  = "Your recent calls and contacts will appear here",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ── Section headers ──────────────────────────────────────────────────────────

@Composable
private fun DateHeaderRow(label: String) {
    Text(
        text     = label,
        color    = TextSecondary,
        style    = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun SectionLabelRow(title: String) {
    Text(
        text     = title.uppercase(),
        color    = TextSecondary,
        style    = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 4.dp)
    )
}

// ── Contact row ──────────────────────────────────────────────────────────────

@Composable
private fun ContactRow(
    contact: Contact,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onMessageTap: () -> Unit,
    onCallTap: () -> Unit
) {
    val rowBg = if (selected) SurfaceActive else Color.Transparent
    val nameColor = if (selected) Accent else TextPrimary
    val secondaryColor = if (selected) AccentMuted else TextSecondary

    Row(
        modifier = modifier
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
            .padding(start = if (selected) 10.dp else 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (contact.isRecent) {
                    CallTypeIcon(callType = contact.callType, selected = selected)
                    Spacer(Modifier.width(4.dp))
                }
                val secondaryText = remember(contact.number, contact.lastCallTime) {
                    buildSecondaryLine(contact)
                }
                Text(
                    text     = secondaryText,
                    color    = secondaryColor,
                    style    = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onMessageTap() }
                .border(1.dp, Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.Message,
                contentDescription = "Message",
                tint               = AccentMuted,
                modifier           = Modifier.size(16.dp)
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onCallTap() }
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

// ── Expanded inline panel (stock-dialer style) ──────────────────────────────

@Composable
private fun ExpandedPanel(
    contact: Contact,
    onCall: () -> Unit,
    onMessage: () -> Unit,
    onHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BgSurface)
            .padding(start = 66.dp, end = 14.dp, top = 4.dp, bottom = 8.dp)
    ) {
        // Last call time
        if (contact.isRecent && contact.lastCallTime > 0) {
            Text(
                text  = formatRelativeTime(contact.lastCallTime),
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text  = "See more in History",
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.clickable { onHistory() }
            )
            Spacer(Modifier.height(8.dp))
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExpandedActionButton(
                icon = Icons.Default.Phone,
                label = "Call",
                onClick = onCall,
                modifier = Modifier.weight(1f)
            )
            ExpandedActionButton(
                icon = Icons.AutoMirrored.Filled.Message,
                label = "Message",
                onClick = onMessage,
                modifier = Modifier.weight(1f)
            )
            ExpandedActionButton(
                icon = Icons.Default.History,
                label = "History",
                onClick = onHistory,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ExpandedActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1  -> "Just now"
        minutes < 60 -> "$minutes min ago"
        hours < 24   -> "$hours hr ago"
        days < 7     -> "$days days ago"
        else -> SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}

// ── Avatar ───────────────────────────────────────────────────────────────────

@Composable
internal fun ContactAvatar(name: String, photoUri: String?, size: Int) {
    Box(
        modifier         = Modifier
            .size(size.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier         = Modifier
                .matchParentSize()
                .background(avatarColor(name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text       = name.firstOrNull()?.uppercaseChar()?.toString() ?: "#",
                color      = Color.White,
                fontSize   = (size * 0.42f).sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (photoUri != null) {
            val context = LocalContext.current
            val imageRequest = remember(photoUri) {
                ImageRequest.Builder(context)
                    .data(photoUri)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .crossfade(150)
                    .size(size * 3)
                    .build()
            }
            AsyncImage(
                model              = imageRequest,
                contentDescription = null,
                modifier           = Modifier.matchParentSize(),
                contentScale       = ContentScale.Crop
            )
        }
    }
}

// ── Call type icon ────────────────────────────────────────────────────────────

@Composable
private fun CallTypeIcon(callType: Int, selected: Boolean) {
    val (icon, color) = when (callType) {
        CallLog.Calls.MISSED_TYPE ->
            Icons.AutoMirrored.Filled.CallMissed to if (selected) Color(0xFFFF6B6B) else Color(0xFFCC3333)
        CallLog.Calls.INCOMING_TYPE ->
            Icons.AutoMirrored.Filled.CallReceived to if (selected) AccentGreen else TextSecondary
        CallLog.Calls.OUTGOING_TYPE ->
            Icons.AutoMirrored.Filled.CallMade to if (selected) AccentGreen else TextSecondary
        else ->
            Icons.AutoMirrored.Filled.PhoneCallback to TextSecondary
    }
    Icon(
        imageVector        = icon,
        contentDescription = null,
        tint               = color,
        modifier           = Modifier.size(14.dp)
    )
}

// ── Secondary line ───────────────────────────────────────────────────────────

private fun buildSecondaryLine(contact: Contact): String {
    return if (contact.isRecent && contact.lastCallTime > 0L) {
        "${contact.number}  •  ${contact.lastCallTime.toTimeAgo()}"
    } else {
        contact.number
    }
}

// ── Date / time formatters ───────────────────────────────────────────────────

private fun Long.toDateLabel(): String {
    val callCal = Calendar.getInstance().also { it.timeInMillis = this }

    val todayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0);      set(Calendar.MILLISECOND, 0)
    }
    val yesterdayStart = (todayStart.clone() as Calendar).apply { add(Calendar.DATE, -1) }

    return when {
        callCal >= todayStart    -> "Today"
        callCal >= yesterdayStart -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(this))
    }
}

private fun Long.toTimeAgo(): String {
    val diff = System.currentTimeMillis() - this
    val min  = diff / 60_000
    val hr   = diff / 3_600_000
    val day  = diff / 86_400_000
    return when {
        diff < 60_000 -> "Just now"
        min  < 60     -> "${min}m ago"
        hr   < 24     -> "${hr}h ago"
        day  < 7      -> "${day}d ago"
        else          -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(this))
    }
}
