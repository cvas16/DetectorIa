package com.securepass.vision.model

data class SecurityEventGroup(
    val id: String? = null, // MockAPI usa IDs como Strings
    val name: String,
    val location: String,
    val prohibitedItems: String,
    val status: String = "Activo",
    val timestamp: Long = System.currentTimeMillis()
)
