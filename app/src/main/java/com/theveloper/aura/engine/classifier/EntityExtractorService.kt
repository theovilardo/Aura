package com.theveloper.aura.engine.classifier

import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityExtractorService @Inject constructor() {

    suspend fun downloadModelIfNeeded() = Unit

    suspend fun extract(text: String): ExtractedEntities {
        val dateTimes = extractDateTimes(text)

        val numbers = NUMBER_REGEX.findAll(text)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .distinct()
            .toList()

        return ExtractedEntities(
            dateTimes = dateTimes,
            numbers = numbers,
            locations = emptyList()
        )
    }

    private fun extractDateTimes(text: String): List<Long> {
        return buildList {
            YEAR_FIRST_DATE_TIME_REGEX.findAll(text).forEach { match ->
                parseYearFirstDateTime(match.groupValues)?.let(::add)
            }
            NUMERIC_DATE_TIME_REGEX.findAll(text).forEach { match ->
                parseNumericDateTime(match.groupValues)?.let(::add)
            }
        }.distinct()
    }

    private fun parseYearFirstDateTime(groups: List<String>): Long? {
        val year = groups[1].toIntOrNull() ?: return null
        val month = groups[2].toIntOrNull() ?: return null
        val day = groups[3].toIntOrNull() ?: return null
        val hour = groups[4].toIntOrNull() ?: 0
        val minute = groups[5].toIntOrNull() ?: 0
        return toEpochMillis(year, month, day, hour, minute)
    }

    private fun parseNumericDateTime(groups: List<String>): Long? {
        val first = groups[1].toIntOrNull() ?: return null
        val second = groups[2].toIntOrNull() ?: return null
        val yearText = groups[3].takeIf { it.isNotBlank() }
        val hour = groups[4].toIntOrNull() ?: 0
        val minute = groups[5].toIntOrNull() ?: 0

        val currentYear = LocalDate.now().year
        val year = yearText
            ?.toIntOrNull()
            ?.let { rawYear ->
                if (rawYear >= 100) rawYear else (currentYear / 100) * 100 + rawYear
            }
            ?: currentYear

        val resolved = when {
            first in 13..31 && second in 1..12 -> Triple(year, second, first)
            second in 13..31 && first in 1..12 -> Triple(year, first, second)
            else -> null
        } ?: return null

        return toEpochMillis(
            year = resolved.first,
            month = resolved.second,
            day = resolved.third,
            hour = hour,
            minute = minute
        )
    }

    private fun toEpochMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int
    ): Long? {
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrElse { error ->
            if (error is DateTimeException) null else throw error
        }
    }
}

data class ExtractedEntities(
    val dateTimes: List<Long> = emptyList(),
    val numbers: List<Double> = emptyList(),
    val locations: List<String> = emptyList()
)

private val NUMBER_REGEX = Regex("""\d+(?:[.,]\d+)?""")
private val YEAR_FIRST_DATE_TIME_REGEX = Regex(
    """\b(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?:[ T](\d{1,2}):(\d{2}))?\b"""
)
private val NUMERIC_DATE_TIME_REGEX = Regex(
    """\b(\d{1,2})[./-](\d{1,2})(?:[./-](\d{2,4}))?(?:[ T](\d{1,2}):(\d{2}))?\b"""
)
