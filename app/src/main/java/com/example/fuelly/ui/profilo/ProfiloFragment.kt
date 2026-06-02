package com.example.fuelly.ui.profilo

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.R
import com.example.fuelly.databinding.FragmentProfiloBinding
import com.example.fuelly.repository.model.Recensione
import com.example.fuelly.ui.auth.LoginActivity
import com.example.fuelly.ui.dettagli.RecensioniAdapter

class ProfiloFragment : Fragment() {

    private var _binding: FragmentProfiloBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfiloViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View {
        _binding = FragmentProfiloBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.listaRecensioni.layoutManager = LinearLayoutManager(requireContext())

        setupUserData()
        observeViewModel()

        //listener per il logout
        binding.Logout.setOnClickListener {
            viewModel.logout()
        }

        viewModel.caricaDatiUtente(arguments?.getString("USER_ID"))
    }

    private fun setupUserData() {
        binding.nomeUtente.text = arguments?.getString("NomeUtente")
        binding.emailUtente.text = arguments?.getString("EmailUtente")
    }

    private fun observeViewModel() {
        //se recensioni cambia in qualche modo nella ViewModel, effettua le seguenti operazioni
        viewModel.recensioni.observe(viewLifecycleOwner) { recensioni ->
            if (recensioni.isNotEmpty()) {
                binding.listaRecensioni.visibility = View.VISIBLE //mostra listaRecensioni
                val adapter = RecensioniAdapter(recensioni.toMutableList()) { recensione ->
                    viewModel.eliminaRecensione(recensione)
                    Toast.makeText(requireContext(), getString(R.string.profile_review_deleted), Toast.LENGTH_SHORT).show()
                }
                binding.listaRecensioni.adapter = adapter
            } else {
                binding.listaRecensioni.visibility = View.GONE //nascondi listaRecensioni se non ci sono recensioni
            }
        }

        //se isLoading è true nella ViewModel, mostra la progress bar, altrimenti nascondila
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }

        //se il logout è stato effettuato con successo, passa alla activity Login con un intent
        viewModel.logoutSuccess.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), getString(R.string.profile_logout_success), Toast.LENGTH_SHORT).show()
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Importante per evitare memory leak
    }
}
