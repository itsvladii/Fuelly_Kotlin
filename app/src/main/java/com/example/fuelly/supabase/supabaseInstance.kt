package com.example.fuelly.supabase

import com.example.fuelly.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth

object SupabaseInstance {
    // Per ora mettiamoli qui per testare, poi li sposteremo in BuildConfig
    private const val URL = BuildConfig.SUPABASE_URL
    private const val KEY = BuildConfig.SUPABASE_KEY

    val client = createSupabaseClient(URL, KEY) {
        install(Postgrest)
        install(Auth)
    }

}