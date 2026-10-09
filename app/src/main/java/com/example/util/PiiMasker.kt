package com.example.util

object PiiMasker {
    /**
     * Masks National ID (13 digits).
     * Format: X-XXXX-XXXXX-XX-X -> X-XXXX-XXXXX-XX-5
     */
    fun maskNationalId(id: String?): String {
        if (id.isNullOrBlank() || id.length != 13) return id ?: ""
        return "X-XXXX-XXXXX-XX-" + id.last()
    }

    /**
     * Masks Phone Number.
     * Shows only the last 4 digits.
     */
    fun maskPhoneNumber(phone: String?): String {
        if (phone.isNullOrBlank() || phone.length < 4) return phone ?: ""
        return "***-***-" + phone.takeLast(4)
    }
}
