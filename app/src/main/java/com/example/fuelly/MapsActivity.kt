package com.example.fuelly

import android.content.res.Resources
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.example.fuelly.databinding.ActivityMapsBinding
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.MapStyleOptions

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        try {
            mMap = googleMap

            //1. imposto lo stile custom in JSON della mappa
            val success = mMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style)
            )
            if (!success) Log.e("Maps", "Errore nello stile della mappa.")


            //2. aggiungi un marker

            //imposto le coordinate per il marker
            val posIniziale = LatLng(43.587378338300255, 13.516612657024158)
            //aggiungo il marker alla mappa
            mMap.addMarker(MarkerOptions().position(posIniziale))
                ?.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.pin_fuel))
            //sposta la visuale alla posizione del marker
            mMap.moveCamera(CameraUpdateFactory.newLatLng(posIniziale))
            //zoom della mappa alla posizione del marker
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(posIniziale, 15f))

            //3. rimuovo la visualizazzione degli edifici in 3D
            mMap.isBuildingsEnabled=false
            mMap.uiSettings.isTiltGesturesEnabled=false




        } catch (e: Resources.NotFoundException) {
            Log.e("Maps", "File dello stile non trovato: ", e)
        }

    }
}