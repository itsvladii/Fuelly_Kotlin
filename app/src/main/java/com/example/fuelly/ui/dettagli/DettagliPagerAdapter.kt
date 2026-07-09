package com.example.fuelly.ui.dettagli

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.fuelly.ui.dettagli.InfoFragment

//adapter per gestire i fragment all'interno del ViewPager2
class DettagliPagerAdapter(activity: FragmentActivity, private val tipo: String?) : FragmentStateAdapter(activity) {

    //GESTORE PER LA TABBAR DEL DettagliActivity
    /*EV: 2 sottosezioni: -> PrezziFragment, Recensioni Fragment
    BENZINA: 3 sottosezioni: -> PrezziFragment, Recensioni Fragment, InfoFragment
     */
    override fun getItemCount(): Int = if (tipo == "EV") 2 else 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PrezziFragment() //creo il primo fragment
            1 -> RecensioniFragment() //creo il secondo fragment
            2 -> InfoFragment() //creo il terzo fragment
            else -> PrezziFragment()
        }
    }
}
