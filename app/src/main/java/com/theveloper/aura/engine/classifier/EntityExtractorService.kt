package com.theveloper.aura.engine.classifier

import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import com.google.mlkit.nl.entityextraction.DateTimeEntity
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityExtractorService @Inject constructor() {
    private val extractor: EntityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.SPANISH)
            .build()
    )

    suspend fun downloadModelIfNeeded() {
        runCatching {
            extractor.downloadModelIfNeeded().await()
        }
    }

    suspend fun extract(text: String): ExtractedEntities {
        val dateTimes = runCatching {
            downloadModelIfNeeded()
            val params = EntityExtractionParams.Builder(text).build()
            val annotations = extractor.annotate(params).await()
            annotations
                .flatMap { it.entities }
                .filterIsInstance<DateTimeEntity>()
                .map { it.timestampMillis }
                .distinct()
        }.getOrDefault(emptyList())

        val numbers = NUMBER_REGEX.findAll(text)
            .mapNotNull { it.value.replace(',', '.').toDoubleOrNull() }
            .toList()

        val locations = LOCATION_REGEX.findAll(text)
            .mapNotNull { match -> match.groups[1]?.value?.trim() }
            .distinct()
            .toList()

        return ExtractedEntities(
            dateTimes = dateTimes,
            numbers = numbers,
            locations = locations
        )
    }
}

data class ExtractedEntities(
    val dateTimes: List<Long> = emptyList(),
    val numbers: List<Double> = emptyList(),
    val locations: List<String> = emptyList()
)

private val NUMBER_REGEX = Regex("""\d+(?:[.,]\d+)?""")
private val LOCATION_REGEX = Regex("""\b(?:a|en|para)\s+([A-ZÁÉÍÓÚÑ][\p{L}]+(?:\s+[A-ZÁÉÍÓÚÑ][\p{L}]+)*)""")
