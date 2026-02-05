package com.anymind.anymind.util

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter


enum class TagFilterMode {
    AND,
    OR
}

enum class GroupingMode(val title: String) {
    DAY("Day"),
    WEEK("Week"),
    MONTH("Month");

    fun keyFrom(instant: Instant): String {
        return when (this) {
            DAY -> dayFormatter.format(instant.atZone(ZoneOffset.UTC))
            WEEK -> WeekKey.from(instant)
            MONTH -> monthFormatter.format(instant.atZone(ZoneOffset.UTC))
        }
    }

    companion object {
        private val dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val monthFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
