package com.example.fuelly;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.example.fuelly.repository.model.Recensione;

import org.junit.Test;

import java.util.UUID;

/**
 * Unit test per la classe Recensione, che gestisce i feedback sugli impianti.
 */
public class RecensioniUnitTest {

    @Test
    public void testCreazioneRecensioneBenzinaio() {
        // 1. Dati di test
        String idUtente = "user_uuid_12345";
        long idImpianto = 7890L;
        String idRecensione = UUID.randomUUID().toString();
        int rating = 4;
        String descrizione = "Prezzi nella media, stazione pulita.";
        String data = "2023-11-20T15:30:00";
        String nomeUtente = "Luigi Verdi";
        String avatar = "https://fuelly.app/avatars/luigi.jpg";
        String tipo = "BENZINA";

        // 2. Istanza dell'oggetto (Uso del costruttore completo)
        Recensione recensione = new Recensione(
                idUtente,
                idImpianto,
                idRecensione,
                rating,
                descrizione,
                data,
                nomeUtente,
                avatar,
                tipo
        );

        // 3. Verifiche
        assertNotNull("L'oggetto recensione non dovrebbe essere null", recensione);
        assertEquals("L'ID utente deve corrispondere", idUtente, recensione.getIdUtente());
        assertEquals("L'ID impianto deve corrispondere", idImpianto, recensione.getIdBenzinaio());
        assertEquals("L'ID recensione deve corrispondere", idRecensione, recensione.getIdRecensione());
        assertEquals("Il rating deve corrispondere", rating, recensione.getRating());
        assertEquals("La descrizione deve corrispondere", descrizione, recensione.getDescRecensione());
        assertEquals("Il nome visualizzato deve corrispondere", nomeUtente, recensione.getNome());
        assertEquals("La tipologia dell'elemento deve corrispondere", tipo, recensione.getTipo());
    }

    @Test
    public void testRecensioneDefaultValues() {
        // Test per verificare i campi opzionali o con valori predefiniti
        Recensione recensione = new Recensione(
                "user_default",
                123L,
                "rec_unique_id",
                5,
                "Top!",
                null, // data_inserimento
                "Utente Anonimo", // nome
                null, // avatar_url
                "BENZINA"
        );

        assertNotNull(recensione);
        assertEquals("Utente Anonimo", recensione.getNome());
        assertEquals(null, recensione.getAvatar_url());
    }
}
