package com.elite.assistant.actions

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Phone

/** A contact's display name and phone number. */
data class FoundContact(val name: String, val number: String)

/** Looks up a phone number by spoken name. Needs READ_CONTACTS. */
class ContactFinder(private val context: Context) {

    fun find(spokenName: String): FoundContact? {
        val query = spokenName.trim()
        if (query.isEmpty()) return null
        val matches = mutableListOf<FoundContact>()
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER),
            "${Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$query%"),
            "${Phone.DISPLAY_NAME} ASC",
        )?.use { c ->
            while (c.moveToNext() && matches.size < 20) {
                matches += FoundContact(c.getString(0).orEmpty(), c.getString(1).orEmpty())
            }
        }
        // Prefer an exact name, then one that starts with what was said.
        return matches.firstOrNull { it.name.equals(query, ignoreCase = true) }
            ?: matches.firstOrNull { it.name.startsWith(query, ignoreCase = true) }
            ?: matches.firstOrNull()
    }

    companion object {
        /** True when [text] is already a phone number rather than a name. */
        fun looksLikeNumber(text: String) = text.count { it.isDigit() } >= 5 &&
            text.all { it.isDigit() || it in "+-() " }

        /**
         * WhatsApp needs the number with country code and digits only.
         * Numbers saved without one are assumed to be Indian (+91); change [defaultCountryCode]
         * if you live elsewhere.
         */
        fun toWhatsAppNumber(raw: String, defaultCountryCode: String = "91"): String {
            val plus = raw.trim().startsWith("+")
            var digits = raw.filter { it.isDigit() }
            if (plus) return digits
            if (digits.startsWith("00")) return digits.drop(2)
            digits = digits.trimStart('0')
            return if (digits.length <= 10) defaultCountryCode + digits else digits
        }
    }
}
