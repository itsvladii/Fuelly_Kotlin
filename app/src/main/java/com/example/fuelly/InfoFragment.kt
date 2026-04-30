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

                //LEGGO DALLA TABELLA info_benzinai TRAMITE L'ID CHE OTTENGO DALLA DETTAGLIACTIVITY
                val risposta =  SupabaseInstance.client.from("info_benzinai").select{
                    filter { eq("idImpianto", idRicevuto) }
                }.decodeList<Info>()

                //TROVO UNA RIGA EFFETTIVA...
                if (risposta.isNotEmpty())
                {
                    try {

                        val datiBenzinaio = SupabaseInstance.client.from("benzinai")
                            .select(columns = Columns.list("gestore")) {
                                filter { eq("idImpianto", idRicevuto) }
                            }.decodeSingle<Map<String, String>>() // Decodifichiamo come mappa chiave-valore

                        nomeEstratto = datiBenzinaio["gestore"] ?: "Sconosciuto"

                    } catch (e: Exception) {
                        Log.e("Errore Supabase", "Dettaglio: ${e.message}", e)
                    }


                    //METTO NELL'OGGETTO InfoBez CIO CHE LEGGO DAL DATABASE
                    val InfoBez = Info(

                        id= risposta[0].id,
                        idImpianto= idRicevuto,
                        idUtente= risposta[0].idUtente,
                        orarioApertura= risposta[0].orarioApertura,
                        orarioChiusura= risposta[0].orarioChiusura,
                        isBar= risposta[0].isBar,
                        isBagno= risposta[0].isBagno,
                        descEstesa= risposta[0].descEstesa
                    )

                    //VISUALIZZO SULLE VARIE TEXT,SWITCH ....
                    nomeBenzinaio.text = InfoBez.idImpianto.toString()
                    orarioApertura.text = "Orario Apertura: " + InfoBez.orarioApertura.toString()
                    orarioChiusura.text = "Orario Chisura: " + InfoBez.orarioChiusura.toString()
                    bagnoPresente.isChecked = InfoBez.isBagno
                    barPresente.isChecked = InfoBez.isBar
                    textDescrizione.setText(InfoBez.descEstesa)
                }
                else
                {
                    //ERRORE: NESSUNA RIGA LETTA DALLA TABELLA info_benzinai
                    Log.d("Errore", "benzinaio senza nessun valore")

                    Toast.makeText(context, "Nessun dato disponibile", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception)
            {
                Log.e("Errore Supabase", "Dettaglio: ${e.message}", e)
            }
        }

        return view




    }




}