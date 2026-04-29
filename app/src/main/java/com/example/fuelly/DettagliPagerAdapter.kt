package com.example.fuelly

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class DettagliPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PrezziFragment()
            1 -> RecensioniFragment()
            2 -> InfoFragment()
            else -> PrezziFragment()
        }
    }
}