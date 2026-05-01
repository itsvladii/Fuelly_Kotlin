package com.example.fuelly

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.Info
import com.example.fuelly.classes.Salvato
import com.example.fuelly.classes.Utente
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalTime

class InfoFragment : Fragment() {

    private var idRicevuto: Long = -1L



    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.fragment_info, container, false)

        // 1) PRENDO L'ID DEL BENZINAIO PASSATO DALL'ACTIVITY DETTAGLI TRAMITE INTENT
        idRicevuto = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L


        // 2) VARIABILI TEXT,SWITCH.....
        val nomeBenzinaio:TextView = view.findViewById(R.id.textNomeBenzinaio)
        val orarioApertura: TextInputEditText = view.findViewById(R.id.textOrarioApertura)
        val orarioChiusura: TextInputEditText = view.findViewById(R.id.textOrarioChiusura)
        val bagnoPresente: Switch = view.findViewById(R.id.switchBagno)
        val barPresente: Switch = view.findViewById(R.id.switchBar)
        val textDescrizione: TextInputEditText = view.findViewById(R.id.DescEstesa)
        var nomeEstratto:String
        val bottoneModificaInfo: ImageButton = view.findViewById(R.id.editButton)
        val bottoneSalvaInfo: ImageButton = view.findViewById(R.id.saveButton)
        val bottoneInserisciInfo: ImageButton = view.findViewById(R.id.insertButton)


        // 3) LETTURA DAL DATABASE E VISUALIZZO NEi CAMPI
        lifecycleScope.launch {

            try {

                //RICAVO IL GESTORE DALLA TABELLA BENZINAI
                try {
                    val datiBenzinaio = SupabaseInstance.client.from("benzinai")
                        .select(columns = Columns.list("Gestore")) {
                            filter { eq("idImpianto", idRicevuto) }
                        }.decodeSingle<Map<String, String>>()

                    //Eventualmente lo setto come "Sconosciuto"
                    nomeBenzinaio.text = datiBenzinaio["Gestore"] ?: "Sconosciuto"

                } catch (e: Exception)
                {
                    nomeBenzinaio.text = "Gestore non trovato"
                }

                //LETTURA DALLA TABELLA info_benzinai IN BASE ALL'ID DEL BENZINAIO CHE PASSO DALLA DETTAGLI ACITIVITY
                val risposta = SupabaseInstance.client.from("info_benzinai").select {
                    filter { eq("idImpianto", idRicevuto) }
                }.decodeList<Info>()

                // SE LA RISPOSTA NON È VUOTA --> TROVO LA RIGA
                if (risposta.isNotEmpty()) {

                    val infoBez = risposta[0]

                    // Prendi solo i primi 5 caratteri (HH:mm) se la stringa non è nulla
                    val apPulito = infoBez.orarioApertura?.take(5) ?: "--:--"
                    val chPulito = infoBez.orarioChiusura?.take(5) ?: "--:--"

                    // Imposta SOLO l'orario pulito nel campo di testo
                    //uso il setText come metodo perchè lavoro con una tipeInput Edit Text
                    orarioApertura.setText(apPulito)
                    orarioChiusura.setText(chPulito)

                    // Se vuoi mostrare un'etichetta, usa l'HINT del layout o una TextView separata
                    bagnoPresente.isChecked = infoBez.isBagno ?: false
                    barPresente.isChecked = infoBez.isBar ?: false
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

                }
                else
                {
                    Log.d("Errore", "Nessuna info trovata per idImpianto: $idRicevuto")

                    Toast.makeText(context, "Dati impianto non disponibili", Toast.LENGTH_SHORT).show()

                    //VADO A FARE L'INSERIMENTO DELLA INFO NELLA TABELLA info_benzinai
                    //ABILITO IL BOTTONE DI INSERIMENTO
                    bottoneInserisciInfo.isEnabled = true
                    bottoneInserisciInfo.isClickable = true
                    bottoneSalvaInfo.isEnabled = false
                    bottoneSalvaInfo.isClickable = false

                    //AL CLICK DEL BOTTONE INSERIMENTO
                    bottoneInserisciInfo.setOnClickListener {

                        //controllo l'utente in sessione
                        val session = SupabaseInstance.client.auth.currentSessionOrNull()

                        if (session == null) {
                            Toast.makeText(context, "Effettuare il login", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener // Esci se non c'è sessione
                        }

                        //Recupero testi dei vari campi puliti e aggiornati
                        val desc = textDescrizione.text.toString().trim()

                        // prendo l'orario così come scritto (es. "08:00")
                        val orarioApStr = orarioApertura.text.toString().trim()
                        val orarioChStr = orarioChiusura.text.toString().trim()

                        //Controllo campi vuoti
                        if (desc.isEmpty() || orarioApStr.isEmpty() || orarioChStr.isEmpty()) {
                            Toast.makeText(context, "Obbligatorio inserire tutti i campi", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener // Esci se mancano dati
                        }

                        lifecycleScope.launch {
                            try {
                                //oggetto Info con quello che inserisco nei campi dell'interfaccia
                                val nuovaInfo = Info(
                                    id = null, //è autoincrement nella tabella lo genero automaticamente
                                    idImpianto = idRicevuto,
                                    idUtente = session.user?.id.toString(),
                                    orarioApertura = orarioApStr,
                                    orarioChiusura = orarioChStr,
                                    isBar = barPresente.isChecked,
                                    isBagno = bagnoPresente.isChecked,
                                    descEstesa = desc
                                )

                                //QUERY DI INSERIMENTO
                                SupabaseInstance.client.from("info_benzinai").insert(nuovaInfo)

                                Toast.makeText(context, "Info inserite con successo", Toast.LENGTH_SHORT).show()

                                // Feedback visivo: disabilita e rendi semi-trasparente
                                bottoneInserisciInfo.isEnabled = false
                                bottoneInserisciInfo.alpha = 0.5f

                            } catch (e: Exception)
                            {
                                Log.e("Errore Supabase", "Errore inserimento info: ${e.message}")
                                Toast.makeText(context, "Errore: controlla formato HH:mm", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("Errore Supabase", "Errore generale: ${e.message}", e)
            }
        }

        //AL CLICK DEL BOTTONE MODIFICA
        bottoneModificaInfo.setOnClickListener {
            // Abilita gli EditText
            textDescrizione.isEnabled = true
            orarioApertura.isEnabled = true
            orarioChiusura.isEnabled = true

            // Abilita gli Switch
            bagnoPresente.isEnabled = true
            barPresente.isEnabled = true

            // Gestione Bottoni
            bottoneSalvaInfo.isEnabled = true
            bottoneSalvaInfo.alpha = 1.0f
            bottoneInserisciInfo.isEnabled = false
        }

        return view




    }




}