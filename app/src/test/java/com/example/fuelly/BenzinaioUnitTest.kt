package com.example.fuelly

import com.example.fuelly.repository.model.Benzinaio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenzinaioUnitTest {

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

        assertTrue(shareText.contains("Eni"))
        assertTrue(shareText.contains("Via Roma 1"))
        assertTrue(shareText.contains("Milano"))
        assertTrue(shareText.contains("1.750"))
        assertTrue(shareText.contains("1.650"))
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
        // Non dovrebbe contenere i prezzi se sono 0
        assertEquals(false, shareText.contains("Benzina:"))
        assertEquals(false, shareText.contains("Diesel:"))
    }
}
