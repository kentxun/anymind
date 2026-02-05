package com.anymind.anymind.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

object DateCodec {
    private val displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    fun encode(instant: Instant): String = instant.toString()

    fun nowString(): String = encode(Instant.now())

    fun decode(value: String): Instant {
        return try {
            Instant.parse(value)
        } catch (ex: Exception) {
            Instant.EPOCH
        }
    }

    fun decodeOptional(value: String?): Instant? {
        if (value == null || value.isBlank()) {
            return null
        }
        return try {
            Instant.parse(value)
        } catch (ex: Exception) {
            null
        }
    }

    fun formatDisplay(instant: Instant): String = displayFormatter.format(instant)
}

object WeekKey {
    fun from(instant: Instant): String {
        val date = instant.atZone(ZoneOffset.UTC).toLocalDate()
        val weekFields = WeekFields.ISO
        val week = date.get(weekFields.weekOfWeekBasedYear())
        val year = date.get(weekFields.weekBasedYear())
        return String.format("%04d-W%02d", year, week)
    }
}
