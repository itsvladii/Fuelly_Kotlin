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
import androidx.fragment.app.activityViewModels
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
    private val viewModel: DettagliViewModel by activityViewModels()

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

        observeViewModel(nomeBenzinaio, orarioApertura, orarioChiusura, bagnoPresente, barPresente, textDescrizione)

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

    private fun observeViewModel(
        nomeBenzinaio: TextView, ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
        viewModel.getGestoreBenzinaio { gestore ->
            activity?.runOnUiThread { nomeBenzinaio.text = gestore }
        }

        viewModel.infoBenzinaio.observe(viewLifecycleOwner) { info ->
            if (info != null) {
                infoId = info.id
                ap.setText(info.orarioApertura?.take(5) ?: "")
                ch.setText(info.orarioChiusura?.take(5) ?: "")
                bagno.isChecked = info.isBagno ?: false
                bar.isChecked = info.isBar ?: false
                desc.setText(info.descEstesa ?: "")
            }
        }
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

    //funzione per salvare le informazioni inserite dall'utente, con validazione e gestione degli errori.
    private fun salvaInformazioni(
        ap: TextInputEditText, ch: TextInputEditText,
        bagno: SwitchMaterial, bar: SwitchMaterial, desc: TextInputEditText
    ) {
        val user = SupabaseInstance.client.auth.currentUserOrNull()
        if (user == null) {
            Toast.makeText(context, getString(R.string.info_login_required), Toast.LENGTH_SHORT).show()
            return
        }

        val apStr = ap.text.toString().trim()
        val chStr = ch.text.toString().trim()
        val descStr = desc.text.toString().trim()

        if (apStr.isEmpty() || chStr.isEmpty()) {
            Toast.makeText(context, getString(R.string.info_hours_required), Toast.LENGTH_SHORT).show()
            return
        }

        val nuovaInfo = Info(
            id = infoId,
            idImpianto = idRicevuto,
            idUtente = user.id,
            orarioApertura = apStr,
            orarioChiusura = chStr,
            isBar = bar.isChecked,
            isBagno = bagno.isChecked,
            descEstesa = descStr
        )

        viewModel.salvaInfoBenzinaio(nuovaInfo)
        Toast.makeText(context, getString(R.string.info_save_success), Toast.LENGTH_SHORT).show()
        setFieldsEnabled(false, ap, ch, bagno, bar, desc)
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
}
