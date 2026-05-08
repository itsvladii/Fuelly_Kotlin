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

        //ID UTENTE, NOME, EMAIL CHE RECUPERO DALLA SESSIONE
        val idUtente = arguments?.getString("USER_ID")
        val nomeUtente = arguments?.getString("NomeUtente")
        val emailUtente = arguments?.getString("EmailUtente")

        val btnLogout = binding.Logout

        btnLogout.setOnClickListener {

            logout()

        }


        //SE SONO LOGGATO IN SESSIONE
        if (idUtente != null) {

            //CICLO DI VITA
            lifecycleScope.launch {

                try {

                    //visualizzo subito le informazioni dell'utente in sessione
                    binding.nomeUtente.text = nomeUtente
                    binding.emailUtente.text = emailUtente

                    //QUERY DI LETTURA DALLA TABELLA recensioni
                    val recensioni = SupabaseInstance.client.from("recensioni_benzinai")
                        .select {
                            filter { eq("idUtente", idUtente) }
                        }.decodeList<Recensione>()

                    if (recensioni.isNotEmpty()) {
                        binding.listaRecensioni.visibility = View.VISIBLE

                        // Basta passare la lista qui. L'Adapter farà il resto per ogni riga.
                        val adapter = RecensioniAdapter(recensioni.toMutableList())
                        binding.listaRecensioni.adapter = adapter
                    }
                    else
                    {
                        binding.listaRecensioni.visibility = View.GONE
                        Toast.makeText(requireContext(), "Nessuna recensione", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception)
                {
                    Log.e("ProfiloFragment", "Errore: ${e.message}")
                    Toast.makeText(requireContext(), "Errore nel caricamento dati", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // Importante per evitare memory leak
    }

    fun logout() {
        lifecycleScope.launch {
            try {
                // Effettua la logout su Supabase
                SupabaseInstance.client.auth.signOut()

                // Messaggio di conferma
                Toast.makeText(requireContext(), "Logout effettuato", Toast.LENGTH_SHORT).show()

                //TORNO ALLA SCHERMATA DI LOGIN
                val intent = Intent(requireContext(), LoginActivity::class.java)

                //FLAG CHE PULISCONO LA CRONOLOGIA DELL'APPLICAZIONE
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                
                startActivity(intent)


            } catch (e: Exception) {
                Log.e("Logout", "Errore durante il logout: ${e.message}")
                Toast.makeText(requireContext(), "Errore durante il logout", Toast.LENGTH_SHORT).show()
            }
        }
    }
}