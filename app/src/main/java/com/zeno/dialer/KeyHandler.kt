package com.zeno.dialer

import android.view.KeyEvent
import android.os.SystemClock

class KeyHandler(
    private val viewModel: DialerViewModel,
    private val onFinish: () -> Unit
) {
    private var lastRepeatedDpadScrollAtMs: Long = 0L


    /**
     * Fast path for the Keypad tab: called with the unicode char from a
     * synthesized Alt-KeyEvent. If the device's key map produced a digit,
     * type it immediately. Navigation/control keys are forwarded to [handle].
     */
    fun handleKeypad(keyCode: Int, nativeAltChar: Int): Boolean {
        // Let control keys fall through to the normal handler
        if (isControlKey(keyCode)) return false

        val ch = keyCodeToPhoneChar(keyCode)
            ?: nativeAltChar.takeIf { it > 0 }?.toChar()
                ?.takeIf { it.isDigit() || it == '*' || it == '#' || it == '+' }
            ?: qwertyKeyCodeToDigit(keyCode)

        if (ch != null) { viewModel.typeUnicode(ch.code); return true }
        return false
    }

    private fun isControlKey(keyCode: Int): Boolean = keyCode in setOf(
        KeyEvent.KEYCODE_CALL, KeyEvent.KEYCODE_ENDCALL, KeyEvent.KEYCODE_MENU,
        KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_TAB
    )

    fun handle(
        keyCode: Int,
        unicodeChar: Int,
        altUnicodeChar: Int = 0,
        repeatCount: Int = 0
    ): Boolean = when (keyCode) {

        KeyEvent.KEYCODE_CALL -> {
            val state = viewModel.uiState.value
            when {
                state.query.isNotBlank() -> viewModel.callSelected()
                viewModel.currentTabIndex == 0 && state.favoriteSuggestions.isNotEmpty() ->
                    viewModel.callFavoriteSuggestionSelected()
                viewModel.currentTabIndex == 1 && state.results.isNotEmpty() ->
                    viewModel.callSelected()
                else -> viewModel.setCurrentTab(2)
            }
            true
        }

        KeyEvent.KEYCODE_ENDCALL -> {
            onFinish()
            true
        }

        KeyEvent.KEYCODE_MENU -> true

        KeyEvent.KEYCODE_BACK -> {
            if (viewModel.uiState.value.query.isNotEmpty()) {
                viewModel.clearQuery()
            } else {
                onFinish()
            }
            true
        }

        KeyEvent.KEYCODE_DPAD_UP -> {
            if (!shouldThrottleRepeatedDpadScroll(repeatCount)) {
                if (viewModel.currentTabIndex == 0) viewModel.nudgeFavoritesSuggestionUp()
                else viewModel.nudgeSelectionUp()
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (!shouldThrottleRepeatedDpadScroll(repeatCount)) {
                if (viewModel.currentTabIndex == 0) viewModel.nudgeFavoritesSuggestionDown()
                else viewModel.nudgeSelectionDown()
            }
            true
        }
        KeyEvent.KEYCODE_DPAD_LEFT  -> {
            if (viewModel.currentTabIndex != 1) viewModel.moveTabLeft()
            else viewModel.nudgeCursorLeft()
            true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (viewModel.currentTabIndex != 1) viewModel.moveTabRight()
            else viewModel.nudgeCursorRight()
            true
        }

        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER -> {
            when {
                viewModel.currentTabIndex == 0 -> {
                    val q = viewModel.uiState.value.query.trim()
                    if (q.isNotEmpty()) viewModel.callNumber(q)
                    else viewModel.callFavoriteSuggestionSelected()
                }
                viewModel.currentTabIndex == 1 && viewModel.uiState.value.query.isBlank() ->
                    viewModel.toggleExpandSelected()
                else -> viewModel.callSelected()
            }
            true
        }

        KeyEvent.KEYCODE_DEL    -> { viewModel.deleteChar(); true }
        KeyEvent.KEYCODE_ESCAPE -> { viewModel.clearQuery(); true }
        KeyEvent.KEYCODE_TAB    -> { viewModel.cycleFilter(); true }

        KeyEvent.KEYCODE_C -> {
            if (viewModel.isOnKeypad) {
                typeDigitOnKeypad(keyCode, unicodeChar, altUnicodeChar)
            } else if (viewModel.uiState.value.query.isEmpty()) {
                if (viewModel.currentTabIndex == 0) viewModel.setCurrentTab(1)
                viewModel.setFilterContacts(); true
            } else {
                if (viewModel.currentTabIndex == 0) viewModel.setCurrentTab(1)
                viewModel.typeUnicode(unicodeChar)
            }
        }
        KeyEvent.KEYCODE_R -> {
            if (viewModel.isOnKeypad) {
                typeDigitOnKeypad(keyCode, unicodeChar, altUnicodeChar)
            } else if (viewModel.uiState.value.query.isEmpty()) {
                if (viewModel.currentTabIndex == 0) viewModel.setCurrentTab(1)
                viewModel.setFilterRecents(); true
            } else {
                if (viewModel.currentTabIndex == 0) viewModel.setCurrentTab(1)
                viewModel.typeUnicode(unicodeChar)
            }
        }

        else -> {
            if (viewModel.isOnKeypad) {
                typeDigitOnKeypad(keyCode, unicodeChar, altUnicodeChar)
            } else {
                // If on Favorites tab, switch to Home before typing
                if (viewModel.currentTabIndex == 0 && unicodeChar > 0) {
                    viewModel.setCurrentTab(1)
                }
                viewModel.typeUnicode(unicodeChar)
            }
        }
    }

    /**
     * Resolves a key press to a phone digit on the Keypad tab.
     * Priority:
     *   1. Dedicated digit keycode (KEYCODE_0–9, STAR, POUND, PLUS)
     *   2. The unicode char itself if already a digit / symbol
     *   3. The Alt-layer unicode char if it's a digit / symbol
     *   4. BB QWERTY row mapping (Q=1, W=2, E=3, … P=0)
     */
    private fun typeDigitOnKeypad(keyCode: Int, unicodeChar: Int, altUnicodeChar: Int): Boolean {
        val ch = keyCodeToPhoneChar(keyCode)
            ?: unicodeChar.takeIf { it > 0 }?.toChar()
                ?.takeIf { it.isDigit() || it == '*' || it == '#' || it == '+' }
            ?: altUnicodeChar.takeIf { it > 0 }?.toChar()
                ?.takeIf { it.isDigit() || it == '*' || it == '#' || it == '+' }
            ?: qwertyKeyCodeToDigit(keyCode)
        if (ch != null) { viewModel.typeUnicode(ch.code); return true }
        return false
    }

    private fun keyCodeToPhoneChar(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_0     -> '0'
        KeyEvent.KEYCODE_1     -> '1'
        KeyEvent.KEYCODE_2     -> '2'
        KeyEvent.KEYCODE_3     -> '3'
        KeyEvent.KEYCODE_4     -> '4'
        KeyEvent.KEYCODE_5     -> '5'
        KeyEvent.KEYCODE_6     -> '6'
        KeyEvent.KEYCODE_7     -> '7'
        KeyEvent.KEYCODE_8     -> '8'
        KeyEvent.KEYCODE_9     -> '9'
        KeyEvent.KEYCODE_STAR  -> '*'
        KeyEvent.KEYCODE_POUND -> '#'
        KeyEvent.KEYCODE_PLUS  -> '+'
        else                   -> null
    }

    /**
     * BB Classic QWERTY keyboard layout:
     *
     *   #=Q  1=W  2=E  3=R
     *   *=A  4=S  5=D  6=F
     *        7=Z  8=X  9=C
     *        0 = dedicated key
     */
    private fun qwertyKeyCodeToDigit(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_Q -> '#'
        KeyEvent.KEYCODE_A -> '*'
        KeyEvent.KEYCODE_W -> '1'
        KeyEvent.KEYCODE_E -> '2'
        KeyEvent.KEYCODE_R -> '3'
        KeyEvent.KEYCODE_S -> '4'
        KeyEvent.KEYCODE_D -> '5'
        KeyEvent.KEYCODE_F -> '6'
        KeyEvent.KEYCODE_Z -> '7'
        KeyEvent.KEYCODE_X -> '8'
        KeyEvent.KEYCODE_C -> '9'
        else               -> null
    }

    private fun shouldThrottleRepeatedDpadScroll(repeatCount: Int): Boolean {
        // Keep first press immediate; only pace auto-repeat while key is held.
        if (repeatCount <= 0) return false
        val now = SystemClock.uptimeMillis()
        val minIntervalMs = 90L
        if (now - lastRepeatedDpadScrollAtMs < minIntervalMs) return true
        lastRepeatedDpadScrollAtMs = now
        return false
    }
}
