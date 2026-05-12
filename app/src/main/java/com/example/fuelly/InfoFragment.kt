package com.example.fuelly

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
    private var nomeBenzinaioRicevuto: String =""
    private var infoId: String? = null // Variabile per memorizzare l'ID del record nel DB

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_info, container, false)

        idRicevuto = activity?.intent?.getLongExtra("ID_ELEMENTO", -1L) ?: -1L
        nomeBenzinaioRicevuto = activity?.intent?.getStringExtra("NOME_BENZINAIO") ?: ""

        val nomeBenzinaio = view.findViewById<TextView>(R.id.textNomeBenzinaio)
        val orarioApertura = view.findViewById<TextInputEditText>(R.id.textOrarioApertura)
        val orarioChiusura = view.findViewById<TextInputEditText>(R.id.textOrarioChiusura)
        val bagnoPresente = view.findViewById<SwitchMaterial>(R.id.switchBagno)
        val barPresente = view.findViewById<SwitchMaterial>(R.id.switchBar)
        val textDescrizione = view.findViewById<TextInputEditText>(R.id.DescEstesa)
        val btnModifica = view.findViewById<MaterialButton>(R.id.editButton)
        val btnSalva = view.findViewById<MaterialButton>(R.id.saveButton)
        val btnSegnala = view.findViewById<MaterialButton>(R.id.segnalaButton)

        setFieldsEnabled(false, orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)

        lifecycleScope.launch {
            try {
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

        btnModifica.setOnClickListener {
            setFieldsEnabled(true, orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
            Toast.makeText(context, "Modalità modifica attivata", Toast.LENGTH_SHORT).show()
        }

        btnSalva.setOnClickListener {
            salvaInformazioni(orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)
        }

        btnSegnala.setOnClickListener {
            inviaSegnalazione()
        }

        return view
    }

    fun inviaSegnalazione() {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("admin@gmail.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Segnalazione Benzinaio: $nomeBenzinaioRicevuto")
            putExtra(Intent.EXTRA_TEXT, "Problemi riscontrati: ")
        }
        startActivity(Intent.createChooser(emailIntent, "Invia email con..."))
    }

    private fun setFieldsEnabled(enabled: Boolean, vararg views: View) {
        views.forEach { v ->
            v.isEnabled = enabled
            if (v is TextInputEditText) {
                v.isFocusable = enabled
                v.isFocusableInTouchMode = enabled
            }
        }
    }

    private suspend fun caricaInfoEsistenti(
        ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
        try {
            val risposta = SupabaseInstance.client.from("info_benzinai").select {
                filter { eq("idImpianto", idRicevuto) }
            }.decodeList<Info>()

            if (risposta.isNotEmpty()) {
                // Prendiamo l'ultima versione disponibile se ci sono più righe (ordinamento implicito)
                val info = risposta.last() 
                infoId = info.id // Salviamo l'ID per i futuri salvataggi
                
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

    private fun salvaInformazioni(
        ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
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
                    id = infoId, // Fondamentale per aggiornare la stessa riga
                    idImpianto = idRicevuto,
                    idUtente = session.user?.id.toString(),
                    orarioApertura = apStr,
                    orarioChiusura = chStr,
                    isBar = bar.isChecked,
                    isBagno = bagno.isChecked,
                    descEstesa = descStr
                )

                // Usiamo upsert e recuperiamo il risultato per aggiornare l'infoId locale
                val result = SupabaseInstance.client.from("info_benzinai").upsert(nuovaInfo) {
                    select()
                }.decodeSingle<Info>()

                infoId = result.id
                
                Toast.makeText(context, "Dati salvati con successo", Toast.LENGTH_SHORT).show()
                setFieldsEnabled(false, ap, ch, bagno, bar, desc)
            } catch (e: Exception) {
                Log.e("InfoFragment", "Errore salvataggio: ${e.message}")
                Toast.makeText(context, "Errore durante il salvataggio", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
