package com.lukashugo.locasnap

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import com.google.android.material.button.MaterialButton
import com.lukashugo.locasnap.views.CameraActivity
import com.lukashugo.locasnap.views.MapActivity

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val openCameraButton: MaterialButton = findViewById(R.id.open_camera_button)
        openCameraButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }

        val openMapButton: MaterialButton = findViewById(R.id.open_map_button)
        openMapButton.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }
    }
}
