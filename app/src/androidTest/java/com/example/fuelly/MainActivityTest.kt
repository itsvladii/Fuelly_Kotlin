package com.example.fuelly

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun testMapsFragmentElementsAreDisplayed() {
        // Verifica che la mappa sia presente (FragmentContainerView con id @id/map)
        onView(withId(R.id.map)).check(matches(isDisplayed()))

        // Verifica che la barra di ricerca sia visibile
        onView(withId(R.id.searchCard)).check(matches(isDisplayed()))
        onView(withId(R.id.searchView)).check(matches(isDisplayed()))

        // Verifica che i pulsanti dei filtri siano visibili
        onView(withId(R.id.btnFiltroBenzina)).check(matches(isDisplayed()))
        onView(withId(R.id.btnFiltroEV)).check(matches(isDisplayed()))

        // Verifica che il pulsante della mia posizione sia visibile
        onView(withId(R.id.btnMyLocation)).check(matches(isDisplayed()))
    }

    @Test
    fun testBottomNavigationIsDisplayed() {
        // Verifica che la barra di navigazione inferiore (custom in questo progetto) sia visibile
        // Basandoci sul layout di MainActivity, verifichiamo i pulsanti della navbar
        onView(withId(R.id.btnNavMappa)).check(matches(isDisplayed()))
        onView(withId(R.id.btnNavSalvati)).check(matches(isDisplayed()))
        onView(withId(R.id.btnNavCerca)).check(matches(isDisplayed()))
        onView(withId(R.id.btnNavProfilo)).check(matches(isDisplayed()))
    }
}
