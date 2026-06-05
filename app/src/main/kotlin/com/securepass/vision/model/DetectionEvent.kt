package com.securepass.vision.model

/** Modelo de datos que representa una detección guardada en el historial y sincronizada. */
data class DetectionEvent(
    val id: Long = 0,
    val objectLabel: String,
    val confidence: Float,
    val timestamp: Long,
    val alertLevel: String = "HIGH",
    val userId: String,
    val userName: String,
    val eventId: Long,
    val eventName: String
)
