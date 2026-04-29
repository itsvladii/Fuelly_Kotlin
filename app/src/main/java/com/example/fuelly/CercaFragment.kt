package com.example.fuelly

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.databinding.FragmentCercaBinding

class CercaFragment : Fragment() {
    // Aggiunto binding per il layout e inizializzazione del binding
    private var _binding: FragmentCercaBinding? = null

    // Aggiunto getter per il binding e settaggio del layout alla creazione dell'activity
    private val binding get() = _binding!!

    // Aggiunto adapter per il RecyclerView
    private lateinit var adapter: BenzinaioAdapter

    // onCreateView per il layout dell'activity
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCercaBinding.inflate(inflater, container, false)
        return binding.root
    }

    // onViewCreated per il fragment e inizializzazione del RecyclerView
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Imposta la navigation bar di sistema trasparente con icone scure (background bianco)
        requireActivity().window.apply {
            navigationBarColor = Color.TRANSPARENT
            WindowCompat.getInsetsController(this, decorView).isAppearanceLightNavigationBars = true
        }

        setupRecyclerView()
        caricaBenzinaiVicini()
    }

    // Aggiunto metodo per caricare i benzinai vicini
    private fun caricaBenzinaiVicini() {
        adapter.updateData(Benzinaio.listaVicini)
    }

    // Aggiunto metodo per configurare il RecyclerView
    private fun setupRecyclerView() {
        // Aggiunto layout manager per il RecyclerView
        binding.rvCerca.layoutManager = LinearLayoutManager(requireContext())

        // Imposta l'adapter benzinaio per il RecyclerView
        adapter = BenzinaioAdapter(emptyList()) { benzinaio ->
            val intent = Intent(requireContext(), DettagliActivity::class.java).apply {
                putExtra("ID_ELEMENTO", benzinaio.id.toLong())
                putExtra("TIPO_ELEMENTO", "BENZINA")
            }
            startActivity(intent)
        }
        binding.rvCerca.adapter = adapter
    }

    // Aggiunto metodo per distruggere il binding
    override fun onDestroyView() {
        super.onDestroyView()
        // Ripristina lo stato precedente (icone chiare su sfondo trasparente)
        requireActivity().window.apply {
            navigationBarColor = Color.TRANSPARENT
            WindowCompat.getInsetsController(this, decorView).isAppearanceLightNavigationBars = false
        }
        _binding = null
    }
}
