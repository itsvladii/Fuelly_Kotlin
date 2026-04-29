package com.example.fuelly

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Benzinaio
import com.example.fuelly.databinding.FragmentSalvatiBinding

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
        // Placeholder: mostra i benzinai attualmente caricati in memoria
        // In futuro qui andrà la query a Supabase filtrata per l'ID utente

        val salvati = Benzinaio.listaVicini
        adapter.updateData(salvati)
    }

    // Aggiunto metodo per distruggere il binding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}