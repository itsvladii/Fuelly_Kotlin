package com.example.fuelly

import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.classes.Salvato
import com.example.fuelly.databinding.FragmentSalvatiBinding
import com.example.fuelly.supabase.SupabaseInstance
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class SalvatiFragment : Fragment() {

    // Aggiunto binding per il layout e inizializzazione del binding
    private var _binding: FragmentSalvatiBinding? = null

    // Aggiunto getter per il binding e settaggio del layout alla creazione dell'activity
    private val binding get() = _binding!!

    // Aggiunto adapter per il RecyclerView
    private lateinit var adapter: BenzinaioAdapter

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

        setupRecyclerView()
        caricaBenzinaiSalvati()
    }

    // Aggiunto metodo per configurare il RecyclerView
    private fun setupRecyclerView() {
        // Aggiunto layout manager per il RecyclerView
        binding.rvSalvati.layoutManager = LinearLayoutManager(requireContext())

        // Imposta l'adapter benzinaio per il RecyclerView
        adapter = BenzinaioAdapter(emptyList()) { benzinaio ->
            val intent = Intent(requireContext(), DettagliActivity::class.java).apply {
                putExtra("ID_ELEMENTO", benzinaio.id.toLong())
                putExtra("TIPO_ELEMENTO", "BENZINA")
            }
            startActivity(intent)
        }
        binding.rvSalvati.adapter = adapter
    }

    // Aggiunto metodo per caricare i benzinai salvati
    private fun caricaBenzinaiSalvati() {
        val user= SupabaseInstance.client.auth.currentUserOrNull()?: return
        lifecycleScope.launch {

            val benzinaiSalvati=SupabaseInstance.client.from("salvati")
                .select {
                    filter {
                        eq("idUtente", user.id)
                    }
                }.decodeList<Salvato>()

            var listaBenzinaio: MutableList<Benzinaio> = mutableListOf()
            for (salvato in benzinaiSalvati) {
                val benzinaio = Benzinaio.listaVicini.find { it.id.toLong() == salvato.idBenzinaio}
                if (benzinaio != null) {
                    listaBenzinaio.add(benzinaio)
                }
            }
            adapter.updateData(listaBenzinaio)

            }

        }

    // Aggiunto metodo per distruggere il binding
    override fun onDestroyView() {
        super.onDestroyView()

        _binding = null
    }

}


