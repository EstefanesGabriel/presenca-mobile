package com.example.presencamobile.data.repository

import com.example.presencamobile.data.model.UserProfile
import com.example.presencamobile.data.remote.SupabaseProvider
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns

class ProfileRepository {
    suspend fun getProfile(userId: String): UserProfile? =
        SupabaseProvider.client.from("profiles")
            .select(columns = Columns.list("id", "name", "role")) {
                filter { eq("id", userId) }
            }.decodeList<UserProfile>().singleOrNull()
}
