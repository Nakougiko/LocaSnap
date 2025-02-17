package com.lukashugo.locasnap.views

import android.os.Bundle
import androidx.activity.ComponentActivity
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.lukashugo.locasnap.R

class MapActivity : ComponentActivity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Récupération de la MapView depuis le XML
        mapView = findViewById(R.id.mapView)

        // Chargement du style de la carte
        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS)

        // Définition de la position initiale
        mapView.getMapboxMap().setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(-98.0, 39.5)) // Coordonnées (longitude, latitude)
                .zoom(3.0) // Niveau de zoom initial
                .build()
        )
    }
}
