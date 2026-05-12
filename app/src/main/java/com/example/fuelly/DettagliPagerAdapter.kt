package com.example.fuelly

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

// Adapter per gestire i fragment all'interno del ViewPager2
class DettagliPagerAdapter(activity: FragmentActivity, private val tipo: String?) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = if (tipo == "EV") 2 else 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PrezziFragment()
            1 -> RecensioniFragment()
            2 -> InfoFragment()
            else -> PrezziFragment()
        }
    }
}
