package com.example.fuelly.supabase

import com.example.fuelly.BuildConfig
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth

object SupabaseInstance {
    //chiave API e URL per connettersi al database Supabase
    private const val URL = BuildConfig.SUPABASE_URL
    private const val KEY = BuildConfig.SUPABASE_KEY

    //creazione del client Supabase
    val client = createSupabaseClient(URL, KEY) {
        install(Postgrest)
        install(Auth)
    }

}
