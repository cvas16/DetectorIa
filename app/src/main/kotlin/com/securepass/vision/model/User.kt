package com.securepass.vision.model

data class User(
    val id: Long = 0,
    val name: String,
    val username: String,
    val password: String,
    val licenseKey: String,
    val groupId: Long = 0 // ID del evento/grupo asignado
)
