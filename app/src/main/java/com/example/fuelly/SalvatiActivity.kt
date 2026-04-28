package com.example.fuelly

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fuelly.classes.Benzinaio

class SalvatiActivity : AppCompatActivity() {

    private lateinit var adapter: BenzinaioAdapter
    private lateinit var rvSalvati: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_salvati)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightNavigationBars = true // Icone scure in basso
        controller.isAppearanceLightStatusBars = true     // Icone scure in alto


        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupRecyclerView()
        caricaBenzinaiSalvati()

        // Listener per la navigazione
        findViewById<LinearLayout>(R.id.btnNavMappa).setOnClickListener {
            startActivity(Intent(this, MapsActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        rvSalvati = findViewById(R.id.rvSalvati)
        rvSalvati.layoutManager = LinearLayoutManager(this)
        
        adapter = BenzinaioAdapter(emptyList()) { benzinaio ->
            val intent = Intent(this, DettagliActivity::class.java).apply {
                putExtra("ID_ELEMENTO", benzinaio.id.toLong())
                putExtra("TIPO_ELEMENTO", "BENZINA")
            }
            startActivity(intent)
        }
        rvSalvati.adapter = adapter
    }

    private fun caricaBenzinaiSalvati() {
        // Placeholder: mostra i benzinai attualmente caricati in memoria
        // In futuro qui andrà la query a Supabase filtrata per l'ID utente
        val salvati = Benzinaio.listaVicini
        adapter.updateData(salvati)
    }
}