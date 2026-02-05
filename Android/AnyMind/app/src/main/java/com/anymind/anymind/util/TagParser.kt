package com.anymind.anymind.util

object TagParser {
    private val tagRegex = Regex("#([\\p{L}\\p{N}_-]+)")
    private val systemTagSet = setOf("temp", "longterm", "p1", "p2", "conflict")

    fun extractTags(content: String): List<String> {
        val tags = tagRegex.findAll(content).mapNotNull { match ->
            val raw = match.groupValues.getOrNull(1)?.lowercase()?.trim()
            if (raw.isNullOrEmpty()) null else "#" + raw
        }.toSet()
        return tags.sorted()
    }

    fun splitTags(tags: List<String>): Pair<List<String>, List<String>> {
        val system = mutableSetOf<String>()
        val user = mutableSetOf<String>()
        for (tag in tags) {
            val trimmed = tag.trim().lowercase()
            val name = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
            if (systemTagSet.contains(name)) {
                system.add("#" + name)
            } else if (name.isNotEmpty()) {
                user.add("#" + name)
            }
        }
        return system.sorted() to user.sorted()
    }

    fun isSystemTag(tag: String): Boolean {
        val trimmed = tag.trim().lowercase()
        val name = if (trimmed.startsWith("#")) trimmed.substring(1) else trimmed
        return systemTagSet.contains(name)
    }

    fun isTagOnlyLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            return true
        }
        val stripped = tagRegex.replace(trimmed, "")
        val cleaned = stripped.replace(",", " ").trim()
        return cleaned.isEmpty()
    }

    fun parseFilterInput(input: String): List<String> {
        val tags = extractTags(input)
        if (tags.isNotEmpty()) {
            return tags
        }
        val tokens = input.split(',', ' ', '\n', '\t')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        return tokens.map { if (it.startsWith("#")) it else "#" + it }.toSet().sorted()
    }
}
