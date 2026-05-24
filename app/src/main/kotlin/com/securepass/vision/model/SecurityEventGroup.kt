package com.securepass.vision.model

data class SecurityEventGroup(
    val id: Long = 0,
    val name: String, // Ejemplo: "Concierto Rock", "Estadio Nacional"
    val location: String,
    val prohibitedItems: String // Lista separada por comas: "Knife,Weapon,Bottle"
)
