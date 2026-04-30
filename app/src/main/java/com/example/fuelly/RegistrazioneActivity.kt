package com.example.fuelly

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.Utente
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class RegistrazioneActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_registrazione)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets

        }

        //Variabili
        val email = findViewById<TextView>(R.id.textEmail)
        val password = findViewById<TextView>(R.id.textPassword)
        val nome = findViewById<TextView>(R.id.textNome)
        val cognome = findViewById<TextView>(R.id.textCognome)

        //bottone
        val btnRegistrati = findViewById<Button>(R.id.btnRegistrazione)

        btnRegistrati.setOnClickListener {
            val emailText = email.text.toString()
            val passwordText = password.text.toString()
            val nomeText = nome.text.toString()
            val cognomeText = cognome.text.toString()

            lifecycleScope.launch {
                try {
                    //CONTROLLO SE L'UTENTE E' GIA' REGISTRATO E DECODIFICO IL RISULTATO DELLA QUERY IN UNA LISTA
                    val risposta = SupabaseInstance.client.from("utenti").select {
                        filter { eq("email", emailText) }
                    }.decodeList<Utente>()

                    //SE NON ESISTE ALCUN UTENTE CON QUELL'EMAIL CHE VOGLIO INSERIRE ALLORA CREO L'UTENTE E LO INSERISCO NELLA TABELLA
                    if (risposta.isEmpty()) {
                        // 2. Creiamo l'oggetto (usa la data class con il serializzatore)
                        val nuovoUtente = Utente(
                            id = java.util.UUID.randomUUID().toString(), // Genera un ID univoco
                            email = emailText,
                            password = passwordText,
                            nome = nomeText,
                            cognome = cognomeText
                        )

                        //QUERY DI INSERIMENTO
                        SupabaseInstance.client.from("utenti").insert(nuovoUtente)

                        //MESSAGGI DI CONFERMA ED ERRORI VARI
                        runOnUiThread {
                            Toast.makeText(this@RegistrazioneActivity, "Registrazione completata!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Log.d("Errore", "L'utente con questa email esiste già")
                    }
                } catch (e: Exception) {
                    Log.e("Errore Supabase", "Dettaglio: ${e.message}", e)
                }
            }
        }

    }
}