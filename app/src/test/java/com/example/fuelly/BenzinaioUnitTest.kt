package com.example.fuelly

import com.example.fuelly.repository.model.Benzinaio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class BenzinaioUnitTest {

    @Before
    fun setup() {
        // Impostiamo il locale US per garantire che il separatore decimale sia il punto
        // e il test sia coerente su ogni macchina.
        Locale.setDefault(Locale.US)
    }

    @Test
    fun testGetShareText() {
        val benzinaio = Benzinaio(
            id = 1,
            gestore = "Eni",
            bandiera = "Eni",
            tipoImpianto = "Stradale",
            nomeImpianto = "Stazione Prova",
            indirizzo = "Via Roma 1",
            comune = "Milano",
            provincia = "MI",
            lat = 45.0,
            lon = 9.0,
            prezzoBenzina = 1.750,
            prezzoDiesel = 1.650,
            prezzoGPL = 0.0,
            prezzoMetano = 0.0
        )

        val shareText = benzinaio.getShareText()

        assertTrue("Il testo dovrebbe contenere la bandiera", shareText.contains("Eni"))
        assertTrue("Il testo dovrebbe contenere l'indirizzo", shareText.contains("Via Roma 1"))
        assertTrue("Il testo dovrebbe contenere il comune", shareText.contains("Milano"))
        assertTrue("Il testo dovrebbe contenere il prezzo benzina formattato", shareText.contains("1.750"))
        assertTrue("Il testo dovrebbe contenere il prezzo diesel formattato", shareText.contains("1.650"))
        assertTrue("Il testo dovrebbe contenere il link a Google Maps", shareText.contains("google.com/maps"))
    }

    @Test
    fun testGetLogoResource() {
        // Creiamo vari benzinai con diverse bandiere
        val bEni = Benzinaio(1, "Eni", "Eni", "", "", "", "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val bIp = Benzinaio(2, "IP", "IP", "", "", "", "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val bEsso = Benzinaio(3, "Esso", "Esso", "", "", "", "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val bSconosciuto = Benzinaio(4, "Generico", "MarcaX", "", "", "", "", "", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        // Verifichiamo che restituiscano i drawable corretti
        assertEquals(R.drawable.logo_agipeni, bEni.getLogoResource())
        assertEquals(R.drawable.logo_ip, bIp.getLogoResource())
        assertEquals(R.drawable.logo_esso, bEsso.getLogoResource())
        assertEquals(R.drawable.fuelly_logo_foreground, bSconosciuto.getLogoResource())
    }

    @Test
    fun testGetShareTextNoPrices() {
        val benzinaio = Benzinaio(
            id = 1,
            gestore = "Q8",
            bandiera = "Q8",
            tipoImpianto = "Stradale",
            nomeImpianto = "Stazione Q8",
            indirizzo = "Via Torino 10",
            comune = "Torino",
            provincia = "TO",
            lat = 45.0,
            lon = 7.0,
            prezzoBenzina = 0.0,
            prezzoDiesel = 0.0,
            prezzoGPL = 0.0,
            prezzoMetano = 0.0
        )

        val shareText = benzinaio.getShareText()

        assertTrue(shareText.contains("Q8"))
        // Non dovrebbe contenere le righe dei prezzi se sono impostati a 0.0
        assertEquals("Non dovrebbe mostrare il prezzo Benzina se è 0", false, shareText.contains("Benzina:"))
        assertEquals("Non dovrebbe mostrare il prezzo Diesel se è 0", false, shareText.contains("Diesel:"))
    }
}
