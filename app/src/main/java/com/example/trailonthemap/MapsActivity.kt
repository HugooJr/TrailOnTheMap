package com.example.trailonthemap

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.trailonthemap.Constants.INTERVAL_TIME

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.example.trailonthemap.databinding.ActivityMapsBinding
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object{
        val REQUIRED_PERMISSION_GPS = arrayOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_NETWORK_STATE
        )
    }

    private var isGpsEnabled = false
    private val PERMISSION_ID = 42
    //Variable to manage GPS with google play services
    //FusedLocation: fusions the GPS data to an object
    private lateinit var fusedLocation: FusedLocationProviderClient

    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var distance: Double = 0.0
    private var velocity: Double = 0.0
    private var contador: Int = 0
    private var total = 0.0
    private val myRoutes = mutableListOf<LatLng>()

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        binding.fabPosition.setOnClickListener {
            manageLocation()
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        val laPazBounds = LatLngBounds(LatLng(-16.5224, -68.1537),
            LatLng(-16.4855, -68.1194)
        )
        //mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(-16.5103, -68.1365), 13f))
        lifecycleScope.launch {
            delay(2_500) //Thousands separation with (_)
            //From the delimited area, the center of the imaginary rectangle can be accessed
            //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(laPazBounds.center, 13f))
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(laPazBounds,32))
        }
        mMap.setLatLngBoundsForCameraTarget(laPazBounds)

        mMap.uiSettings.apply {
            isZoomControlsEnabled = true //OrientationButtons of zoom in and zoom out
            isCompassEnabled = false //Orientation compass
            isMapToolbarEnabled = true // Enables an option to see a route, for a marker
            isRotateGesturesEnabled = false //Disables the rotation option in the map
            //isZoomGesturesEnabled = false //Disables the zoom option in the map
            isScrollGesturesEnabled = true
            isTiltGesturesEnabled = true //Disables the camera rotation option
        }

        val marker = mMap.addMarker(MarkerOptions()
            .position(LatLng(latitude,longitude))
        )
        marker?.run {
            setIcon(BitmapDescriptorFactory.fromResource(R.drawable.custom_icon))
            setupPolyline()
        }
    }

    private fun setupPolyline() {
        val actualPosition = LatLng(latitude,longitude)
        if(actualPosition != LatLng(0.0,0.0)){
            myRoutes.add(actualPosition)
        }
        val polyline = mMap.addPolyline(
            PolylineOptions()
            .color(R.color.teal_700)
            .width(10f) //Line width
            .clickable(true) //Click options on the line
            .geodesic(true) //Curvature respect to earth ratio
        )
        //polyline.points = myRoutes

        val routes = mutableListOf<LatLng>()
        for (point in myRoutes){
            routes.add(point)
            polyline.points = routes
        }

        //Set information or description in the line
        polyline.tag = "Tracing route"
        //Style configuration of the line unions
        //polyline.jointType = JointType.ROUND
        //polyline.width = 100f
        //How to configure the way patter of the line
        //1. Continue line
        //2. Pointed line
        //3. Segmented line
        //polyline.pattern = listOf(Dot(), Gap(32f), Dash(32f))
    }

    @SuppressLint("MissingPermission")
    private fun manageLocation() {
        if (hasGPSEnabled()) {
            if (allPermissionsGrantedGPS()) {
                //This kind of object can only be treated if user allowed permissions
                fusedLocation = LocationServices.getFusedLocationProviderClient(this)
                //Configuring an event which listen when GPS sensor captures data correctly
                fusedLocation.lastLocation.addOnSuccessListener {
                        location -> requestNewLocationData()
                }
            } else
                requestPermissionLocation()
        } else
            goToEnableGPS()
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        //Setting location request characteristics
        //TODO check the update to 21 version with the create() method
        //create() was deprecated for the actual versions of the libraries
        /*val myLocationRequest = LocationRequest.create().apply {
            priority = Priority.PRIORITY_HIGH_ACCURACY
            interval = 0
            fastestInterval = 0
            numUpdates = 1
        }*/
        val myLocationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            INTERVAL_TIME
        ).setMaxUpdates(50)
            .build() //build() -> builds the location
        //This is the check for permissions (Plan A)
        /*
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
         */
        //Looper() is something like a police officer who verifies the queue of a work and follows it
        fusedLocation.requestLocationUpdates(myLocationRequest, myLocationCallback, Looper.myLooper())
    }

    private val myLocationCallback = object: LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val myLastLocation: Location? = locationResult.lastLocation
            if (myLastLocation != null) {
                val lastLatitude = myLastLocation.latitude
                val lastLongitude = myLastLocation.longitude
                //if (contador > 0){
                //    distance = calculateDistance(lastLatitude, lastLongitude)
                //    velocity = calculateVelocity()
                //}
                //total += distance
                //binding.apply {
                binding.txtInfo.text = "Lat: $lastLatitude\r\nLong: $lastLongitude"
                //    txtDistance.text = "$distance mts."
                //    txtInfo.text = "Total distance: $total"
                //    txtVelocity.text = "$velocity km/h."
                //}
                latitude = myLastLocation.latitude
                longitude = myLastLocation.longitude
                onMapReady(mMap)
                //contador++
                //getAddressName()
            }
        }
    }

    private fun hasGPSEnabled(): Boolean {
        //LocationManager: Manage everything referred to location, from the ambit
        //of usage of Android's libraries for location
        val locationManager: LocationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun goToEnableGPS() {
        val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }

    private fun allPermissionsGrantedGPS(): Boolean {
        return REQUIRED_PERMISSION_GPS.all {
            ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionLocation() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSION_GPS,PERMISSION_ID)
    }
}