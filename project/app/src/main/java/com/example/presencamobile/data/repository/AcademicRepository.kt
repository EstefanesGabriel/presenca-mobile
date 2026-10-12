package com.example.presencamobile.data.repository

import com.example.presencamobile.data.model.*
import com.example.presencamobile.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AcademicRepository {
    private suspend fun rpc(name: String, params: JsonObject = buildJsonObject {}) =
        SupabaseProvider.client.postgrest.rpc(name, params)

    suspend fun classroom(id: String): Classroom? =
        SupabaseProvider.client.from("classrooms").select { filter { eq("id", id) } }.decodeList<Classroom>().singleOrNull()

    suspend fun updateClassroom(classroom: Classroom, password: String?): Classroom =
        rpc("update_classroom", buildJsonObject {
            put("p_classroom_id", classroom.id); put("p_name", classroom.name)
            put("p_subject", classroom.subject); put("p_semester", classroom.semester)
            if (password != null) put("p_password", password)
        }).decodeSingle<Classroom>()

    suspend fun closeClassroom(classroomId: String): Classroom =
        rpc("close_classroom", classParams(classroomId)).decodeSingle<Classroom>()
    suspend fun removeParticipant(classroomId: String, studentId: String) {
        rpc("remove_classroom_participant", buildJsonObject { put("p_classroom_id", classroomId); put("p_student_id", studentId) })
    }
    suspend fun lessons(classroomId: String): List<Lesson> = rpc("list_lessons", classParams(classroomId)).decodeList<Lesson>()
    suspend fun saveLesson(id: String, classroomId: String, title: String, startsAt: String): Lesson =
        rpc("save_lesson", buildJsonObject { put("p_id", id); put("p_classroom_id", classroomId); put("p_title", title); put("p_starts_at", startsAt) }).decodeSingle<Lesson>()
    suspend fun cancelLesson(lessonId: String) { rpc("cancel_lesson", lessonParams(lessonId)) }
    suspend fun generateQr(id: String, lessonId: String, position: GeoPosition, duration: Int, radius: Int): AttendanceQr =
        rpc("generate_attendance_qr", buildJsonObject {
            put("p_id", id); put("p_lesson_id", lessonId)
            putPosition(position); put("p_duration", duration); put("p_radius", radius)
        }).decodeSingle<AttendanceQr>()
    suspend fun invalidateQr(lessonId: String) { rpc("invalidate_attendance_qr", lessonParams(lessonId)) }
    suspend fun closeLesson(lessonId: String) { rpc("close_lesson", lessonParams(lessonId)) }
    suspend fun attendance(lessonId: String): List<AttendanceRecord> = rpc("list_lesson_attendance", lessonParams(lessonId)).decodeList<AttendanceRecord>()
    suspend fun recordAttendance(classroomId: String, token: String, position: GeoPosition): AttendanceReceipt =
        rpc("record_attendance", buildJsonObject { put("p_classroom_id", classroomId); put("p_token", token); putPosition(position) }).decodeSingle<AttendanceReceipt>()
    suspend fun history(classroomId: String): List<AttendanceHistory> = rpc("student_attendance_history", classParams(classroomId)).decodeList<AttendanceHistory>()
    suspend fun invite(classroomId: String, email: String) { rpc("invite_student", buildJsonObject { put("p_classroom_id", classroomId); put("p_email", email) }) }
    suspend fun invitations(): List<ClassroomInvitation> = rpc("list_my_invitations").decodeList<ClassroomInvitation>()
    suspend fun respondInvitation(id: String, accept: Boolean) { rpc("respond_invitation", buildJsonObject { put("p_invitation_id", id); put("p_accept", accept) }) }
    suspend fun notifications(): List<AppNotification> = rpc("list_my_notifications").decodeList<AppNotification>()
    suspend fun markRead(id: String) { rpc("mark_notification_read", buildJsonObject { put("p_id", id) }) }
    private fun classParams(id: String) = buildJsonObject { put("p_classroom_id", id) }
    private fun lessonParams(id: String) = buildJsonObject { put("p_lesson_id", id) }
    private fun kotlinx.serialization.json.JsonObjectBuilder.putPosition(position: GeoPosition) {
        put("p_latitude", position.latitude); put("p_longitude", position.longitude); put("p_accuracy", position.accuracyMeters)
    }
}
