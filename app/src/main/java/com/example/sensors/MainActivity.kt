package com.example.sensors

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var locationManager: LocationManager
    private lateinit var statusText: TextView
    private lateinit var locationButton: MaterialButton
    private lateinit var navigateButton: MaterialButton
    private lateinit var detailsToggleButton: MaterialButton
    private lateinit var setSafeZoneButton: MaterialButton
    private lateinit var destinationEditText: EditText
    private lateinit var detailsPanel: LinearLayout
    private lateinit var geofenceRadiusEditText: EditText
    private lateinit var latitudeText: TextView
    private lateinit var longitudeText: TextView
    private lateinit var accuracyText: TextView
    private lateinit var altitudeText: TextView
    private lateinit var speedText: TextView
    private lateinit var providerText: TextView
    private lateinit var timeText: TextView
    private lateinit var destinationStatusText: TextView
    private lateinit var geofenceStatusText: TextView
    private lateinit var mapWebView: WebView

    private var latestLocation: Location? = null
    private var safeZoneLocation: Location? = null
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
        navigateButton = findViewById(R.id.navigateButton)
        detailsToggleButton = findViewById(R.id.detailsToggleButton)
        detailsPanel = findViewById(R.id.detailsPanel)
        setSafeZoneButton = findViewById(R.id.setSafeZoneButton)
        destinationEditText = findViewById(R.id.destinationEditText)
        geofenceRadiusEditText = findViewById(R.id.geofenceRadiusEditText)
        latitudeText = findViewById(R.id.latitudeText)
        longitudeText = findViewById(R.id.longitudeText)
        accuracyText = findViewById(R.id.accuracyText)
        altitudeText = findViewById(R.id.altitudeText)
        speedText = findViewById(R.id.speedText)
        providerText = findViewById(R.id.providerText)
        timeText = findViewById(R.id.timeText)
        destinationStatusText = findViewById(R.id.destinationStatusText)
        geofenceStatusText = findViewById(R.id.geofenceStatusText)
        mapWebView = findViewById(R.id.mapWebView)

        mapWebView.settings.javaScriptEnabled = true
        mapWebView.settings.domStorageEnabled = true
        mapWebView.loadDataWithBaseURL(null, emptyMapHtml(), "text/html", "UTF-8", null)

        locationButton.setOnClickListener {
            requestLocationOrStart()
        }

        navigateButton.setOnClickListener {
            openNavigationToDestination()
        }

        detailsToggleButton.setOnClickListener {
            toggleDetailsPanel()
        }

        setSafeZoneButton.setOnClickListener {
            setSafeZoneFromCurrentLocation()
        }
    }

    override fun onStop() {
        super.onStop()
        locationManager.removeUpdates(locationListener)
    }

    private fun toggleDetailsPanel() {
        val detailsAreVisible = detailsPanel.visibility == View.VISIBLE
        detailsPanel.visibility = if (detailsAreVisible) View.GONE else View.VISIBLE
        detailsToggleButton.setText(
            if (detailsAreVisible) R.string.show_details else R.string.hide_details
        )
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
        latestLocation = location
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

        updateGeofenceStatus(location)
        updateMap(location)
    }

    private fun openNavigationToDestination() {
        val destination = destinationEditText.text.toString().trim()
        if (destination.isEmpty()) {
            Toast.makeText(this, R.string.destination_required, Toast.LENGTH_SHORT).show()
            return
        }

        val currentLocation = latestLocation
        if (currentLocation == null) {
            Toast.makeText(this, R.string.location_required_for_navigation, Toast.LENGTH_LONG).show()
            requestLocationOrStart()
            return
        }

        val encodedDestination = URLEncoder.encode(destination, "UTF-8")
        val origin = "${currentLocation.latitude},${currentLocation.longitude}"
        destinationStatusText.text = "Opening best route from $origin to $destination."
        val directionsUri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&origin=$origin&destination=$encodedDestination&travelmode=driving"
        )

        startActivity(Intent(Intent.ACTION_VIEW, directionsUri))
    }

    private fun setSafeZoneFromCurrentLocation() {
        val currentLocation = latestLocation
        if (currentLocation == null) {
            Toast.makeText(this, R.string.location_required_for_geofence, Toast.LENGTH_LONG).show()
            requestLocationOrStart()
            return
        }

        safeZoneLocation = Location(currentLocation)
        updateGeofenceStatus(currentLocation)
        updateMap(currentLocation)
    }

    private fun updateGeofenceStatus(currentLocation: Location) {
        val center = safeZoneLocation
        if (center == null) {
            geofenceStatusText.setText(R.string.geofence_not_set)
            return
        }

        val radius = geofenceRadiusMeters()
        val distance = currentLocation.distanceTo(center)
        val status = when {
            distance <= radius -> "Inside safe zone"
            distance <= radius + 25f -> "Approaching boundary"
            else -> "Outside safe zone"
        }
        geofenceStatusText.text = "$status: ${distance.roundToInt()} meters from center, radius ${radius.roundToInt()} meters."
    }

    private fun updateMap(location: Location) {
        val safeZone = safeZoneLocation
        val safeLat = safeZone?.latitude
        val safeLon = safeZone?.longitude
        val radius = if (safeZone == null) 0f else geofenceRadiusMeters()
        mapWebView.loadDataWithBaseURL(
            "https://www.openstreetmap.org/",
            mapHtml(location.latitude, location.longitude, safeLat, safeLon, radius),
            "text/html",
            "UTF-8",
            null
        )
    }

    private fun geofenceRadiusMeters(): Float {
        return geofenceRadiusEditText.text.toString().toFloatOrNull()?.coerceAtLeast(10f) ?: 100f
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

    private fun emptyMapHtml(): String {
        return """
            <!doctype html>
            <html><body style="margin:0;font-family:sans-serif;background:#eef2f7;color:#1f2937;display:flex;align-items:center;justify-content:center;height:100vh;text-align:center;padding:24px;box-sizing:border-box;">
            Start location updates to load your map and nearby places.
            </body></html>
        """.trimIndent()
    }

    private fun mapHtml(lat: Double, lon: Double, safeLat: Double?, safeLon: Double?, radius: Float): String {
        val safeZoneScript = if (safeLat != null && safeLon != null) {
            "L.circle([$safeLat, $safeLon], { radius: $radius, color: '#16a34a', fillColor: '#86efac', fillOpacity: 0.22 }).addTo(map).bindPopup('Safe zone');"
        } else {
            ""
        }

        return """
            <!doctype html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
                <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                <style>
                    html, body, #map { height: 100%; margin: 0; }
                    .notice { font: 13px sans-serif; }
                </style>
            </head>
            <body>
                <div id="map"></div>
                <script>
                    const lat = $lat;
                    const lon = $lon;
                    const map = L.map('map').setView([lat, lon], 16);
                    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                        maxZoom: 19,
                        attribution: '&copy; OpenStreetMap contributors'
                    }).addTo(map);

                    L.marker([lat, lon]).addTo(map).bindPopup('You are here').openPopup();
                    L.circle([lat, lon], { radius: 60, color: '#2563eb', fillColor: '#93c5fd', fillOpacity: 0.18 }).addTo(map);
                    $safeZoneScript

                    const query = `[out:json][timeout:25];(
                      node(around:900,${lat},${lon})["tourism"];
                      node(around:900,${lat},${lon})["historic"];
                      node(around:900,${lat},${lon})["leisure"~"park|garden"];
                      node(around:900,${lat},${lon})["amenity"~"restaurant|cafe|hospital|pharmacy|library|police|fire_station"];
                    );out 25;`;

                    fetch('https://overpass-api.de/api/interpreter', {
                        method: 'POST',
                        body: query
                    })
                    .then(response => response.json())
                    .then(data => {
                        data.elements.forEach(place => {
                            const name = place.tags.name || place.tags.amenity || place.tags.tourism || place.tags.historic || place.tags.leisure || 'Notable place';
                            const type = place.tags.amenity || place.tags.tourism || place.tags.historic || place.tags.leisure || 'nearby';
                            L.circleMarker([place.lat, place.lon], {
                                radius: 7,
                                color: '#dc2626',
                                fillColor: '#fca5a5',
                                fillOpacity: 0.8
                            }).addTo(map).bindPopup(`<b>${'$'}{name}</b><br><span class="notice">${'$'}{type}</span>`);
                        });
                    })
                    .catch(() => {
                        L.popup()
                            .setLatLng([lat, lon])
                            .setContent('Nearby places need internet access to load.')
                            .openOn(map);
                    });
                </script>
            </body>
            </html>
        """.trimIndent()
    }

    private companion object {
        const val MIN_UPDATE_TIME_MS = 2_000L
        const val MIN_UPDATE_DISTANCE_METERS = 1f
    }
}



