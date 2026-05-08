package com.example.fuelly

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.example.fuelly.databinding.ActivityMainBinding
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class MainActivity : AppCompatActivity() {

    // Aggiunto binding per il layout
    private lateinit var binding: ActivityMainBinding

    // Aggiunto inizializzazione del binding e settaggio del layout alla creazione dell'activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inizializza il binding con il layout dell'activity
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false  // icone status bar bianche

        // Imposta il layout binding comeContentView
        setContentView(binding.root)

        // Carica il fragment iniziale (Mappa)
        if (savedInstanceState == null) {
            replaceFragment(MapsFragment(), "MAPPA")
        }

        setupNavigation()
    }

    // Imposta il listener per i bottoni di navigazione e aggiorna l'UI
    private fun setupNavigation() {
        // Aggiunto listener per i bottoni di navigazione
        binding.btnNavMappa.setOnClickListener {
            replaceFragment(MapsFragment(), "MAPPA")
            updateNavbarUI("MAPPA")
        }

        // Aggiunto listener per il pulsante di navigazione "Salvati"
        binding.btnNavSalvati.setOnClickListener {
            replaceFragment(SalvatiFragment(), "SALVATI")
            updateNavbarUI("SALVATI")
        }

        // Aggiunto listener per il pulsante di navigazione "Cerca"
        binding.btnNavCerca.setOnClickListener {
            replaceFragment(CercaFragment(), "CERCA")
            updateNavbarUI("CERCA")
        }

        // Aggiunto listener per il pulsante di navigazione "Profilo"
        binding.btnNavProfilo.setOnClickListener {
            val user = SupabaseInstance.client.auth.currentUserOrNull()
            var nomeCompleto = user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull ?: "Utente Anonimo"
            var email = user?.email ?: "Non disponibile"

            val fragment = ProfiloFragment().apply {
                arguments = Bundle().apply {
                    putString("USER_ID", user?.id)
                    putString("NomeUtente",user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull)
                    putString("EmailUtente",user?.email)
                }
            }
            replaceFragment(fragment, "PROFILO")
            updateNavbarUI("PROFILO")
        }
    }

    // Sostituisce il fragment corrente con il nuovo fragment
    private fun replaceFragment(fragment: Fragment, tag: String) {
        // Ottieni il fragment corrente
        val currentFragment = supportFragmentManager.findFragmentByTag(tag)
        // Se il fragment corrente esiste ed è visibile, non sostituisci nulla
        if (currentFragment != null && currentFragment.isVisible) return

        // Rimuovi tutti i fragment tranne quello corrente
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    // Aggiorna l'UI della navbar in base al fragment selezionato
    private fun updateNavbarUI(selectedTag: String) {
        val yellow = ContextCompat.getColor(this, R.color.fuelly_yellow_fluo)
        val grey = ContextCompat.getColor(this, R.color.grey_inactive)

        // Reset tutti a grigio
        resetItem(binding.imgNavMappa, binding.txtNavMappa, grey)
        resetItem(binding.imgNavSalvati, binding.txtNavSalvati, grey)
        resetItem(binding.imgNavCerca, binding.txtNavCerca, grey)
        resetItem(binding.imgNavProfilo, binding.txtNavProfilo, grey)

        // Evidenzia il selezionato
        when (selectedTag) {
            "MAPPA" -> highlightItem(binding.imgNavMappa, binding.txtNavMappa, yellow)
            "SALVATI" -> highlightItem(binding.imgNavSalvati, binding.txtNavSalvati, yellow)
            "CERCA" -> highlightItem(binding.imgNavCerca, binding.txtNavCerca, yellow)
            "PROFILO" -> highlightItem(binding.imgNavProfilo, binding.txtNavProfilo, yellow)
        }
    }

    // Aggiunto metodo per resettare l'item della navbar con colori iniziali
    private fun resetItem(img: ImageView, txt: TextView, color: Int) {
        img.setColorFilter(color)
        txt.setTextColor(color)
    }

    // Aggiunto metodo per evidenziare l'item della navbar con colori diversi
    private fun highlightItem(img: ImageView, txt: TextView, color: Int) {
        img.setColorFilter(color)
        txt.setTextColor(color)
    }
}
