package com.example.fuelly

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.fuelly.classes.Info
import com.example.fuelly.classes.Recensione
import com.example.fuelly.classes.Utente
import com.example.fuelly.databinding.FragmentProfiloBinding
import com.example.fuelly.databinding.FragmentSalvatiBinding
import com.example.fuelly.supabase.SupabaseInstance
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class ProfiloFragment : Fragment() {

    private var _binding: FragmentProfiloBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?

    ): View {
        _binding = FragmentProfiloBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Impostiamo il LayoutManager subito, così la RecyclerView è pronta
        binding.listaRecensioni.layoutManager = LinearLayoutManager(requireContext())

        val btnLogout = binding.Logout

        btnLogout.setOnClickListener {
            logout()
        }

        //funzione di caricamento delle recensioni dell'utente
        caricaRecensioni(
            arguments?.getString("USER_ID"),
            arguments?.getString("NomeUtente"),
            arguments?.getString("EmailUtente")
        )

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Importante per evitare memory leak
    }

    private fun logout() {
        lifecycleScope.launch {
            try {
                // Effettua la logout su Supabase
                SupabaseInstance.client.auth.signOut()

                // Messaggio di conferma
                Toast.makeText(requireContext(), getString(R.string.profile_logout_success), Toast.LENGTH_SHORT).show()

                //TORNO ALLA SCHERMATA DI LOGIN
                val intent = Intent(requireContext(), LoginActivity::class.java)

                //FLAG CHE PULISCONO LA CRONOLOGIA DELL'APPLICAZIONE
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                startActivity(intent)


            } catch (e: Exception) {
                Log.e("Logout", "Errore durante il logout: ${e.message}")
                Toast.makeText(requireContext(), getString(R.string.profile_logout_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun caricaRecensioni(idUt: String?, nome: String?, email: String?) {
        //SE SONO LOGGATO IN SESSIONE
        if (idUt != null) {
            //CICLO DI VITA
            lifecycleScope.launch {
                binding.progressBar.visibility = View.VISIBLE
                binding.listaRecensioni.visibility = View.GONE

                try {

                    //visualizzo subito le informazioni dell'utente in sessione
                    binding.nomeUtente.text = nome
                    binding.emailUtente.text = email

                    //QUERY DI LETTURA DALLA TABELLA recensioni
                    val recensioni = SupabaseInstance.client.from("recensioni_benzinai")
                        .select {
                            filter { eq("idUtente", idUt) }
                        }.decodeList<Recensione>()

                    if (recensioni.isNotEmpty()) {
                        binding.listaRecensioni.visibility = View.VISIBLE

                        // Passiamo la lista e la lambda che chiama la funzione di eliminazione locale
                        val adapter = RecensioniAdapter(recensioni.toMutableList()) { recensioneDaEliminare ->
                            eliminaRecensioneDalProfilo(recensioneDaEliminare)
                        }
                        binding.listaRecensioni.adapter = adapter
                    } else {
                        binding.listaRecensioni.visibility = View.GONE
                        Toast.makeText(requireContext(), getString(R.string.profile_no_reviews), Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    Log.e("ProfiloFragment", "Errore: ${e.message}")
                    Toast.makeText(requireContext(), getString(R.string.profile_loading_error), Toast.LENGTH_SHORT).show()
                } finally {
                    binding.progressBar.visibility = View.GONE
                }
            }
        }
    }

    //funzione che elimina una recensione, con gestione degli errori e aggiornamento dell'UI
    private fun eliminaRecensioneDalProfilo(recensione: Recensione) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                //elimino la recensione dal database, usando il filtro per idRecensione e idUtente per sicurezza
                val tabella = if (recensione.tipo == "BENZINA") "recensioni_benzinai" else "recensioni_ev"

                //elimino la recensione usando un filtro per idRecensione e idUtente,
                // così da essere sicuri di eliminare solo la recensione specifica dell'utente
                SupabaseInstance.client.from(tabella).delete {
                    filter {
                        eq("idRecensione", recensione.idRecensione)
                        eq("idUtente", recensione.idUtente)
                    }
                }

                // Aggiorno l'UI dopo l'eliminazione, ricaricando le recensioni per riflettere la modifica
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), getString(R.string.profile_review_deleted), Toast.LENGTH_SHORT).show()
                    caricaRecensioni(
                        arguments?.getString("USER_ID"),
                        arguments?.getString("NomeUtente"),
                        arguments?.getString("EmailUtente")
                    )
                }

            } catch (e: Exception) {
                Log.e("ProfiloFragment", "Errore eliminazione: ${e.message}")
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), getString(R.string.profile_review_delete_error), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

}
