package com.example.presencamobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LessonStatus { SCHEDULED, OPEN, CLOSED, CANCELLED }

@Serializable
data class Lesson(
    val id: String,
    @SerialName("classroom_id") val classroomId: String,
    val title: String,
    @SerialName("starts_at") val startsAt: String,
    val status: LessonStatus,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class AttendanceQr(
    val id: String,
    @SerialName("lesson_id") val lessonId: String,
    val token: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("radius_m") val radiusMeters: Int,
    @SerialName("is_active") val isActive: Boolean
)

@Serializable
data class AttendanceRecord(
    @SerialName("student_id") val studentId: String,
    val name: String,
    @SerialName("recorded_at") val recordedAt: String? = null,
    @SerialName("distance_m") val distanceMeters: Double? = null
)

@Serializable
data class AttendanceReceipt(
    @SerialName("lesson_id") val lessonId: String,
    @SerialName("already_recorded") val alreadyRecorded: Boolean,
    @SerialName("distance_m") val distanceMeters: Double
)

@Serializable
enum class AttendanceStatus { PRESENT, ABSENT, PENDING }

@Serializable
data class AttendanceHistory(
    @SerialName("lesson_id") val lessonId: String,
    val title: String,
    @SerialName("starts_at") val startsAt: String,
    @SerialName("lesson_status") val lessonStatus: LessonStatus,
    @SerialName("attendance_status") val attendanceStatus: AttendanceStatus,
    @SerialName("recorded_at") val recordedAt: String? = null
)

data class AttendanceSummary(val present: Int, val absent: Int) {
    val total: Int get() = present + absent
    val percentage: Int? get() = if (total == 0) null else (100.0 * present / total).toInt()
    companion object {
        fun from(history: List<AttendanceHistory>): AttendanceSummary {
            val completed = history.filter { it.lessonStatus == LessonStatus.CLOSED }
            return AttendanceSummary(completed.count { it.attendanceStatus == AttendanceStatus.PRESENT }, completed.count { it.attendanceStatus == AttendanceStatus.ABSENT })
        }
    }
}

@Serializable
enum class InvitationStatus { PENDING, ACCEPTED, REFUSED, CANCELLED }

@Serializable
data class ClassroomInvitation(
    val id: String,
    @SerialName("classroom_id") val classroomId: String,
    @SerialName("classroom_name") val classroomName: String,
    @SerialName("teacher_name") val teacherName: String,
    val status: InvitationStatus,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class AppNotification(
    val id: String,
    @SerialName("user_id") val userId: String,
    val message: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("read_at") val readAt: String? = null
)

data class GeoPosition(val latitude: Double, val longitude: Double, val accuracyMeters: Double)

object AttendanceToken {
    fun isValid(value: String): Boolean = Regex("^presence:v1:[0-9a-f]{64}$").matches(value)
}
