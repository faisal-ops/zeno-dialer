package com.zeno.dialer

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeno.dialer.data.Contact
import com.zeno.dialer.data.BlockedNumbersRepo
import com.zeno.dialer.data.ContactsRepo
import com.zeno.dialer.data.FavoritesRepo
import com.zeno.dialer.data.FilterMode
import com.zeno.dialer.data.RecentsRepo
import com.zeno.dialer.data.SearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@androidx.compose.runtime.Immutable
data class DialerUiState(
    val query: String = "",
    val cursorPos: Int = 0,
    val results: List<Contact> = emptyList(),
    val selectedIndex: Int = 0,
    val filterMode: FilterMode = FilterMode.ALL,
    val pinnedFavorites: List<Contact> = emptyList(),
    val favorites: List<Contact> = emptyList(),
    val favoriteSuggestions: List<Contact> = emptyList(),
    val blockedNumbers: Set<String> = emptySet(),
    val currentTabIndex: Int = 1,
    val isKeypadActive: Boolean = false,
    /** D-pad selection index within [favoriteSuggestions] on Favorites tab only */
    val favoritesSuggestionIndex: Int = 0,
) {
    /** Query string with a visual "|" cursor inserted at cursorPos */
    val displayQuery: String
        get() = if (query.isEmpty()) ""
        else query.substring(0, cursorPos) + "|" + query.substring(cursorPos)
}

@OptIn(FlowPreview::class)
class DialerViewModel(application: Application) : AndroidViewModel(application) {

    private val contactsRepo = ContactsRepo(application)
    private val recentsRepo = RecentsRepo(application)
    private val favoritesRepo = FavoritesRepo(application)
    private val blockedNumbersRepo = BlockedNumbersRepo(application)
    private val searchEngine = SearchEngine(contactsRepo, recentsRepo)

    private val _uiState = MutableStateFlow(DialerUiState())

