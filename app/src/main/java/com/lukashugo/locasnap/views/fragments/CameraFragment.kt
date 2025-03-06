package com.lukashugo.locasnap.views.fragments

import android.content.ContentValues
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.lukashugo.locasnap.R
import androidx.camera.view.PreviewView
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment(R.layout.fragment_camera) {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        previewView = view.findViewById(R.id.camera_preview)
        val captureButton: FloatingActionButton = view.findViewById(R.id.capture_button)

        cameraExecutor = Executors.newSingleThreadExecutor()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        getLocation() // Récupérer la localisation GPS dès l'ouverture

        startCamera()

        captureButton.setOnClickListener {
            takePhoto()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(previewView.display.rotation)
                .build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Erreur d'initialisation de la caméra", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
            .format(System.currentTimeMillis())

        val photoFile = File(requireContext().filesDir, "IMG_$name.jpg")

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d("CameraFragment", "📸 Photo enregistrée temporairement à : ${photoFile.absolutePath}")

                    // Ajouter la localisation en EXIF
                    addLocationToExif(photoFile.absolutePath)

                    // Ajouter la photo modifiée dans la galerie
                    saveToGallery(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraFragment", "❌ Erreur de capture : ${exception.message}", exception)
                }
            })
    }


    private fun getLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    currentLocation = location
                    Log.d("CameraFragment", "🌍 Localisation obtenue : ${location.latitude}, ${location.longitude}")
                }
            }
        } catch (e: SecurityException) {
            Log.e("CameraFragment", "❌ Permission de localisation non accordée", e)
        }
    }

    private fun addLocationToExif(imagePath: String) {
        if (currentLocation == null) {
            Log.w("CameraFragment", "⚠️ Localisation non disponible, EXIF ignoré")
            return
        }

        try {
            val exif = ExifInterface(imagePath)

            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, convertToDMS(currentLocation!!.latitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, if (currentLocation!!.latitude >= 0) "N" else "S")
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE, convertToDMS(currentLocation!!.longitude))
            exif.setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, if (currentLocation!!.longitude >= 0) "E" else "W")

            exif.saveAttributes()
            Log.d("CameraFragment", "✅ Localisation enregistrée dans EXIF : ${currentLocation!!.latitude}, ${currentLocation!!.longitude}")
        } catch (e: IOException) {
            Log.e("CameraFragment", "❌ Erreur lors de l'ajout des métadonnées EXIF", e)
        }
    }

    private fun saveToGallery(photoFile: File) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, photoFile.name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/LocaSnap")
        }

        val uri = requireContext().contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                photoFile.inputStream().copyTo(outputStream)
            }
            Log.d("CameraFragment", "📸 Photo enregistrée avec EXIF dans la galerie : $uri")
        } ?: Log.e("CameraFragment", "❌ Erreur lors de l'ajout de la photo dans la galerie")
    }

    private fun convertToDMS(coord: Double): String {
        val degrees = coord.toInt()
        val minutes = ((coord - degrees) * 60).toInt()
        val seconds = ((coord - degrees - (minutes / 60.0)) * 3600).toInt()

        return "$degrees/1,$minutes/1,$seconds/1"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}
