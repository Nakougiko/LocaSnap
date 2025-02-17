package com.lukashugo.locasnap.views

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.lukashugo.locasnap.R
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import androidx.camera.view.PreviewView
import androidx.exifinterface.media.ExifInterface
class CameraActivity : ComponentActivity() {

    private lateinit var imageCapture: ImageCapture
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        previewView = findViewById(R.id.previewView)
        val captureButton: ImageView = findViewById(R.id.capture_button)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (allPermissionsGranted()) {
            startCamera()
            getLastKnownLocation()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        captureButton.setOnClickListener { takePhoto() }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation() {
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = location
                    Log.d(TAG, "Localisation obtenue : ${location.latitude}, ${location.longitude}")
                } else {
                    Log.e(TAG, "Impossible d'obtenir la localisation")
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build()

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Erreur d'initialisation de la caméra", e)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val photoFile = File(externalMediaDirs.firstOrNull(), "photo.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo enregistrée : ${photoFile.absolutePath}")
                    saveLocationData(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Erreur de capture", exception)
                }
            })
    }

    private fun saveLocationData(photoFile: File) {
        currentLocation?.let { location ->
            try {
                val exif = ExifInterface(photoFile.absolutePath)

                // Convertir les coordonnées pour Exif (format DMS - degrés, minutes, secondes)
                val lat = location.latitude
                val lon = location.longitude

                val latRef = if (lat < 0) "S" else "N"
                val lonRef = if (lon < 0) "W" else "E"

                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDMS(lat))
                exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, latRef)
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDMS(lon))
                exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, lonRef)

                exif.saveAttributes()
                Log.d(TAG, "Coordonnées GPS enregistrées dans la photo : ${photoFile.absolutePath}")

            } catch (e: Exception) {
                Log.e(TAG, "Erreur lors de l'enregistrement des métadonnées EXIF", e)
            }
        } ?: Log.e(TAG, "Localisation non disponible")
    }

    private fun convertToDMS(coord: Double): String {
        val absolute = Math.abs(coord)
        val degrees = absolute.toInt()
        val minutes = ((absolute - degrees) * 60).toInt()
        val seconds = ((absolute - degrees - minutes / 60.0) * 3600).toInt()
        return "$degrees/1,$minutes/1,$seconds/1"
    }


    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
}
