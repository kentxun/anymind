package com.anymind.anymind.util

object RecordPreview {
    fun make(content: String): String {
        val lines = content.split("\n", ignoreCase = false, limit = 0)
        var candidate: String? = null
        for (line in lines) {
            if (TagParser.isTagOnlyLine(line)) {
                continue
            }
            candidate = line
            break
        }
        val trimmed = (candidate ?: content).trim()
        if (trimmed.isEmpty()) {
            return "(empty)"
        }
        return if (trimmed.length > 120) {
            trimmed.substring(0, 120) + "..."
        } else {
            trimmed
        }
    }
}
