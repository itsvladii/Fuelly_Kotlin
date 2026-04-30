package com.example.fuelly

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.Info
import com.example.fuelly.classes.Utente
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class InfoFragment : Fragment() {

    private var idRicevuto: Long = -1L



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_info, container, false)
        //Logica
        //3) leggo da database dalla tabella info e popolo i vari campi
        //4) bottone modifica per i vari campi da parte dell'utente

        // 1) PRENDO L'ID DEL BENZINAIO PASSATO DALL'ACTIVITY DETTAGLI TRAMITE INTENT
        idRicevuto = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L


        // 2) VARIABILI TEXT,SWITCH.....
        val nomeBenzinaio:TextView = view.findViewById(R.id.textNomeBenzinaio)
        val orarioApertura:TextView = view.findViewById(R.id.textOrarioApertura)
        val orarioChiusura:TextView = view.findViewById(R.id.textOrarioChiusura)
        val bagnoPresente: Switch = view.findViewById(R.id.switchBagno)
        val barPresente: Switch = view.findViewById(R.id.switchBar)
        val textDescrizione: TextInputEditText = view.findViewById(R.id.DescEstesa)
        var nomeEstratto:String


        // 3) LETTURA DAL DATABASE E POPOLAZIONE CAMPI
        lifecycleScope.launch {
            try {
                //LETTURA DALLA TABELLA info_benzinai IN BASE ALL'ID DEL BENZINAIO CHE PASSO DALLA DETTAGLI ACITIVITY
                val risposta = SupabaseInstance.client.from("info_benzinai").select {
                    filter { eq("idImpianto", idRicevuto) }
                }.decodeList<Info>()

                // SE LA RISPOSTA NON È VUOTA --> TROVO LA RIGA
                if (risposta.isNotEmpty()) {
                    val infoBez = risposta[0]

                    var nomeImpianto = "Caricamento..."

                    //Lettura del nome del gestore dalla tabella benzinai
                    try {
                        val datiBenzinaio = SupabaseInstance.client.from("benzinai")
                            .select(columns = Columns.list("Gestore")) {
                                filter { eq("idImpianto", idRicevuto) }
                            }.decodeSingle<Map<String, String>>()

                        nomeImpianto = datiBenzinaio["Gestore"] ?: "Sconosciuto"

                    } catch (e: Exception)
                    {
                        Log.e("Errore Supabase", "Errore recupero gestore: ${e.message}")

                        nomeImpianto = "Gestore non trovato"
                    }

                    //VISUALIZZO NELL'INTERFACCIA
                    nomeBenzinaio.text = nomeImpianto
                    orarioApertura.text = "Orario Apertura | " + infoBez.orarioApertura.toString()
                    orarioChiusura.text = "Orario Chiusura | " + infoBez.orarioChiusura.toString()
                    bagnoPresente.isChecked = infoBez.isBagno
                    barPresente.isChecked = infoBez.isBar
                    textDescrizione.setText(infoBez.descEstesa ?: "")

                    //DISABILITO ALCUNI ELEMENTI
                    textDescrizione.apply {
                        isFocusable = false
                        isFocusableInTouchMode = false
                        isCursorVisible = false
                    }
                    bagnoPresente.isClickable = false
                    barPresente.isClickable = false
                    orarioApertura.isClickable = false
                    orarioChiusura.isClickable = false

                } else {
                    Log.d("Errore", "Nessuna info trovata per idImpianto: $idRicevuto")
                    Toast.makeText(context, "Dati impianto non disponibili", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("Errore Supabase", "Errore generale: ${e.message}", e)
            }
        }

        return view




    }




}