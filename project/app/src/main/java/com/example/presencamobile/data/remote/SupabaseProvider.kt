package com.example.presencamobile.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseProvider {

    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = "https://verkrjzynojplaxgviro.supabase.co",
            supabaseKey = "sb_publishable_KupKTkZ336OYsTCdCmRgSQ_4QPuQIXP"
        ) {
            install(Auth)
            install(Postgrest)
        }
    }
}