package com.zeno.dialer.data

class SearchEngine(
    private val contactsRepo: ContactsRepo,
    private val recentsRepo: RecentsRepo
) {

    fun search(query: String, mode: FilterMode): List<Contact> {
        val contacts = contactsRepo.search(query).take(15)
        val recents = when (mode) {
            FilterMode.CONTACTS -> emptyList()
            FilterMode.MISSED   -> recentsRepo.search(query, missedOnly = true, limit = 20)
            else                -> recentsRepo.search(query, limit = 20)
        }
        return merge(contacts, recents)
    }

    /** Top N recent contacts for the Favorites row (no query filter). */
    fun getTopRecents(n: Int): List<Contact> = recentsRepo.search("", limit = n)

    private fun merge(contacts: List<Contact>, recents: List<Contact>): List<Contact> {
        val merged = mutableListOf<Contact>()
        val seen   = mutableSetOf<String>()

        for (recent in recents) {
            val key = recent.number.filter { it.isDigit() }
            if (key in seen) continue
            seen.add(key)

            val enriched = contacts.find { it.number.filter { c -> c.isDigit() } == key }
            merged.add(
                if (enriched != null && enriched.name != recent.number)
                    recent.copy(name = enriched.name, id = enriched.id)
                else
                    recent
            )
        }

        for (contact in contacts) {
            val key = contact.number.filter { it.isDigit() }
            if (key !in seen) {
                seen.add(key)
                merged.add(contact)
            }
        }

        return merged
    }
}
