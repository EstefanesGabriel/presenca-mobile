package com.example.presencamobile.data.repository

import com.example.presencamobile.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    val sessionStatus
        get() = SupabaseProvider.client.auth.sessionStatus

    val currentUserId: String?
        get() = SupabaseProvider.client.auth.currentUserOrNull()?.id

    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        role: String
    ) {
        SupabaseProvider.client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password

            data = buildJsonObject {
                put("name", name.trim())
                put("role", role)
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        SupabaseProvider.client.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signOut() {
        SupabaseProvider.client.auth.signOut(SignOutScope.LOCAL)
    }

    suspend fun getCurrentUser(): UserInfo =
        SupabaseProvider.client.auth.retrieveUserForCurrentSession(updateSession = true)

    suspend fun updatePassword(password: String): UserInfo =
        SupabaseProvider.client.auth.updateUser { this.password = password }
}
