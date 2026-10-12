package com.example.presencamobile.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class UserRole { STUDENT, TEACHER }

@Serializable
data class UserProfile(val id: String, val name: String, val role: UserRole)
