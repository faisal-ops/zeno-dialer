package com.zeno.dialer.data

import android.content.Context

class BlockedNumbersRepo(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAll(): Set<String> {
        val raw = prefs.getString(KEY_BLOCKED_NUMBERS, "").orEmpty()
        if (raw.isBlank()) return emptySet()
        return raw.split(SEPARATOR)
            .map { it.trim() }
            .map { normalize(it) }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun add(number: String) {
        val normalized = normalize(number)
        if (normalized.isBlank()) return
        val values = getAll().toMutableSet()
        values.add(normalized)
        save(values)
    }

    fun remove(number: String) {
        val normalized = normalize(number)
        if (normalized.isBlank()) return
        val values = getAll().toMutableSet()
        values.remove(normalized)
        save(values)
    }

    fun contains(number: String): Boolean = normalize(number) in getAll()

    private fun save(values: Set<String>) {
        prefs.edit().putString(KEY_BLOCKED_NUMBERS, values.joinToString(SEPARATOR)).apply()
    }

    private fun normalize(number: String): String = number.filter { it.isDigit() }

    private companion object {
        private const val PREFS_NAME = "zeno_settings"
        private const val KEY_BLOCKED_NUMBERS = "blocked_numbers"
        private const val SEPARATOR = "|:|"
    }
}
