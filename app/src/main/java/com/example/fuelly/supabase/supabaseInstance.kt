package com.example.fuelly.supabase

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseInstance {
    // Per ora mettiamoli qui per testare, poi li sposteremo in BuildConfig
    private const val URL ="https://ivimwqhiviuawwbctejd.supabase.co"
    private const val KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Iml2aW13cWhpdml1YXd3YmN0ZWpkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQzNDUzNTUsImV4cCI6MjA4OTkyMTM1NX0.9gI0sZB0qIG6bLywe7bTwci4KUKZ_z7CFcE7BQCKhk0"

    val client = createSupabaseClient(URL, KEY) {
        install(Postgrest)
    }
}