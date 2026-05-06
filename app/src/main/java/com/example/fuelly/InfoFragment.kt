package com.example.fuelly

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.classes.Info
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
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

        //recupero ID dell'elemento
        idRicevuto = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L

        //componenti del fragment
        val nomeBenzinaio = view.findViewById<TextView>(R.id.textNomeBenzinaio)
        val orarioApertura = view.findViewById<TextInputEditText>(R.id.textOrarioApertura)
        val orarioChiusura = view.findViewById<TextInputEditText>(R.id.textOrarioChiusura)
        val bagnoPresente = view.findViewById<SwitchMaterial>(R.id.switchBagno)
        val barPresente = view.findViewById<SwitchMaterial>(R.id.switchBar)
        val textDescrizione = view.findViewById<TextInputEditText>(R.id.DescEstesa)
        val btnModifica = view.findViewById<MaterialButton>(R.id.editButton)
        val btnSalva = view.findViewById<MaterialButton>(R.id.saveButton)

        //all'inizio tutti i componenti sono disabilitati
        setFieldsEnabled(false, orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)

        lifecycleScope.launch {
            try {
                // Recupero nome gestore dalla tabella benzinai
                val datiBenzinaio = SupabaseInstance.client.from("benzinai")
                    .select(columns = Columns.list("Gestore")) {
                        filter { eq("idImpianto", idRicevuto) }
                    }.decodeSingle<Map<String, String>>()
                nomeBenzinaio.text = datiBenzinaio["Gestore"] ?: "Sconosciuto"
            } catch (e: Exception) {
                nomeBenzinaio.text = "Gestore non trovato"
            }

            caricaInfoEsistenti(orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
        }

        //attivo la modifica se clicco il pulsante modifica
        btnModifica.setOnClickListener {
            setFieldsEnabled(true, orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
            Toast.makeText(context, "Modalità modifica attivata", Toast.LENGTH_SHORT).show()
        }

        //salvo le modifiche se clicco il pulsante salva
        btnSalva.setOnClickListener {
            salvaInformazioni(orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
        }

        return view
    }

    //funzione di attivazione/disattivazione degli elementi del fragment
    private fun setFieldsEnabled(enabled: Boolean, vararg views: View) {
        views.forEach { v ->
            v.isEnabled = enabled
            if (v is TextInputEditText) {
                v.isFocusable = enabled
                v.isFocusableInTouchMode = enabled
            }
        }
    }

    //funzione di caricamento le info del benzinaio
    private suspend fun caricaInfoEsistenti(
        ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
        try {
            //recupero informazioni esistenti dalla tabella info_benzinai
            val risposta = SupabaseInstance.client.from("info_benzinai").select {
                filter { eq("idImpianto", idRicevuto) }
            }.decodeList<Info>()

            //se esistono informazioni, le mostro nei campi
            if (risposta.isNotEmpty()) {
                val info = risposta[0]
                ap.setText(info.orarioApertura?.take(5) ?: "")
                ch.setText(info.orarioChiusura?.take(5) ?: "")
                bagno.isChecked = info.isBagno ?: false
                bar.isChecked = info.isBar ?: false
                desc.setText(info.descEstesa ?: "")
            }
        } catch (e: Exception) {
            Log.e("InfoFragment", "Errore caricamento: ${e.message}")
        }
    }

    //funzione di salvataggio delle info sul DB
    private fun salvaInformazioni(
        ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
        //verifico che l'utente sia loggato
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session == null) {
            Toast.makeText(context, "Effettua il login per salvare", Toast.LENGTH_SHORT).show()
            return
        }

        val apStr = ap.text.toString().trim()
        val chStr = ch.text.toString().trim()
        val descStr = desc.text.toString().trim()

        if (apStr.isEmpty() || chStr.isEmpty()) {
            Toast.makeText(context, "Orari obbligatori", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val nuovaInfo = Info(
                    id = null,
                    idImpianto = idRicevuto,
                    idUtente = session.user?.id.toString(),
                    orarioApertura = apStr,
                    orarioChiusura = chStr,
                    isBar = bar.isChecked,
                    isBagno = bagno.isChecked,
                    descEstesa = descStr
                )

                //inserisco se nuovo, aggiorno se esiste
                SupabaseInstance.client.from("info_benzinai").upsert(nuovaInfo)

                Toast.makeText(context, "Dati salvati con successo", Toast.LENGTH_SHORT).show()
                //dopo il salvataggio disabilito i campi
                setFieldsEnabled(false, ap, ch, bagno, bar, desc)
            } catch (e: Exception) {
                Toast.makeText(context, "Errore durante il salvataggio", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
