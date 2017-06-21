package com.apps.prakriti.route;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

// Imports for Fingerprint API
import java.util.ArrayList;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.Toast;
import com.samsung.android.sdk.SsdkUnsupportedException;
import com.samsung.android.sdk.pass.Spass;
import com.samsung.android.sdk.pass.SpassFingerprint;
import com.samsung.android.sdk.pass.SpassInvalidStateException;

public class MapsActivity extends AppCompatActivity implements
        OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener
{
    GoogleMap mMap;
    SupportMapFragment mapFrag;
    LocationRequest mLocationRequest;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    Marker mCurrLocationMarker;
    private static final String TAG = MapsActivity.class.getSimpleName();
    private MapDirectionsAsyncTask mMapTask;

    LatLng myLocationLatLng;
    LatLng SFO = new LatLng(37.621313, -122.378955);
    LatLng SJAirport = new LatLng(37.363947, -121.928938);
    LatLng GGBridge = new LatLng(37.819929, -122.478255);

    // ====================== Set up timer to keep listening to fingerprint ======================//
    private Handler timerHandler = new Handler();
    Runnable timerRunnable = new Runnable() {
        @Override
        public void run()
        {
            Log.d (TAG, "!!!!!!!!!!!!!!!! START IDENTIFY CALLED !!!!!!!!!!!!!!!");
            startIdentify();
            timerHandler.postDelayed(this, 1000);
        }
    };

    // ====================== Fingerprint related variables=======================================//

    private SpassFingerprint mSpassFingerprint;
    private Spass mSpass;
    private Context mContext;
    private ArrayList<Integer> designatedFingers = null;
    private boolean needRetryIdentify = false;
    private boolean onReadyIdentify = false;
    private boolean isFeatureEnabled_fingerprint = false;
    private boolean isFeatureEnabled_index = false;

    // ====================== Set up Broadcast Receiver ==========================================//

    private BroadcastReceiver mPassReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (SpassFingerprint.ACTION_FINGERPRINT_RESET.equals(action)) {
                Toast.makeText(mContext, "all fingerprints are removed", Toast.LENGTH_SHORT).show();
            } else if (SpassFingerprint.ACTION_FINGERPRINT_REMOVED.equals(action)) {
                int fingerIndex = intent.getIntExtra("fingerIndex", 0);
                Toast.makeText(mContext, fingerIndex + " fingerprints is removed", Toast.LENGTH_SHORT).show();
            } else if (SpassFingerprint.ACTION_FINGERPRINT_ADDED.equals(action)) {
                int fingerIndex = intent.getIntExtra("fingerIndex", 0);
                Toast.makeText(mContext, fingerIndex + " fingerprints is added", Toast.LENGTH_SHORT).show();
            }
        }
    };

    private void registerBroadcastReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(SpassFingerprint.ACTION_FINGERPRINT_RESET);
        filter.addAction(SpassFingerprint.ACTION_FINGERPRINT_REMOVED);
        filter.addAction(SpassFingerprint.ACTION_FINGERPRINT_ADDED);
        mContext.registerReceiver(mPassReceiver, filter);
    }

    private void unregisterBroadcastReceiver() {
        try {
            if (mContext != null) {
                mContext.unregisterReceiver(mPassReceiver);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void resetAll() {
        designatedFingers = null;
        needRetryIdentify = false;
        onReadyIdentify = false;
    }

    // ====================== Set up Spass Fingerprint listener Object ===========================//

    private SpassFingerprint.IdentifyListener mIdentifyListener = new SpassFingerprint.IdentifyListener() {
        @Override
        public void onFinished(int eventStatus)
        {
            int FingerprintIndex = 0;
            String FingerprintGuideText = null;
            try {
                FingerprintIndex = mSpassFingerprint.getIdentifiedFingerprintIndex();
            } catch (IllegalStateException ise) {
            }
            if (eventStatus == SpassFingerprint.STATUS_AUTHENTIFICATION_SUCCESS)
            {
                fingerprintAction(FingerprintIndex);
            }
            else if (eventStatus == SpassFingerprint.STATUS_TIMEOUT_FAILED)
            {
                Log.d(TAG, "!!!!!!!!! TIME OUT !!!!!!!!!!!!!!!!");
            }
            else if (eventStatus == SpassFingerprint.STATUS_QUALITY_FAILED)
            {
                needRetryIdentify = true;
                FingerprintGuideText = mSpassFingerprint.getGuideForPoorQuality();
                Toast.makeText(mContext, FingerprintGuideText, Toast.LENGTH_SHORT).show();
            }
            else {
                needRetryIdentify = true;
            }
            if (!needRetryIdentify) {
                resetIdentifyIndex();
            }
        }

        @Override
        public void onReady() {
        }

        @Override
        public void onStarted() {
        }

        @Override
        public void onCompleted()
        {
            onReadyIdentify = false;
            if (needRetryIdentify)
            {
                needRetryIdentify = false;
            }
        }
    };
    // ==================================== Activity functions ================================== //

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            checkLocationPermission();
        }

        mapFrag = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFrag.getMapAsync(this);

        // Fingerprint API onCreate()
        mContext = this;
        mSpass = new Spass();

        try {
            mSpass.initialize(MapsActivity.this);
        } catch (SsdkUnsupportedException e) {
        } catch (UnsupportedOperationException e) {
        }
        isFeatureEnabled_fingerprint = mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT);

        if (isFeatureEnabled_fingerprint)
        {
            mSpassFingerprint = new SpassFingerprint(MapsActivity.this);
        } else {
            return;
        }
        isFeatureEnabled_index = mSpass.isFeatureEnabled(Spass.DEVICE_FINGERPRINT_FINGER_INDEX);

        registerBroadcastReceiver();

        // Enable the timer
        timerHandler.postDelayed(timerRunnable, 0);
    }

    @Override
    public void onPause()
    {
        super.onPause();

        //stop location updates when Activity is no longer active
        if (mGoogleApiClient != null)
        {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        mMap = googleMap;
        // Disable map rotation
        mMap.getUiSettings().setAllGesturesEnabled(false);

        //Initialize Google Play Services
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            {
                buildGoogleApiClient();
                mMap.setMyLocationEnabled(true);
            }
        }
        else
        {
            buildGoogleApiClient();
            mMap.setMyLocationEnabled(true);
        }
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterBroadcastReceiver();
        resetAll();
    }

    protected synchronized void buildGoogleApiClient()
    {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onConnected(Bundle bundle)
    {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {}


    @Override
    public void onLocationChanged(Location location)
    {
        mLastLocation = location;
        if (mCurrLocationMarker != null)
        {
            mCurrLocationMarker.remove();
        }
        myLocationLatLng = new LatLng(location.getLatitude(), location.getLongitude());

        // Intial camera view: Aerial view of cities around myLocation
        CameraPosition cameraPosition = new CameraPosition.Builder()
                .target(new LatLng(location.getLatitude(), location.getLongitude()))
                .zoom(8)                   // Sets the zoom
                .bearing(0)                // Sets the orientation of the camera to east = 90
                .tilt(0)                   // Sets the tilt of the camera to 30 degrees
                .build();                   // Creates a CameraPosition from the builder
        mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        // stop location updates
        if (mGoogleApiClient != null)
        {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }
    // ============================ Permission related  functions =============================== //

    public static final int MY_PERMISSIONS_REQUEST_LOCATION = 99;
    public boolean checkLocationPermission()
    {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
        {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION))
            {
                //Prompt the user once explanation has been shown
                //(just doing it here for now, note that with this code, no explanation is shown)
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            }
            else
            {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_LOCATION);
            }
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {
            case MY_PERMISSIONS_REQUEST_LOCATION:
            {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    // permission was granted, yay! Do the
                    // location-related task you need to do.
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                    {
                        if (mGoogleApiClient == null)
                        {
                            buildGoogleApiClient();
                        }
                        mMap.setMyLocationEnabled(true);
                    }
                }
                else
                {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(this, "permission denied", Toast.LENGTH_LONG).show();
                }
                return;
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult)
    {
    }

    // ============================ Fingerprint functions ======================================= //
    public void startIdentify()
    {
        if (onReadyIdentify == false)
        {
            try {
                onReadyIdentify = true;
                if (mSpassFingerprint != null) {
                    setIdentifyIndex();
                    mSpassFingerprint.startIdentify(mIdentifyListener);
                }
            } catch (SpassInvalidStateException ise) {
                onReadyIdentify = false;
                resetIdentifyIndex();
            } catch (IllegalStateException e) {
                onReadyIdentify = false;
                resetIdentifyIndex();
            }
        }
    }

    private void setIdentifyIndex()
    {
        if (isFeatureEnabled_index) {
            if (mSpassFingerprint != null && designatedFingers != null)
            {
                mSpassFingerprint.setIntendedFingerprintIndex(designatedFingers);
            }
        }
    }

    private void resetIdentifyIndex()
    {
        designatedFingers = null;
    }

    // Program each registered fingerprint with chosen action
    public void fingerprintAction(int fingerprintIndex)
    {
        if (fingerprintIndex == 1)
        {
            Log.d(TAG, " !!!!!!!!!!!!!!!!!!!!! finger print index = 1 !!!!!!!!!!!!!!!!!!!");
            routeToDestination(SFO, "SFO Airport");
        }
        else if (fingerprintIndex == 2)
        {
            Log.d(TAG, " !!!!!!!!!!!!!!!!!!!!! finger print index = 2 !!!!!!!!!!!!!!!!!!!");
            routeToDestination(SJAirport, "San Jose Airport");
        }

        else if (fingerprintIndex == 3)
        {
            Log.d(TAG, " !!!!!!!!!!!!!!!!!!!!! finger print index = 3 !!!!!!!!!!!!!!!!!!!");
            routeToDestination(GGBridge, "Golden Gate Bridge");
        }
    }

    public void routeToDestination(LatLng destination, String destString)
    {
        mMap.clear();
        LatLngBounds.Builder builder = new LatLngBounds.Builder();
        builder.include(myLocationLatLng);
        builder.include(destination);
        LatLngBounds bounds = builder.build();
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        int padding = (int) (width * 0.10); // offset from edges of the map 10% of screen
        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding));
        MarkerOptions marker = new MarkerOptions();
        marker.position(destination).title(destString);
        mMap.addMarker(marker);
        mMapTask = new MapDirectionsAsyncTask(mMap, myLocationLatLng, destination, MapDirectionsAsyncTask.MODE_DRIVING);
        mMapTask.execute();
    }
}
