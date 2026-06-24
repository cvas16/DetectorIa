package com.securepass.vision.model

data class User(
    val id: String = "0",
    val name: String,
    val username: String,
    val password: String,
    val licenseKey: String,
    val groupId: String = "0",
    val role: String = "staff"
)
