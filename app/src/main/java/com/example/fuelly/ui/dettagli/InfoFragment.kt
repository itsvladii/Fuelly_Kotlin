package com.example.fuelly.ui.dettagli

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.fuelly.R
import com.example.fuelly.repository.model.Info
import com.example.fuelly.repository.supabase.SupabaseInstance
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class InfoFragment : Fragment() {

    //variabili per memorizzare i dati ricevuti dall'Intent precedente
    private var idRicevuto: Long = -1L
    private var nomeBenzinaioRicevuto: String = ""
    private var infoId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_info, container, false)

        //recupera i dati dall'Intent
        idRicevuto = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L
        nomeBenzinaioRicevuto = activity?.intent?.getStringExtra("NOME_BENZINAIO") ?: ""

        //elementi UI dell'activity
        val nomeBenzinaio = view.findViewById<TextView>(R.id.textNomeBenzinaio)
        val orarioApertura = view.findViewById<TextInputEditText>(R.id.textOrarioApertura)
        val orarioChiusura = view.findViewById<TextInputEditText>(R.id.textOrarioChiusura)
        val bagnoPresente = view.findViewById<SwitchMaterial>(R.id.switchBagno)
        val barPresente = view.findViewById<SwitchMaterial>(R.id.switchBar)
        val textDescrizione = view.findViewById<TextInputEditText>(R.id.DescEstesa)
        val btnModifica = view.findViewById<MaterialButton>(R.id.editButton)
        val btnSalva = view.findViewById<MaterialButton>(R.id.saveButton)
        val btnSegnala = view.findViewById<MaterialButton>(R.id.segnalaButton)

        //di default i campi sono disabilitati, si abilitano solo quando l'utente clicca su "Modifica"
        setFieldsEnabled(false, orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)

        //carichiamo il nome del benzinaio e le info esistenti (se presenti)
        lifecycleScope.launch {
            try {
                val datiBenzinaio = SupabaseInstance.client.from("benzinai")
                    .select(columns = Columns.Companion.list("Gestore")) {
                        filter { eq("idImpianto", idRicevuto) }
                    }.decodeSingle<Map<String, String>>()
                nomeBenzinaio.text = datiBenzinaio["Gestore"] ?: "Sconosciuto"
            } catch (e: Exception) {
                nomeBenzinaio.text = "Gestore non trovato"
            }

            caricaInfoEsistenti(orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
        }

        //gestione click sui pulsanti
        btnModifica.setOnClickListener {
            setFieldsEnabled(true, orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
            Toast.makeText(context, getString(R.string.info_edit_mode), Toast.LENGTH_SHORT).show()
        }

        btnSalva.setOnClickListener {
            salvaInformazioni(orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
        }

        btnSegnala.setOnClickListener {
            inviaSegnalazione()
        }

        return view
    }

    //funzione di gestione della segnalazione, con intent per inviare una email precompilata all'indirizzo di supporto
    fun inviaSegnalazione() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("info@fuelly.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Segnalazione Benzinaio: $nomeBenzinaioRicevuto")
            putExtra(Intent.EXTRA_TEXT, "Problemi riscontrati: ")
        }
        startActivity(Intent.createChooser(emailIntent, "Invia email con..."))
    }

    //funzione di utilità per abilitare/disabilitare i campi di input in modo centralizzato
    private fun setFieldsEnabled(enabled: Boolean, vararg views: View) {
        views.forEach { v ->
            v.isEnabled = enabled
            if (v is TextInputEditText) {
                v.isFocusable = enabled
                v.isFocusableInTouchMode = enabled
            }
        }
    }

    //funzione per caricare le informazioni esistenti dal database e popolare i campi, se presenti.
    private suspend fun caricaInfoEsistenti(
        ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
        try {
            //recuperiamo tutte le info per questo impianto
            val risposta = SupabaseInstance.client.from("info_benzinai").select {
                filter { eq("idImpianto", idRicevuto) }
            }.decodeList<Info>()

            if (risposta.isNotEmpty()) {
                //prendiamo l'ultima versione disponibile se ci sono più righe
                val info = risposta.last()
                infoId = info.id //salviamo l'ID per i futuri salvataggi

                //popoliamo i campi con i dati recuperati
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

    //funzione per salvare le informazioni inserite dall'utente, con validazione e gestione degli errori.
    private fun salvaInformazioni(
        ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
        //verifichiamo che l'utente sia loggato prima di permettere il salvataggio
        val session = SupabaseInstance.client.auth.currentSessionOrNull()
        if (session == null) {
            Toast.makeText(context, getString(R.string.info_login_required), Toast.LENGTH_SHORT).show()
            return
        }

        //validazione dei campi obbligatori
        val apStr = ap.text.toString().trim()
        val chStr = ch.text.toString().trim()
        val descStr = desc.text.toString().trim()

        if (apStr.isEmpty() || chStr.isEmpty()) {
            Toast.makeText(context, getString(R.string.info_hours_required), Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                //creiamo un oggetto Info con i dati inseriti
                val nuovaInfo = Info(
                    id = infoId,
                    idImpianto = idRicevuto,
                    idUtente = session.user?.id.toString(),
                    orarioApertura = apStr,
                    orarioChiusura = chStr,
                    isBar = bar.isChecked,
                    isBagno = bagno.isChecked,
                    descEstesa = descStr
                )

                //usiamo upsert e recuperiamo il risultato per aggiornare l'infoId locale
                val result = SupabaseInstance.client.from("info_benzinai").upsert(nuovaInfo) {
                    select()
                }.decodeSingle<Info>()

                infoId = result.id

                Toast.makeText(context, getString(R.string.info_save_success), Toast.LENGTH_SHORT).show()
                setFieldsEnabled(false, ap, ch, bagno, bar, desc)
            } catch (e: Exception) {
                Log.e("InfoFragment", "Errore salvataggio: ${e.message}")
                Toast.makeText(context, getString(R.string.info_save_error), Toast.LENGTH_SHORT).show()
            }
        }
    }
}