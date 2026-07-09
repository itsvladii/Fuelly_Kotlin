package com.example.fuelly

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import com.example.fuelly.databinding.ActivityMainBinding
import com.example.fuelly.repository.supabase.SupabaseInstance
import com.example.fuelly.ui.cerca.CercaFragment
import com.example.fuelly.ui.maps.MapsFragment
import com.example.fuelly.ui.profilo.ProfiloFragment
import com.example.fuelly.ui.salvati.SalvatiFragment
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //effettua l'inflate
        binding = ActivityMainBinding.inflate(layoutInflater)

        // 3. Abilita la modalità "Edge-to-Edge", facendo sì che il layout dell'app si estenda
        enableEdgeToEdge()

        // 4. Ottiene il controller per gestire l'aspetto e il comportamento delle barre di sistema (finestra dei controlli)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

        // 5. Imposta le icone della barra di stato in modalità scura (false = icone bianche/chiare).
        windowInsetsController.isAppearanceLightStatusBars = false  // icone status bar bianche

        // 6. Mostra effettivamente a schermo la vista principale dell'Activity, recuperando la radice (root) del layout xml
        setContentView(binding.root)

        //carica il fragment iniziale (Mappa)
        if (savedInstanceState == null) {
            replaceFragment(MapsFragment(), "MAPPA")
            updateNavbarUI("MAPPA")
        }

        setupNavigation()
    }

    //funzione che imposta i listener per i bottoni di navigazione e aggiorna l'UI
    private fun setupNavigation() {
        //listener per i bottoni di navigazione
        binding.btnNavMappa.setOnClickListener {
            replaceFragment(MapsFragment(), "MAPPA")
            updateNavbarUI("MAPPA")
        }

        //listener per il pulsante di navigazione "Salvati"
        binding.btnNavSalvati.setOnClickListener {
            replaceFragment(SalvatiFragment(), "SALVATI")
            updateNavbarUI("SALVATI")
        }

        //listener per il pulsante di navigazione "Cerca"
        binding.btnNavCerca.setOnClickListener {
            replaceFragment(CercaFragment(), "CERCA")
            updateNavbarUI("CERCA")
        }

        //listener per il pulsante di navigazione "Profilo"
        binding.btnNavProfilo.setOnClickListener {
            // Recupera i dati dell'utente attualmente autenticato
            // (con fallback in caso in cui l'utente non sia autenticato)
            val user = SupabaseInstance.client.auth.currentUserOrNull()

            //al passaggio del fragment Profilo, passo i dati dell'utente (id, nome completo ed email) tramite Bundle
            val fragment = ProfiloFragment().apply {
                arguments = Bundle().apply {
                    putString("USER_ID", user?.id)
                    putString("NomeUtente", user?.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull)
                    putString("EmailUtente", user?.email)
                }
            }
            replaceFragment(fragment, "PROFILO")
            updateNavbarUI("PROFILO")
        }
    }

    //funzione che gestisce il passaggio dal fragment corrente al nuovo fragment
    private fun replaceFragment(fragment: Fragment, tag: String) {
        //ottieni il fragment corrente
        val currentFragment = supportFragmentManager.findFragmentByTag(tag)
        //se il fragment corrente esiste ed è visibile, non sostituisci nulla
        if (currentFragment != null && currentFragment.isVisible) return

        //rimuovi tutti i fragment tranne quello corrente
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment, tag)
            .commit()
    }

    //Funzione che aggiorna l'UI della navbar in base al fragment selezionato
    private fun updateNavbarUI(selectedTag: String) {
        //colori da utilizzare per evidenziare il fragment selezionato e per resettare gli altri
        val yellow = ContextCompat.getColor(this, R.color.fuelly_yellow_fluo)
        val grey = ContextCompat.getColor(this, R.color.grey_inactive)

        // Otteniamo il controller per gestire l'aspetto delle barre di sistema
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)


        //di default, resetta tutti i bottoni della navbar con i colori di default (grigio)
        // prima di evidenziare quello selezionato
        resetItem(binding.imgNavMappa, binding.txtNavMappa, grey)
        resetItem(binding.imgNavSalvati, binding.txtNavSalvati, grey)
        resetItem(binding.imgNavCerca, binding.txtNavCerca, grey)
        resetItem(binding.imgNavProfilo, binding.txtNavProfilo, grey)


        // Assicuriamoci che la barra di sistema rimanga sempre trasparente
        window.navigationBarColor = android.graphics.Color.TRANSPARENT

        //evidenzia il selezionato
        when (selectedTag) {
            "MAPPA" -> {
                highlightItem(binding.imgNavMappa, binding.txtNavMappa, yellow)
                // Nella mappa vogliamo la navigation bar chiara (icone scure) se il tema è chiaro
                windowInsetsController.isAppearanceLightNavigationBars = false
            }
            "SALVATI" -> {
                highlightItem(binding.imgNavSalvati, binding.txtNavSalvati, yellow)
                // Nei fragment richiesti, navigation bar scura (icone chiare/bianche)
                windowInsetsController.isAppearanceLightNavigationBars = true
            }
            "CERCA" -> {
                highlightItem(binding.imgNavCerca, binding.txtNavCerca, yellow)
                windowInsetsController.isAppearanceLightNavigationBars = true
            }
            "PROFILO" -> {
                highlightItem(binding.imgNavProfilo, binding.txtNavProfilo, yellow)
                windowInsetsController.isAppearanceLightNavigationBars = true
            }
        }
    }

    //Funzione che resetta l'item della navbar con colori iniziali
    private fun resetItem(img: ImageView, txt: TextView, color: Int) {
        img.setColorFilter(color)
        txt.setTextColor(color)
    }

    //Funzione che evidenzia l'item della navbar con colori diversi
    private fun highlightItem(img: ImageView, txt: TextView, color: Int) {
        img.setColorFilter(color)
        txt.setTextColor(color)
    }
}
