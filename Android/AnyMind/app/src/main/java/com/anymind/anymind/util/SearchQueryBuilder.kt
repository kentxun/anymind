package com.anymind.anymind.util

object SearchQueryBuilder {
    fun make(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return null
        }
        val tokens = trimmed.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return null
        }
        val cleaned = tokens.mapNotNull { token ->
            val sanitized = token.replace("\"", "").trim().trimStart('#')
            if (sanitized.isEmpty()) null else sanitized
        }
        if (cleaned.isEmpty()) {
            return null
        }
        return cleaned.joinToString(" AND ") { "${it}*" }
    }
}
