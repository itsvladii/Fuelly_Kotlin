package com.example.fuelly

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import android.os.Handler
import android.os.Looper

class Splash : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        //nascondi la barra di navigazione
        supportActionBar?.hide()

        //aspetta 2 secondi e poi apre l'activity principale
        Handler(Looper.getMainLooper()).postDelayed({
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            //finish() // Chiude lo splash così non torna indietro premendo il tasto back
        }, 2000)
    }
}