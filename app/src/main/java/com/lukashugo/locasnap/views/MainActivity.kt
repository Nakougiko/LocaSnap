package com.lukashugo.locasnap.views

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.lukashugo.locasnap.R
import com.lukashugo.locasnap.views.fragments.MapFragment
import com.lukashugo.locasnap.views.fragments.CameraFragment
import com.lukashugo.locasnap.views.fragments.ProfileFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private var selectedItemId = R.id.nav_home // Sélection par défaut

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // Charger la carte par défaut
        loadFragment(MapFragment())

        bottomNavigationView.setOnItemSelectedListener { item ->
            if (item.itemId == selectedItemId) {
                return@setOnItemSelectedListener false
            }
            selectedItemId = item.itemId

            when (item.itemId) {
                R.id.nav_home -> loadFragment(MapFragment())
                R.id.nav_profile -> loadFragment(ProfileFragment())
                R.id.nav_camera -> loadFragment(CameraFragment())
            }
            true
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
