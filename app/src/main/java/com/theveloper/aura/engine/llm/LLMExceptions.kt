package com.theveloper.aura.engine.llm

class LLMNotInitializedException :
    IllegalStateException("Local model was not initialized.")

class ModelNotDownloadedException(modelId: String) :
    IllegalStateException("Model $modelId has not been downloaded.")

class GroqAPIKeyMissingException :
    IllegalStateException("Groq is not configured.")

class GroqAPIException(code: Int) :
    IllegalStateException("Groq returned HTTP $code.")

class TaskDSLParseException(rawResponse: String) :
    IllegalStateException("Could not parse TaskDSLOutput from: $rawResponse")
