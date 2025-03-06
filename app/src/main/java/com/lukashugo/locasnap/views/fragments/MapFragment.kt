package com.lukashugo.locasnap.views.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color.alpha
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.lukashugo.locasnap.R
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.Style
import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
import com.mapbox.maps.plugin.annotation.annotations
import androidx.exifinterface.media.ExifInterface
import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
import java.io.File
import coil.load
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import kotlin.math.roundToInt

class MapFragment : Fragment(R.layout.fragment_map) {

    private lateinit var mapView: MapView
    private lateinit var pointAnnotationManager: PointAnnotationManager

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView = view.findViewById(R.id.mapView)

        Log.d("MapFragment", "✅ MapView trouvé, chargement de Mapbox...")

        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {
            Log.d("MapFragment", "✅ Mapbox est chargé avec succès !")

            // Initialisation des marqueurs
            val annotationPlugin = mapView.annotations
            pointAnnotationManager = mapView.annotations.createPointAnnotationManager()

            checkPermissions()

            // Charger les marqueurs
            loadPhotoMarkers()
        }
    }

    private fun loadPhotoMarkers() {
        val photos = getPhotosFromGallery()

        if (photos.isEmpty()) {
            Log.e("MapFragment", "❌ Aucune photo trouvée dans DCIM/LocaSnap")
            return
        }

        Log.d("MapFragment", "📸 ${photos.size} photos trouvées.")

        var firstLocation: Point? = null
        val groupedPhotos = mutableMapOf<Point, MutableList<String>>()

        for (photo in photos) {
            val location = getPhotoLocation(photo)

            if (location == null) {
                Log.w("MapFragment", "⚠️ Pas de données GPS pour cette photo")
            } else {
                val position = Point.fromLngLat(location.second, location.first)

                if (firstLocation == null) {
                    firstLocation = position
                }

                groupedPhotos.getOrPut(position) { mutableListOf() }.add(photo)
            }
        }

        for ((position, photoList) in groupedPhotos) {
            addPhotoMarker(photoList, position)
        }

        if (firstLocation != null) {
            mapView.getMapboxMap().setCamera(
                CameraOptions.Builder()
                    .center(firstLocation)
                    .zoom(12.0)
                    .build()
            )
            Log.d("MapFragment", "🎯 Zoom sur : $firstLocation")
        }
    }

    private fun getPhotosFromGallery(): List<String> {
        val photoPaths = mutableListOf<String>()

        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = requireContext().contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, null, null, "${MediaStore.Images.Media.DATE_ADDED} DESC"
        )

        cursor?.use {
            val columnIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
            while (it.moveToNext()) {
                val path = it.getString(columnIndex)
                val file = File(path)

                if (file.exists()) {
                    Log.d("MapFragment", "📸 Photo trouvée : $path")
                    photoPaths.add(path)
                } else {
                    Log.e("MapFragment", "❌ Fichier introuvable : $path")
                }
            }
        }

        return photoPaths
    }

    private fun getPhotoLocation(photoPath: String): Pair<Double, Double>? {
        try {
            val exif = ExifInterface(File(photoPath))

            val lat = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)
            val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF)
            val lon = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)
            val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF)

            Log.d("MapFragment", "📸 EXIF GPS : lat=$lat, ref=$latRef | lon=$lon, ref=$lonRef")

            if (lat != null && latRef != null && lon != null && lonRef != null) {
                val latitude = convertDMSToDecimal(lat, latRef)
                val longitude = convertDMSToDecimal(lon, lonRef)
                Log.d("MapFragment", "✅ GPS converti : $latitude, $longitude")
                return Pair(latitude, longitude)
            }
        } catch (e: Exception) {
            Log.e("MapFragment", "❌ Erreur lors de la lecture des EXIF", e)
        }
        return null
    }

    private fun convertDMSToDecimal(dms: String, ref: String): Double {
        val parts = dms.split(",")

        val degrees = parts[0].split("/")[0].toDouble()
        val minutes = parts[1].split("/")[0].toDouble() / 60.0
        val seconds = parts[2].split("/")[0].toDouble() / 3600.0

        var decimal = degrees + minutes + seconds

        if (ref == "S" || ref == "W") {
            decimal *= -1
        }

        return decimal
    }

    private fun addPhotoMarker(photoList: List<String>, position: Point) {
        if (photoList.isEmpty()) return

        val viewAnnotationManager = mapView.viewAnnotationManager

        val view = LayoutInflater.from(requireContext()).inflate(R.layout.marker_photo_card, null)

        val photoPreview = view.findViewById<ImageView>(R.id.photo_preview)
        val photoStack = view.findViewById<ImageView>(R.id.photo_stack)
        val photoCount = view.findViewById<TextView>(R.id.photo_count)

        // Vérifie si la première photo existe avant de la charger
        val firstPhotoFile = File(photoList[0])
        if (firstPhotoFile.exists()) {
            photoPreview.load(firstPhotoFile) {
                crossfade(true)
                placeholder(android.R.drawable.ic_menu_gallery)
                error(android.R.drawable.stat_notify_error)
            }
        } else {
            Log.e("MapFragment", "❌ Première image introuvable: ${photoList[0]}")
        }

        // Vérifie si une deuxième photo existe avant de la charger
        if (photoList.size > 1) {
            val secondPhotoFile = File(photoList[1])
            if (secondPhotoFile.exists()) {
                photoStack.visibility = View.VISIBLE
                photoStack.load(secondPhotoFile) {
                    crossfade(true)
                    alpha(0.7f.roundToInt())
                }
            } else {
                Log.e("MapFragment", "⚠️ Deuxième image introuvable: ${photoList[1]}")
            }

            photoCount.visibility = View.VISIBLE
            photoCount.text = "+${photoList.size - 1}"
        } else {
            photoStack.visibility = View.GONE
            photoCount.visibility = View.GONE
        }

        val viewAnnotation = viewAnnotationManager.addViewAnnotation(
            resId = R.layout.marker_photo_card,
            options = viewAnnotationOptions {
                geometry(position)
                allowOverlap(true)
            }
        )
        viewAnnotation.isClickable = true

        // Ouvre le bottom sheet au clic
        view.setOnClickListener {
            Log.d("MapFragment", "📸 Clic sur un marqueur !")
            val bottomSheet = PhotoBottomSheetFragment(photoList)
            bottomSheet.show(parentFragmentManager, "PhotoBottomSheet")
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onStop()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 100)
        } else {
            loadPhotoMarkers()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadPhotoMarkers()
        }
    }
}
