package com.example.presencamobile.data.repository

import com.example.presencamobile.data.model.Classroom
import com.example.presencamobile.data.model.NewClassroom
import com.example.presencamobile.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ClassroomRepository {
    suspend fun listClassrooms(teacherId: String): List<Classroom> =
        SupabaseProvider.client.from("classrooms").select {
            filter { eq("teacher_id", teacherId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<Classroom>()

    suspend fun createClassroom(classroom: NewClassroom): Classroom =
        SupabaseProvider.client.postgrest.rpc("create_classroom", buildJsonObject {
            put("p_id", classroom.id)
            put("p_name", classroom.name)
            put("p_subject", classroom.subject)
            put("p_semester", classroom.semester)
            put("p_access_password", classroom.accessPassword)
        }).decodeSingle<Classroom>()
}
