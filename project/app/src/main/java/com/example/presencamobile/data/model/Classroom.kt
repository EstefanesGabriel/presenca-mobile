package com.example.presencamobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Classroom(
    val id: String,
    @SerialName("teacher_id") val teacherId: String,
    val name: String,
    val subject: String,
    val semester: String,
    @SerialName("access_code") val accessCode: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("is_active") val isActive: Boolean = true
)

// O mesmo ID é reutilizado se uma resposta se perder e o usuário tentar novamente.
data class NewClassroom(
    val id: String,
    val name: String,
    val subject: String,
    val semester: String,
    val accessPassword: String
)
