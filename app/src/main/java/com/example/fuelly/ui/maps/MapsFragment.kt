package com.example.fuelly.ui.maps

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.fuelly.ui.dettagli.DettagliActivity
import com.example.fuelly.R
import com.example.fuelly.databinding.FragmentMapsBinding
import com.example.fuelly.repository.model.Benzinaio
import com.example.fuelly.repository.model.ColonninaEV
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Locale

class MapsFragment : Fragment(), OnMapReadyCallback {
    private val viewModel: MapsViewModel by viewModels()

    //lista di tutti i marker della mappa
    private val markersBenzina = mutableListOf<Marker>()
    private val markersEV = mutableListOf<Marker>()

    private lateinit var mMap: GoogleMap

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!

    //invece di scrivere binding.root.findViewById(R.id.id) ogni volta, cosi scrivo solo findViewById
    private fun <T : View> findViewById(id: Int): T = binding.root.findViewById(id)
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupUI(view)
        observeViewModel()
    }

    //funzione di primo setup del fragment Maps
    private fun setupUI(view: View) {
        //imposta la navigation bar di sistema trasparente con icone bianche
        requireActivity().window.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(this, decorView).isAppearanceLightNavigationBars = false
        }

        //inizializza il fusedLocationClient (servizio di geolocalizzazione di Google)
        // per ottenere la posizione dell'utente
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        ViewCompat.setOnApplyWindowInsetsListener(binding.searchCard) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top + (16 * resources.displayMetrics.density).toInt()
            }

            windowInsets
        }


        //listener per i bottoni di filtro
        binding.btnFiltroBenzina.setOnClickListener {
            viewModel.toggleBenzinaFilter()
        }

        binding.btnFiltroEV.setOnClickListener {
            viewModel.toggleEVFilter()
        }

        //listener per il pulsante di my location
        binding.btnMyLocation.setOnClickListener {
            moveToCurrentLocation(refreshData = true)
        }

        //listener per il pulsante "Cerca in questa zona"
        binding.btnSearchArea.setOnClickListener {
            cercaInQuestaZona()
        }

        //richiamo della funzione di configurazione della barra di ricerca
        setupSearchView()
    }

    private fun observeViewModel() {
        viewModel.benzinai.observe(viewLifecycleOwner) {
            if (::mMap.isInitialized) {
                aggiornaMarkerMappa()
            }
        }
        viewModel.colonnine.observe(viewLifecycleOwner) {
            if (::mMap.isInitialized) {
                aggiornaMarkerMappa()
            }
        }
        viewModel.isBenzinaActive.observe(viewLifecycleOwner) { active ->
            binding.btnFiltroBenzina.alpha = if (active) 1.0f else 0.5f
            if (::mMap.isInitialized) {
                filtraMarker(active, viewModel.isEVActive.value ?: true)
            }
        }
        viewModel.isEVActive.observe(viewLifecycleOwner) { active ->
            binding.btnFiltroEV.alpha = if (active) 1.0f else 0.5f
            if (::mMap.isInitialized) {
                filtraMarker(viewModel.isBenzinaActive.value ?: true, active)
            }
        }
    }

    //Funzione di configurazione della barra di ricerca
    private fun setupSearchView() {
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            //listener per l'invio del testo nella barra di ricerca
            override fun onQueryTextSubmit(query: String?): Boolean {
                //quando l'utente preme invio, esegui la ricerca
                if (!query.isNullOrBlank()) {
                    cercaLuogo(query)
                }
                binding.searchView.clearFocus()
                return true
            }

            //listener per il cambio di testo nella barra di ricerca
            override fun onQueryTextChange(newText: String?): Boolean {
                return false
            }
        })
    }

    //Funzione per cercare un luogo tramite Geocoding
    private fun cercaLuogo(indirizzo: String) {
        // Geocoding per ottenere le coordinate geografiche
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                //ottieni le coordinate geografiche in base all'indirizzo fornito
                val list = geocoder.getFromLocationName(indirizzo, 1)
                //se ci sono risultati, ottieni la prima posizione e crea un marker
                if (!list.isNullOrEmpty()) {
                    val location = list[0]
                    //crea un LatLng con le coordinate geografiche
                    val latLng = LatLng(location.latitude, location.longitude)

                    //centra la mappa sulla posizione e esegui la ricerca
                    withContext(Dispatchers.Main) {
                        //centra la mappa sulla posizione
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 14f))
                        //esegui la ricerca tramite ViewModel
                        viewModel.eseguiRicerca(location.latitude, location.longitude)
                        binding.btnSearchArea.visibility = View.GONE
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.location_not_found),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Fuelly", "Errore Geocoding: ${e.message}")
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        try {
            //imposto lo sitle custom presente su res/raw
            val success = mMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.map_style))
            if (!success) Log.e("Fuelly", "Errore nel caricamento dello stile JSON.")

            //rimozione di alcune feature non necessarie sulla mappa
            mMap.isBuildingsEnabled = false //rimozione degli edifici 3D
            mMap.uiSettings.isTiltGesturesEnabled = false //rimozione della gesture di tilt della mappa
            mMap.uiSettings.isRotateGesturesEnabled = false //rimozione della gesture di rotazione della mappa
            mMap.uiSettings.isMapToolbarEnabled = false //rimozione della toolbar della mappa

            //se ho i permessi di geolocalizzazione, posiziono la camera della mappa sulla posizione dell'utente
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                mMap.isMyLocationEnabled = true
                mMap.uiSettings.isMyLocationButtonEnabled = false
                moveToCurrentLocation(refreshData = false)
            }

            //aggiungi marker iniziali
            aggiornaMarkerMappa()

            //listener per il movimento della camera per mostrare il pulsante "Cerca in questa zona"
            mMap.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    binding.btnSearchArea.visibility = View.VISIBLE
                }
            }

            //recupero il layout della card
            val card = binding.root.findViewById<CardView>(R.id.stationCard)
            val btnMyLocation = binding.btnMyLocation

            //ricavo la custom bottom navbar
            val mainActivityNav = requireActivity().findViewById<LinearLayout>(R.id.customBottomNav)

            //listener per il click di un marker sulla mappa
            mMap.setOnMarkerClickListener { marker ->
                //rimuovi la card se presente
                val data = marker.tag
                if (data is Benzinaio) setupCardBenzinaio(data) //se il marker è di un benzinaio, chiamo il setup della card per i benzinai
                else if (data is ColonninaEV) setupCardElettrica(data) //se il marker è di una colonnina EV, chiamo il setup della card per le colonnine

                //nascondo la bottom navbar
                mainActivityNav?.animate()?.translationY(600f)?.setDuration(300)?.start()
                btnMyLocation.animate().translationY(600f).setDuration(300).start()

                //mostro la card
                card.visibility = View.VISIBLE
                card.alpha = 0f
                card.animate().alpha(1f).setDuration(300).start() //animazione di fade-in della card

                //listener per il click sulla card
                card.setOnClickListener {
                    //apro il dettaglio del benzinaio o della colonnina EV in base al tipo di marker cliccato
                    if (data is Benzinaio) apriDettaglio(data.id.toLong(), "BENZINA", data.gestore)
                    else if (data is ColonninaEV) apriDettaglio(data.id.toLong(), "EV", data.operatore)
                }
                false
            }

            //listener per il listener del click sulla mappa
            mMap.setOnMapClickListener {
                //nascondo la card e mostro la bottom navbar se la card è attiva
                if (card.isVisible) {
                    card.animate().alpha(0f).setDuration(200).withEndAction { card.visibility = View.GONE }.start()
                    mainActivityNav?.animate()?.translationY(0f)?.setDuration(300)?.start()
                    btnMyLocation.animate().translationY(0f).setDuration(300).start()
                }
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("Fuelly", "File map_style non trovato: ", e)
        }
    }

    //funzione per aggiornare i marker sulla mappa in base ai risultati della ricerca
    private fun aggiornaMarkerMappa() {
        //pulisco i marker esistenti
        markersBenzina.forEach { it.remove() }
        markersEV.forEach { it.remove() }
        markersBenzina.clear()
        markersEV.clear()

        //aggiungo i nuovi marker dei benzinai e delle colonnine EV
        val iconaCustom = vectorToBitmap(R.drawable.ic_fuel_marker)
        for (stazione in viewModel.benzinai.value ?: emptyList()) {
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(LatLng(stazione.lat, stazione.lon))
                    .icon(iconaCustom)
                    .anchor(0.5f, 1.0f)
            )
            marker?.tag = stazione
            marker?.isVisible = viewModel.isBenzinaActive.value ?: true
            marker?.let { markersBenzina.add(it) }
        }

        val iconaEV = vectorToBitmap(R.drawable.ic_ev_marker)
        for (ev in viewModel.colonnine.value ?: emptyList()) {
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(LatLng(ev.lat, ev.lon))
                    .icon(iconaEV)
                    .anchor(0.5f, 1.0f)
            )
            marker?.tag = ev
            marker?.isVisible = viewModel.isEVActive.value ?: true
            marker?.let { markersEV.add(it) }
        }
    }

    //funzione per cercare in questa zona
    private fun cercaInQuestaZona() {
        binding.btnSearchArea.visibility = View.GONE
        //ottieni le coordinate della posizione corrente
        val center = mMap.cameraPosition.target
        viewModel.eseguiRicerca(center.latitude, center.longitude)
    }

    //funzione che sposta la camera della mappa sulla posizione corrente dell'utente
    private fun moveToCurrentLocation(refreshData: Boolean = false) {
        //verifico di avere i permessi di geolocalizzazione, altrimenti esco dalla funzione
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                //creo un LatLng con le coordinate della posizione dell'utente
                val currentLatLng = LatLng(location.latitude, location.longitude)
                //centro la mappa sulla posizione dell'utente
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 14f))
                if (refreshData) {
                    binding.btnSearchArea.visibility = View.GONE
                    //eseguo la ricerca tramite ViewModel
                    viewModel.eseguiRicerca(location.latitude, location.longitude)
                }
            }
        }
    }

    //funzione di setup della card se cliccato un marker di un benzinaio
    private fun setupCardBenzinaio(b: Benzinaio) {
        val card = binding.root.findViewById<CardView>(R.id.stationCard)
        card.setCardBackgroundColor("#0B3D2E".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtStationName).setTextColor("#DFFF00".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtStationCity).setTextColor("#DFFF00".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtStationAddress).setTextColor("#DFFF00".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtPrice).setTextColor("#DFFF00".toColorInt())

        binding.root.findViewById<TextView>(R.id.txtStationName).text = b.bandiera + " "
        binding.root.findViewById<TextView>(R.id.txtStationCity).apply {
            text = b.comune + " (" + b.provincia + ")"
            isSelected = true
        }
        binding.root.findViewById<TextView>(R.id.txtStationAddress).apply {
            text = b.indirizzo
            isSelected = true
        }

        val benzinaStr = if (b.prezzoBenzina > 0) "${b.prezzoBenzina}" else "N.D."
        val dieselStr = if (b.prezzoDiesel > 0) "${b.prezzoDiesel}" else "N.D."
        findViewById<TextView>(R.id.txtPrice).text = "Self | B: $benzinaStr · D: $dieselStr"
        findViewById<ImageView>(R.id.imgPompa).setImageResource(b.getLogoResource())
    }

    //funzione di setup della card se cliccato un marker di una colonnina EV
    private fun setupCardElettrica(ev: ColonninaEV) {
        val card = binding.root.findViewById<CardView>(R.id.stationCard)
        card.setCardBackgroundColor("#0B101E".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtStationName).setTextColor("#00FFC2".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtStationCity).setTextColor("#00FFC2".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtStationAddress).setTextColor("#00FFC2".toColorInt())
        binding.root.findViewById<TextView>(R.id.txtPrice).setTextColor("#00FFC2".toColorInt())

        binding.root.findViewById<TextView>(R.id.txtStationName).text = ev.titolo + " "
        binding.root.findViewById<TextView>(R.id.txtStationCity).apply {
            text = ev.comune
            isSelected = true
        }
        binding.root.findViewById<TextView>(R.id.txtStationAddress).apply {
            text = ev.indirizzo
            isSelected = true
        }

        // --- LOGICA DI CALCOLO REALE DELLE PRESE ---
        var totalePrese = 0
        if (!ev.connettoriJson.isNullOrEmpty()) {
            try {
                val array = JSONArray(ev.connettoriJson)
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    // Sommiamo il campo 'quantita' che abbiamo aggiunto alla RPC
                    totalePrese += obj.optInt("quantita", 1)
                }
            } catch (e: Exception) {
                totalePrese = ev.numPunti // Fallback al valore base in caso di errore
            }
        } else {
            totalePrese = ev.numPunti
        }

        // Usiamo il valore calcolato invece di ev.numPunti
        findViewById<TextView>(R.id.txtPrice).text = "$totalePrese prese"
        findViewById<ImageView>(R.id.imgPompa).setImageResource(ev.getLogoResource())
    }


    //funzione di filtraggio dei marker (nasconde i marker)
    private fun filtraMarker(mostraBenzina: Boolean, mostraEV: Boolean) {
        markersBenzina.forEach { it.isVisible = mostraBenzina }
        markersEV.forEach { it.isVisible = mostraEV }
        binding.root.findViewById<View>(R.id.stationCard).visibility = View.GONE
    }

    //funzione che gestisce l'intent di passaggio all'activity DettagliActivity
    private fun  apriDettaglio(id: Long, tipo: String, gestore: String) {
        //all'intent aggiungo ID_ELEMENTO e TIPO_ELEMENTO
        val intent = Intent(requireContext(), DettagliActivity::class.java).apply {
            //passo tramite putExtra i parametri ad un' altra activity o fragment
            putExtra("ID_ELEMENTO", id)
            putExtra("TIPO_ELEMENTO", tipo)
            putExtra("NOME_BENZINAIO", gestore)
        }
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                //all'intent aggiungo anche la posizione, se ho i permessi
                intent.putExtra("USER_LAT", location?.latitude ?: 0.0)
                intent.putExtra("USER_LON", location?.longitude ?: 0.0)
                startActivity(intent)
            }
        } else startActivity(intent)
    }

    //TODO: da valutare
    private fun vectorToBitmap(drawableId: Int): BitmapDescriptor {
        val vectorDrawable = ResourcesCompat.getDrawable(resources, drawableId, null)
            ?: return BitmapDescriptorFactory.defaultMarker()

        val width = vectorDrawable.intrinsicWidth
        val height = vectorDrawable.intrinsicHeight

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, width, height)
        vectorDrawable.draw(canvas)

        // Trova i limiti dei pixel non trasparenti per "restringere" la hitbox
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = (bitmap.getPixel(x, y) shr 24) and 0xff
                if (alpha > 0) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }

        // Se l'immagine è vuota, restituisci il bitmap originale
        if (maxX < minX || maxY < minY) return BitmapDescriptorFactory.fromBitmap(bitmap)

        // Crea un nuovo bitmap ritagliato solo sull'area visibile del pin
        val croppedBitmap = Bitmap.createBitmap(
            bitmap,
            minX,
            minY,
            maxX - minX + 1,
            maxY - minY + 1
        )
        return BitmapDescriptorFactory.fromBitmap(croppedBitmap)
    }

    //metodo per distruggere il binding
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}