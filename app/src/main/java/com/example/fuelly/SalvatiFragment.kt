package com.example.fuelly

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.classes.ColonninaEV
import com.example.fuelly.classes.Salvato
import com.example.fuelly.databinding.FragmentSalvatiBinding
import com.example.fuelly.supabase.SupabaseInstance
import com.example.fuelly.utils.Utils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class SalvatiFragment : Fragment() {

    // Aggiunto binding per il layout e inizializzazione del binding
    private var _binding: FragmentSalvatiBinding? = null

    // Aggiunto getter per il binding e settaggio del layout alla creazione dell'activity
    private val binding get() = _binding!!

    // Aggiunto adapter per il RecyclerView
    private lateinit var adapter: StazioneAdapter
    private var userLat: Double? = null
    private var userLon: Double? = null



    // onCreateView per il layout dell'activity
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSalvatiBinding.inflate(inflater, container, false)
        return binding.root
    }

    // onViewCreated per il fragment e inizializzazione del RecyclerView
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Imposta la navigation bar di sistema trasparente con icone scure
        requireActivity().window.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(this, decorView).isAppearanceLightNavigationBars = true
        }

        val btnSalvatiBenzinai = binding.salvatiBenzinai
        val btnSalvatiColonnine = binding.salvatiColonnine

        //alla creazione della view eseguo la funzione setupRecyclerView()
        setupRecyclerView()
        caricaTuttiSalvati()

        //al click del bottone dei benzinai salvati
        btnSalvatiBenzinai.setOnClickListener {

            caricaBenzinaiSalvati()
        }

        //al click del bottone delle colonnine salvate
        btnSalvatiBenzinai.setOnClickListener {

            caricaColonnineSalvate()
        }
    }

    // Azione da fare quando torno sul fragment
    override fun onResume() {
        super.onResume()
        // Ricarica i dati ogni volta che il fragment torna visibile
        caricaTuttiSalvati()

    }

    // Aggiunto metodo per configurare il RecyclerView
    private fun setupRecyclerView() {
        // Aggiunto layout manager per il RecyclerView
        binding.rvSalvati.layoutManager = LinearLayoutManager(requireContext())

        // Imposta l'adapter benzinaio per il RecyclerView
        adapter = StazioneAdapter(emptyList(), userLat, userLon) { item ->
            val intent = Intent(requireContext(), DettagliActivity::class.java)
            when (item) {
                is Benzinaio -> {
                    intent.putExtra("ID_ELEMENTO", item.id.toLong())
                    intent.putExtra("TIPO_ELEMENTO", "BENZINA")
                }
                is ColonninaEV -> {
                    intent.putExtra("ID_ELEMENTO", item.id.toLong())
                    intent.putExtra("TIPO_ELEMENTO", "EV")
                }
            }
            // Passiamo la posizione utente ai dettagli
            intent.putExtra("USER_LAT", userLat ?: 0.0)
            intent.putExtra("USER_LON", userLon ?: 0.0)
            startActivity(intent)
        }
        binding.rvSalvati.adapter = adapter
    }


    //metodo per caricare i dati dei benzinai salvati
    private fun caricaBenzinaiSalvati() {

      adapter.updateData(Benzinaio.listaSalvati)

    }

    //metodo per caricare i dati delle colonnine salvate
    private fun caricaColonnineSalvate(){

        adapter.updateData(ColonninaEV.listaSalvati)

    }

    private fun caricaTuttiSalvati(){

        adapter.updateData(Benzinaio.listaSalvati + ColonninaEV.listaSalvati)

    }




    // Aggiunto metodo per distruggere il binding
    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }



}
