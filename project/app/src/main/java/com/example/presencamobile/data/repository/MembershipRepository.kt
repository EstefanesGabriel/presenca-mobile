package com.example.presencamobile.data.repository

import com.example.presencamobile.data.model.Classroom
import com.example.presencamobile.data.model.ClassroomParticipant
import com.example.presencamobile.data.model.JoinClassroomResult
import com.example.presencamobile.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MembershipRepository {
    // A RLS limita o resultado aos vínculos do aluno autenticado.
    suspend fun listMyClassrooms(): List<Classroom> =
        SupabaseProvider.client.from("classrooms").select {
            order("created_at", Order.DESCENDING)
        }.decodeList<Classroom>()

    suspend fun joinClassroom(code: String, password: String): JoinClassroomResult =
        SupabaseProvider.client.postgrest.rpc("join_classroom", buildJsonObject {
            put("p_access_code", code)
            put("p_access_password", password)
        }).decodeSingle<JoinClassroomResult>()

    suspend fun listParticipants(classroomId: String): List<ClassroomParticipant> =
        SupabaseProvider.client.postgrest.rpc("list_classroom_participants", buildJsonObject {
            put("p_classroom_id", classroomId)
        }).decodeList<ClassroomParticipant>()
}
