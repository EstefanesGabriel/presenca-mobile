package com.example.presencamobile.data.repository

import com.example.presencamobile.data.remote.SupabaseProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthRepository {

    val sessionStatus
        get() = SupabaseProvider.client.auth.sessionStatus

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
}
