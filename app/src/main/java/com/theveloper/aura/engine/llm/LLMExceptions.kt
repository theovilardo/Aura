package com.theveloper.aura.engine.llm

class LLMNotInitializedException :
    IllegalStateException("El modelo local no fue inicializado.")

class ModelNotDownloadedException(modelId: String) :
    IllegalStateException("El modelo $modelId no fue descargado.")

class GroqAPIKeyMissingException :
    IllegalStateException("Groq no está configurado.")

class GroqAPIException(code: Int) :
    IllegalStateException("Groq devolvió HTTP $code.")

class TaskDSLParseException(rawResponse: String) :
    IllegalStateException("No se pudo parsear TaskDSLOutput desde: $rawResponse")
