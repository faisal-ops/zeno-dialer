package com.zeno.dialer.data

/**
 * Lightweight fuzzy scorer.
 *
 * Scoring tiers (higher = better match):
 *   100 – exact match
 *    90 – target starts with query
 *    80 – any word in target starts with query  ("jo" → "John Doe")
 *    70 – target contains query as substring
 *    40 – query chars appear in order in target ("jhn" → "John")
 *     0 – no match
 */
object FuzzySearch {

    fun score(query: String, target: String): Int {
        if (query.isEmpty()) return 50
        val q = query.lowercase()
        val t = target.lowercase()
        return when {
            t == q                                                    -> 100
            t.startsWith(q)                                           -> 90
            t.split(Regex("\\s+")).any { it.startsWith(q) }          -> 80
            t.contains(q)                                             -> 70
            charSequenceMatch(q, t)                                   -> 40
            else                                                      -> 0
        }
    }

    fun matches(query: String, name: String, number: String): Boolean {
        if (query.isEmpty()) return true
        return score(query, name) > 0 || score(query, number) > 0
    }

    /** Rank [contacts] by best match score against [query]. Zero-score entries are dropped. */
    fun rank(query: String, contacts: List<Contact>): List<Contact> {
        if (query.isEmpty()) return contacts
        return contacts
            .map { c -> c to maxOf(score(query, c.name), score(query, c.number)) }
            .filter { (_, s) -> s > 0 }
            .sortedByDescending { (_, s) -> s }
            .map { (c, _) -> c }
    }

    /** True if every char in [query] appears in [target] in the same order. */
    private fun charSequenceMatch(query: String, target: String): Boolean {
        var qi = 0
        for (c in target) {
            if (qi < query.length && c == query[qi]) qi++
        }
        return qi == query.length
    }
}
