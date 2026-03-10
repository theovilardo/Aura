package com.theveloper.aura.engine.classifier

import com.google.mlkit.nl.entityextraction.Entity
import com.google.mlkit.nl.entityextraction.EntityAnnotation
import com.google.mlkit.nl.entityextraction.EntityExtraction
import com.google.mlkit.nl.entityextraction.EntityExtractionParams
import com.google.mlkit.nl.entityextraction.EntityExtractor
import com.google.mlkit.nl.entityextraction.EntityExtractorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EntityExtractorService @Inject constructor() {
    private val extractor: EntityExtractor = EntityExtraction.getClient(
        EntityExtractorOptions.Builder(EntityExtractorOptions.SPANISH)
            .build()
    )

    suspend fun downloadModelIfNeeded(): Boolean {
        return runCatching {
            extractor.downloadModelIfNeeded().await()
            true
        }.getOrDefault(false)
    }

    suspend fun extractEntities(text: String): List<Entity> {
        return runCatching {
            val params = EntityExtractionParams.Builder(text).build()
            val annotations = extractor.annotate(params).await()
            annotations.flatMap { it.entities }
        }.getOrDefault(emptyList())
    }
}