    // Observes the call log for changes (new calls, deletions) and triggers a live refresh.
    private val callLogObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = forceRefresh()
    }
    val uiState: StateFlow<DialerUiState> = _uiState.asStateFlow()

    // Activity collects this and fires the intent — keeps calling out of the ViewModel
    private val _callEvent = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val callEvent: SharedFlow<String> = _callEvent.asSharedFlow()

    /** Emitted when D-pad center/enter is pressed on Home tab to toggle expand on selected item. */
    private val _toggleExpandEvent = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val toggleExpandEvent: SharedFlow<Int> = _toggleExpandEvent.asSharedFlow()

    fun toggleExpandSelected() {
        _toggleExpandEvent.tryEmit(_uiState.value.selectedIndex)
    }

    private var callLogObserverRegistered = false

    init {
        // Reactive search — safe to start immediately, results stay empty until
        // permissions are granted and onPermissionsReady() triggers the first load.
        viewModelScope.launch {
            _uiState
                .map { it.query to it.filterMode }
                .distinctUntilChanged()
                .debounce(80L)
                .collectLatest { (query, mode) ->
                    val results = try {
                        withContext(Dispatchers.IO) { searchEngine.search(query, mode) }
                    } catch (_: SecurityException) { emptyList() }
                    _uiState.update { it.copy(results = results, selectedIndex = 0) }
                }
        }

        // Register call log observer only if permission is already held (e.g. subsequent launches).
        if (hasCallLogPermission()) {
            registerCallLogObserver()
            refreshFavorites()
        }
        refreshBlockedNumbers()
    }

    /**
     * Called by MainActivity after READ_CALL_LOG / READ_CONTACTS permissions are granted.
     * Safe to call multiple times — observer is only registered once.
     */
    fun onPermissionsReady() {
        registerCallLogObserver()
        refreshFavorites()
        refreshBlockedNumbers()
    }

    private fun registerCallLogObserver() {
        if (callLogObserverRegistered) return
        try {
            getApplication<Application>().contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI, /* notifyForDescendants= */ true, callLogObserver
            )
            callLogObserverRegistered = true
        } catch (_: SecurityException) { }
    }

    private fun hasCallLogPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED

    // ── Query editing ────────────────────────────────────────────────────────

    fun typeUnicode(code: Int): Boolean {
        if (code <= 0) return false
        val char = code.toChar()
        if (char.isISOControl()) return false
        val state = _uiState.value
        val newQuery = state.query.substring(0, state.cursorPos) + char +
                state.query.substring(state.cursorPos)
        _uiState.update { it.copy(query = newQuery, cursorPos = state.cursorPos + 1) }
        return true
    }

    fun deleteChar() {
        val state = _uiState.value
        if (state.cursorPos == 0) return
        val newQuery = state.query.substring(0, state.cursorPos - 1) +
                state.query.substring(state.cursorPos)
        _uiState.update { it.copy(query = newQuery, cursorPos = state.cursorPos - 1) }
    }

    fun nudgeCursorLeft()  = _uiState.update { it.copy(cursorPos = (it.cursorPos - 1).coerceAtLeast(0)) }
    fun nudgeCursorRight() = _uiState.update { it.copy(cursorPos = (it.cursorPos + 1).coerceAtMost(it.query.length)) }

    fun clearQuery() {
        _uiState.update { it.copy(query = "", cursorPos = 0, selectedIndex = 0) }
    }

    /** Replace the entire query at once (used by voice search result). */
    fun setQueryDirect(text: String) {
        _uiState.update { it.copy(query = text, cursorPos = text.length, selectedIndex = 0) }
    }

    fun copyKeypadNumberToClipboard(): Boolean {
        val text = _uiState.value.query
        if (text.isEmpty()) return false
        val app = getApplication<Application>()
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Phone number", text))
        return true
    }

    fun pasteIntoKeypadFromClipboard(): Boolean {
        val app = getApplication<Application>()
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return false
        if (clip.itemCount == 0) return false
        val raw = clip.getItemAt(0).coerceToText(app)?.toString() ?: return false
        val sanitized = sanitizePastedDialText(raw)
        if (sanitized.isEmpty()) return false
        val state = _uiState.value
        val pos = state.cursorPos.coerceIn(0, state.query.length)
        val newQuery = state.query.substring(0, pos) + sanitized + state.query.substring(pos)
        val newCursor = pos + sanitized.length
        _uiState.update { it.copy(query = newQuery, cursorPos = newCursor) }
        return true
    }

    private fun sanitizePastedDialText(raw: String): String {
        var s = raw.trim().lineSequence().firstOrNull()?.trim().orEmpty()
        if (s.length >= 4 && s.startsWith("tel:", ignoreCase = true)) {
            s = s.substring(4).trim()
        }
        return buildString {
            for (c in s) {
                when {
                    c.isDigit() -> append(c)
                    c == '+' || c == '*' || c == '#' -> append(c)
                    c == ',' || c == ';' -> append(c)
                    else -> Unit
                }
            }
        }
    }

    // ── Selection ────────────────────────────────────────────────────────────

    fun nudgeSelectionUp() {
        _uiState.update { it.copy(selectedIndex = (it.selectedIndex - 1).coerceAtLeast(0)) }
    }

    fun nudgeSelectionDown() {
        _uiState.update { state ->
            val max = (state.results.size - 1).coerceAtLeast(0)
            state.copy(selectedIndex = (state.selectedIndex + 1).coerceAtMost(max))
        }
    }

    fun nudgeFavoritesSuggestionUp() {
        _uiState.update { state ->
            if (state.favoriteSuggestions.isEmpty()) return@update state
            state.copy(
                favoritesSuggestionIndex =
                    (state.favoritesSuggestionIndex - 1).coerceAtLeast(0)
            )
        }
    }

    fun nudgeFavoritesSuggestionDown() {
        _uiState.update { state ->
            if (state.favoriteSuggestions.isEmpty()) return@update state
            val max = state.favoriteSuggestions.lastIndex
            state.copy(
                favoritesSuggestionIndex = (state.favoritesSuggestionIndex + 1).coerceAtMost(max)
            )
        }
    }

    /** Call the currently focused suggestion on Favorites tab (D-pad center / call when applicable). */
    fun callFavoriteSuggestionSelected() {
        val state = _uiState.value
        val list = state.favoriteSuggestions
        if (list.isEmpty()) return
        val idx = state.favoritesSuggestionIndex.coerceIn(0, list.lastIndex)
        emitCall(list[idx].number)
    }

    fun selectItem(index: Int) {
        _uiState.update { it.copy(selectedIndex = index) }
    }

    // ── Filter ───────────────────────────────────────────────────────────────

    fun setFilter(mode: FilterMode) {
        _uiState.update { it.copy(filterMode = mode, selectedIndex = 0) }
    }

    fun cycleFilter() {
        val next = when (_uiState.value.filterMode) {
            FilterMode.ALL      -> FilterMode.MISSED
            FilterMode.MISSED   -> FilterMode.CONTACTS
            FilterMode.CONTACTS -> FilterMode.RECENTS
            FilterMode.RECENTS  -> FilterMode.ALL
        }
        _uiState.update { it.copy(filterMode = next, selectedIndex = 0) }
    }

    // Keep legacy key shortcuts working
    fun setFilterContacts() = setFilter(FilterMode.CONTACTS)
    fun setFilterRecents()  = setFilter(FilterMode.RECENTS)

    // ── Tab state ─────────────────────────────────────────────────────────────

    @Volatile
    var isOnKeypad = false
        private set

    fun setKeypadActive(active: Boolean) {
        isOnKeypad = active
        _uiState.update { it.copy(isKeypadActive = active) }
    }

    val currentTabIndex: Int
        get() = _uiState.value.currentTabIndex

    fun setCurrentTab(index: Int) {
        val clamped = index.coerceIn(0, 2)
        _uiState.update {
            it.copy(
                currentTabIndex = clamped,
                favoritesSuggestionIndex = if (clamped == 0) 0 else it.favoritesSuggestionIndex
            )
        }
        isOnKeypad = clamped == 2
        _uiState.update { it.copy(isKeypadActive = isOnKeypad) }
    }

    fun moveTabLeft() = setCurrentTab(currentTabIndex - 1)
    fun moveTabRight() = setCurrentTab(currentTabIndex + 1)

    // ── Live refresh ─────────────────────────────────────────────────────────

    /** Re-runs the current search and reloads favorites without changing any user-visible state. */
    private fun forceRefresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val state = _uiState.value
            val results = searchEngine.search(state.query, state.filterMode)
            _uiState.update { it.copy(results = results) }
        }
        refreshFavorites()
        refreshBlockedNumbers()
    }

    override fun onCleared() {
        super.onCleared()
        getApplication<Application>().contentResolver.unregisterContentObserver(callLogObserver)
    }

    // ── Call history ─────────────────────────────────────────────────────────

    fun deleteCallLogForNumber(number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.delete(
                    CallLog.Calls.CONTENT_URI,
                    "${CallLog.Calls.NUMBER} = ?",
                    arrayOf(number)
                )
            } catch (_: Exception) { }
            // ContentObserver fires forceRefresh() automatically
        }
    }

    fun clearCallHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                getApplication<Application>().contentResolver.delete(
                    CallLog.Calls.CONTENT_URI, null, null
                )
            } catch (_: Exception) { }
        }
        refreshFavorites()
    }

    fun addBlockedNumber(number: String) {
        blockedNumbersRepo.add(number)
        refreshBlockedNumbers()
    }

    fun removeBlockedNumber(number: String) {
        blockedNumbersRepo.remove(number)
        refreshBlockedNumbers()
    }

    fun isBlocked(number: String): Boolean = blockedNumbersRepo.contains(number)

    private fun refreshBlockedNumbers() {
        _uiState.update { it.copy(blockedNumbers = blockedNumbersRepo.getAll()) }
    }

    fun pinFavorite(contact: Contact) {
        favoritesRepo.add(contact.number)
        refreshFavorites()
    }

    fun unpinFavorite(number: String) {
        favoritesRepo.remove(number)
        refreshFavorites()
    }

    private fun refreshFavorites() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pinnedNumbers = favoritesRepo.getPinnedNumbers()
                val starredContacts = contactsRepo.getStarredContacts()
                val recents = searchEngine.getTopRecents(20)
                val contacts = contactsRepo.search("")
                val pool = (recents + contacts).distinctBy { normalize(it.number) }

                // App-pinned contacts
                val appPinned = pinnedNumbers.map { pinnedNum ->
                    pool.firstOrNull { normalize(it.number) == normalize(pinnedNum) }
                        ?: Contact(name = pinnedNum, number = pinnedNum)
                }

                // Merge: starred contacts first, then app-pinned (skip duplicates)
                val pinned = (starredContacts + appPinned)
                    .distinctBy { normalize(it.number) }

                val suggestions = recents.filter { r ->
                    pinned.none { normalize(it.number) == normalize(r.number) }
                }.take(12)

                val combined = (pinned + suggestions).distinctBy { normalize(it.number) }.take(12)

                _uiState.update { prev ->
                    val maxIdx = (suggestions.size - 1).coerceAtLeast(0)
                    prev.copy(
                        pinnedFavorites = pinned,
                        favorites = combined,
                        favoriteSuggestions = suggestions,
                        favoritesSuggestionIndex = prev.favoritesSuggestionIndex.coerceIn(0, maxIdx)
                    )
                }
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        pinnedFavorites = emptyList(),
                        favorites = emptyList(),
                        favoriteSuggestions = emptyList(),
                        favoritesSuggestionIndex = 0
                    )
                }
            }
        }
    }

    private fun normalize(number: String): String = number.filter { it.isDigit() }

    // ── Calling ──────────────────────────────────────────────────────────────

    fun callSelected() {
        val state = _uiState.value
        val number = when {
            state.results.isNotEmpty() && state.selectedIndex < state.results.size ->
                state.results[state.selectedIndex].number
            state.query.isNotBlank() -> state.query
            else -> return
        }
        emitCall(number)
    }

    fun callItem(index: Int) {
        val results = _uiState.value.results
        if (index < results.size) emitCall(results[index].number)
    }

    fun callNumber(number: String) = emitCall(number)

    private fun emitCall(number: String) {
        viewModelScope.launch { _callEvent.emit(number) }
    }
}
