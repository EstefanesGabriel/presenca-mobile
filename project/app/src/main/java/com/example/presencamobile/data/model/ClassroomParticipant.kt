package com.example.presencamobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClassroomParticipant(
    @SerialName("student_id") val studentId: String,
    val name: String,
    @SerialName("joined_at") val joinedAt: String
)

@Serializable
data class JoinClassroomResult(
    val classroom: Classroom,
    @SerialName("already_member") val alreadyMember: Boolean
)
