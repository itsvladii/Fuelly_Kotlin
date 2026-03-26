package com.example.fuelly.classes

data class Benzinai(
    val id: Int,
    val gestore: String,
    val bandiera: String,
    val tipoImpianto: String,
    val nomeImpianto: String,
    val indirizzo: String,
    val comune: String,
    val provincia: String,
    val lat: Double,
    val lon: Double
)