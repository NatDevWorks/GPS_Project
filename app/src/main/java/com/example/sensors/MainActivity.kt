package com.example.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.TextView

class MainActivity : AppCompatActivity() {
    private lateinit var locationManager: LocationManager
    private lateinit var statusText: TextView
    private lateinit var locationButton: MaterialButton
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var altitudeText: TextView
    private lateinit var speedText: TextView
    private lateinit var providerText: TextView
    private lateinit var timeText: TextView

    private val timeFormatter = SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault())

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (fineGranted || coarseGranted) {
            startLocationUpdates()
        } else {
            statusText.setText(R.string.location_permission_denied)
        }
    }

    private val locationListener = LocationListener { location ->
        showLocation(location)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        statusText = findViewById(R.id.statusText)
        locationButton = findViewById(R.id.locationButton)
        latitudeText = findViewById(R.id.latitudeText)
        longitudeText = findViewById(R.id.longitudeText)
        accuracyText = findViewById(R.id.accuracyText)
        altitudeText = findViewById(R.id.altitudeText)
        speedText = findViewById(R.id.speedText)
        providerText = findViewById(R.id.providerText)
        timeText = findViewById(R.id.timeText)

        locationButton.setOnClickListener {
            requestLocationOrStart()
        }
    }

    override fun onStop() {
        super.onStop()
        locationManager.removeUpdates(locationListener)
    }

    private fun requestLocationOrStart() {
        if (hasAnyLocationPermission()) {
            startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startLocationUpdates() {
        if (!isProviderAvailable(LocationManager.GPS_PROVIDER) &&
            !isProviderAvailable(LocationManager.NETWORK_PROVIDER)
        ) {
            statusText.setText(R.string.location_services_off)
            return
        }

        statusText.setText(R.string.location_updates_started)
        locationButton.setText(R.string.refresh_location)

        requestUpdatesFrom(LocationManager.GPS_PROVIDER)
        requestUpdatesFrom(LocationManager.NETWORK_PROVIDER)
        showBestLastKnownLocation()
    }

    private fun requestUpdatesFrom(provider: String) {
        if (!isProviderAvailable(provider) || !hasAnyLocationPermission()) return

        locationManager.requestLocationUpdates(
            provider,
            MIN_UPDATE_TIME_MS,
            MIN_UPDATE_DISTANCE_METERS,
            locationListener
        )
    }

    private fun showBestLastKnownLocation() {
        if (!hasAnyLocationPermission()) return

        val lastLocation = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).mapNotNull { provider ->
            if (isProviderAvailable(provider)) locationManager.getLastKnownLocation(provider) else null
        }.maxByOrNull { it.time }

        lastLocation?.let(::showLocation)
    }

    private fun showLocation(location: Location) {
        latitudeText.text = "Latitude: ${"%.6f".format(location.latitude)}"
        longitudeText.text = "Longitude: ${"%.6f".format(location.longitude)}"
        accuracyText.text = if (location.hasAccuracy()) {
            "Accuracy: +/- ${"%.1f".format(location.accuracy)} meters"
        } else {
            "Accuracy: unavailable"
        }
        altitudeText.text = if (location.hasAltitude()) {
            "Altitude: ${"%.1f".format(location.altitude)} meters"
        } else {
            "Altitude: unavailable"
        }
        speedText.text = if (location.hasSpeed()) {
            "Speed: ${"%.1f".format(location.speed)} m/s"
        } else {
            "Speed: unavailable"
        }
        providerText.text = "Provider: ${location.provider ?: "unknown"}"
        timeText.text = "Updated: ${timeFormatter.format(Date(location.time))}"
    }

    private fun hasAnyLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun isProviderAvailable(provider: String): Boolean {
        return locationManager.allProviders.contains(provider) &&
            locationManager.isProviderEnabled(provider)
    }

    private companion object {
        const val MIN_UPDATE_TIME_MS = 2_000L
        const val MIN_UPDATE_DISTANCE_METERS = 1f
    }
}
