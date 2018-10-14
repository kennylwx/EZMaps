package com.moonstone.ezmaps_app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.LinearSnapHelper;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SnapHelper;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionDeniedResponse;
import com.karumi.dexter.listener.PermissionGrantedResponse;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.single.PermissionListener;


public class EZDirectionActivity extends AppCompatActivity implements RetrieveFeed.AsyncResponse, View.OnClickListener {

    /* FusedLocationProviderAPI attributes (https://developer.android.com/training/location/receive-location-updates#java)*/
    private String mLastUpdateTime; // Last Update Time
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000; // 5s
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = 5000; // location fastest updates interval (5s)
    private static final int REQUEST_CHECK_SETTINGS = 100;

    private static final double BUFFER_ZONE = 0.0005;

    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;
    private Location mCurrentLocation; // Location Object

    private Boolean mRequestingLocationUpdates; // requesting location flag

    /* Main Activity attributes */
    private Toolbar toolbar;
    private ActionBar actionbar;
    private ImageButton leftButton;
    private ImageButton rightButton;
    private Button nextStopButton;
    private Button refreshButton;

    /* Recycler View Attributes */
    private ArrayList<String> imageUrlsList;
    private ArrayList<String> textDirectionsList;
    private ArrayList<Coordinate> coordinatesList;
    private Coordinate nextStopCoordinate;
    private int nextStopCounter = 0;

    private View recyclerView;
    private EZCardRecyclerViewAdapter adapter;
    private LinearLayoutManager layoutManager;
    private TextView notFoundText;
    private TextView notFoundSubtext;
    private ImageView notFoundImg;
    private ProgressBar progressBar;
    private int progressStatus = 0;
    private boolean isLocationNotFound = false;
    private boolean isCardLoaded = false;

    /* Utilities */
    private int REQUEST_CODE = 1;
    private int counter = 0;
    private int numView;
    private HashMap<String, Object> tab2_to_ezdirection; // Object received from Tab 2
    private boolean isCurrentDestinationFavourited;
    private String currentDestination; // Name of the current destination

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d("EZD", "STARTING EZD---------");

        /* Set up the activity's layout */
        setContentView(R.layout.activity_ezdirection);
        recyclerView = findViewById(R.id.recyclerView);
        toolbar = findViewById(R.id.my_toolbar);
        leftButton = findViewById(R.id.leftButton);
        rightButton = findViewById(R.id.rightButton);
        progressBar = findViewById(R.id.progressBar);
        nextStopButton = findViewById(R.id.nextStopButton);
        refreshButton = findViewById(R.id.refreshButton);

        notFoundText = findViewById(R.id.notFoundText);
        notFoundSubtext = findViewById(R.id.notFoundSubtext);
        notFoundImg = findViewById(R.id.notFoundImg);

        /* Listen on for Left and Right clicks */
        leftButton.setOnClickListener(this);
        rightButton.setOnClickListener(this);
        nextStopButton.setOnClickListener(this);
        refreshButton.setOnClickListener(this);

        /* Set Up Action Bar */
        setSupportActionBar(toolbar);
        actionbar = getSupportActionBar();
        actionbar.setTitle("EZMap");
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        /* Retrieve Object from Tab 2 */
        Intent intent = getIntent();
        tab2_to_ezdirection = (HashMap<String, Object>) intent.getSerializableExtra("tab2_to_ezdirection");
        isCurrentDestinationFavourited = (boolean) tab2_to_ezdirection.get("isCurrentDestinationFavourited");
        currentDestination = tab2_to_ezdirection.get("currentDestination").toString();
        Log.d("EZDIRECTION", "CURRENT DESTINATION RECEIVED FROM TAB2: " + currentDestination);

        /* Attach Snapper to Recycler View */
        SnapHelper helper = new LinearSnapHelper();
        helper.attachToRecyclerView((RecyclerView) recyclerView);

        /* Getting Current Location's GPS Coordinates (FusedLocationProviderAPI) */
        initFusedLocationProvider(); // Initialise Fused Location Provider
        restoreValuesFromBundle(savedInstanceState); // Restore the values from saved instance state
        startLocationUpdatesPermission(); // Initiate Request permission to access GPS

    }




    /*********                         Methods Handling Geolocation                   *************/
    /* -------------------------------------------------------------------------------------------*/

    private void initFusedLocationProvider() {
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        mSettingsClient = LocationServices.getSettingsClient(this);

        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);

                // Getting Current Location and Update Time
                checkCurrentGPSStatus(locationResult.getLastLocation(),Calendar.getInstance().getTime().toString());

                logUpdateLocation("EZDIRECTION/initFLP");
            }
        };

        mRequestingLocationUpdates = false;
        mLocationRequest = LocationRequest.create();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private void checkCurrentGPSStatus(Location currentLocation, String lastUpdateTime){

        // Check to see if they have changed and isn't null
        if(currentLocation == null || lastUpdateTime == null){
            Log.d("EZDIRECTION/StatusCheck", "Current Location and Last Update time is null");
            return;
        }

        mLastUpdateTime = lastUpdateTime;
        mCurrentLocation = currentLocation;


        // If the cards are not loaded, and object has not been returned (regardless of validity)
        if(!isCardLoaded && !isLocationNotFound){
            executeURL();
            Log.d("EZDIRECTION/StatusCheck", "URL Executed");
        }

        if(isCardLoaded){
            checkIfArrivedAtNextStop(mCurrentLocation);
        }

    }

    private boolean checkIfArrivedAtNextStop(Location location){
        Log.d("EZD", "**********************************");
        if(nextStopCoordinate == null){
            nextStopCoordinate = coordinatesList.get(nextStopCounter);
            Log.d("EZD", "Next Stop is Null");
            Log.d("EZD", "Next Stop Coordinate: " + nextStopCounter);
            Log.d("EZD", "Next Stop Counter: " + nextStopCounter);
            return false;
        }

        double currentLat = location.getLatitude();
        double currentLng = location.getLongitude();

        for(Coordinate coord : coordinatesList){
            if(((currentLat <= (coord.getLat() + BUFFER_ZONE)) && (currentLat >= coord.getLat() - BUFFER_ZONE))
                    && ((currentLng <= (coord.getLng() + BUFFER_ZONE)) && (currentLng >= coord.getLng() - BUFFER_ZONE))){

                nextStopCounter = coordinatesList.indexOf(coord) + 1;
                if(nextStopCounter < coordinatesList.size()){
                    nextStopCoordinate = coordinatesList.get(nextStopCounter);
                    Log.d("EZD", "Current Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude());
                    Log.d("EZD", "Next Stop is not null");
                    Log.d("EZD", "Next Stop Counter: " + nextStopCounter);
                    Log.d("EZD", "Next Stop Coordinate: " + nextStopCoordinate);

                }else{
                    Toast.makeText(getApplicationContext(), "You have arrived at your destination!", Toast.LENGTH_SHORT).show();

                }

                return true;
            }

        }

        return false;
    }

    /* Restoring values from saved instance state */
    private void restoreValuesFromBundle(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey("is_requesting_updates")) {
                mRequestingLocationUpdates = savedInstanceState.getBoolean("is_requesting_updates");
            }

            if (savedInstanceState.containsKey("last_known_location")) {
                mCurrentLocation = savedInstanceState.getParcelable("last_known_location");
            }

            if (savedInstanceState.containsKey("last_updated_on")) {
                mLastUpdateTime = savedInstanceState.getString("last_updated_on");
            }
        }

        logUpdateLocation("EZDIRECTION/restore");
    }


    /* Update the location data */
    private void logUpdateLocation(String tag) {
        if (mCurrentLocation != null) {
            Log.d(tag, "Current Latitude: " + mCurrentLocation.getLatitude());
            Log.d(tag, "Current Longitude: " + mCurrentLocation.getLongitude());
            Log.d(tag, "Last Update Time: " + mLastUpdateTime);

        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_requesting_updates", mRequestingLocationUpdates);
        outState.putParcelable("last_known_location", mCurrentLocation);
        outState.putString("last_updated_on", mLastUpdateTime);

    }

    /**
     * Starting location updates
     * Check whether location settings are satisfied and then
     * location updates will be requested
     */
    private void startLocationUpdates() {
        mSettingsClient
                .checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
                    @SuppressLint("MissingPermission")
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i("EZDIRECTION/LocUpSuccess", "All location settings are satisfied.");

                        Toast.makeText(getApplicationContext(), "Started location updates!", Toast.LENGTH_SHORT).show();

                        //noinspection MissingPermission
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                                mLocationCallback, Looper.myLooper());

                        Log.d("EZDIRECTION/LocUpSuccess", "Successfully started Location Update");

                    }
                })
                .addOnFailureListener(this, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i("EZDIRECTION/LocUpFailure", "Location settings are not satisfied. Attempting to upgrade " +
                                        "location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(EZDirectionActivity.this, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i("EZDIRECTION/LocUpFailure", "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be " +
                                        "fixed here. Fix in Settings.";
                                Log.e("EZDIRECTION/LocUpFailure", errorMessage);
                                Toast.makeText(EZDirectionActivity.this, errorMessage, Toast.LENGTH_LONG).show();
                        }

                    }
                });
    }

    /* Get Permission to Start Location Updates */
    public void startLocationUpdatesPermission() {
        // Requesting ACCESS_FINE_LOCATION using Dexter library
        Dexter.withActivity(this)
                .withPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                .withListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted(PermissionGrantedResponse response) {
                        mRequestingLocationUpdates = true;
                        startLocationUpdates();
                    }

                    @Override
                    public void onPermissionDenied(PermissionDeniedResponse response) {
                        if (response.isPermanentlyDenied()) {
                            // open device settings when the permission is
                            // denied permanently
                            openSettings();
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(PermissionRequest permission, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    public void stopLocationUpdates() {
        mRequestingLocationUpdates = false;
        mFusedLocationClient
                .removeLocationUpdates(mLocationCallback)
                .addOnCompleteListener(this, new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        Toast.makeText(getApplicationContext(), "Location updates stopped!", Toast.LENGTH_SHORT).show();
                    }
                });
    }


    public void showLastKnownLocation() {
        if (mCurrentLocation != null) {
            Toast.makeText(getApplicationContext(), "Lat: " + mCurrentLocation.getLatitude()
                    + ", Lng: " + mCurrentLocation.getLongitude(), Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(getApplicationContext(), "Last known location is not available!", Toast.LENGTH_SHORT).show();
        }
    }

    private void openSettings() {
        Intent intent = new Intent();
        intent.setAction(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package",
                BuildConfig.APPLICATION_ID, null);
        intent.setData(uri);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private boolean checkPermissions() {
        int permissionState = ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        return permissionState == PackageManager.PERMISSION_GRANTED;
    }




    /*********                  Methods Handling Activity's Life Cycle                *************/
    /* -------------------------------------------------------------------------------------------*/
    @Override
    public void onResume() {
        super.onResume();

        // Resuming location updates depending on button state and allowed permissions
        if (mRequestingLocationUpdates && checkPermissions()) {
            startLocationUpdates();

        }

        logUpdateLocation("EZDIRECTION/onResume");

    }


    @Override
    protected void onPause() {
        super.onPause();

        if (mRequestingLocationUpdates) {
            // pausing location updates
            stopLocationUpdates();
        }
    }



    @Override
    public void finish() {
        stopLocationUpdates();
        Intent returnIntent = new Intent();
        returnIntent.putExtra("ezdirection_to_tab2", isCurrentDestinationFavourited);

        if(isCardLoaded){
            Log.d("EZDirection/Finish", "Card was loaded");
            setResult(RESULT_OK, returnIntent);

        }else{
            Log.d("EZDirection/Finish", "Card was NOT loaded");
            setResult(RESULT_CANCELED, returnIntent);
        }

        Log.d("EZDirection/Finish", "EZDirection activity has ended");
        super.finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            // Check for the integer request code originally supplied to startResolutionForResult().
            case REQUEST_CHECK_SETTINGS:
                switch (resultCode) {
                    case Activity.RESULT_OK:
                        Log.e("EZDIRECTION/Result", "User agreed to make required location settings changes.");

                        // AFTER GETTING PERMISSION
                        this.recreate();

                        // Nothing to do. startLocationupdates() gets called in onResume again.
                        break;
                    case Activity.RESULT_CANCELED:
                        Log.e("EZDIRECTION/Result", "User chose not to make required location settings changes.");
                        mRequestingLocationUpdates = false;

                        // AFTER NOT GETTING PERMISSION
                        this.finish();

                        break;
                }
                break;
        }
    }



    /************                  Methods Handling Building EZMaps Cards             *************/
    /* -------------------------------------------------------------------------------------------*/

    /* Send URL with Current Location's Coordinates and Destination */
    public void executeURL(){

        if(mCurrentLocation != null){
            String bodyURL = "https://us-central1-it-project-moonstone-43019.cloudfunctions.net/mapRequest?text=";
            String latitudeURL = Double.toString(mCurrentLocation.getLatitude());
            String longitudeURL = Double.toString(mCurrentLocation.getLongitude());
            String destinationURL = currentDestination.replaceAll(" ", "%20");
            String url = bodyURL + latitudeURL + "," + longitudeURL +  "---" + destinationURL;

            new RetrieveFeed(this).execute(url);

            Log.d("EZDIRECTION/URL", "Latitude: " + latitudeURL);
            Log.d("EZDIRECTION/URL", "Longitude: " + longitudeURL);
            Log.d("EZDIRECTION/URL", "Desination: " + destinationURL);
            Log.d("EZDIRECTION/URL", "URL: " + url);

        }else{

            Toast.makeText(this.getApplicationContext(), "GPS is currently not working",Toast.LENGTH_SHORT).show();
        }

    }


    /* Wait to receive Object initiated by executeURL */
    @Override
    public void processFinish(JSONArray output){

        if (output != null) {
            imageUrlsList = new ArrayList<>();
            textDirectionsList = new ArrayList<>();
            coordinatesList = new ArrayList<>();

            for (int i = 0; i < output.length(); i++) {
                try {

                    JSONObject object = output.getJSONObject(i);
                    JSONObject object_coord = output.getJSONObject(i).getJSONObject("coord");

                    imageUrlsList.add(object.getString("imageURL"));
                    textDirectionsList.add(object.getString("description"));
                    coordinatesList.add(new Coordinate(object_coord.getDouble("lat"),
                            object_coord.getDouble("lng")));

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            Log.d("EZD", "COORDINATES Received: " + coordinatesList);

            initRecyclerView();
            isCardLoaded = true;
            isLocationNotFound = false;
            progressBar.setVisibility(View.INVISIBLE);
            setLeftRightButtonVisibility("VISIBILE");
            invalidateOptionsMenu();

        }else{
            isCardLoaded = false;
            isLocationNotFound = true;
            progressBar.setVisibility(View.INVISIBLE);
            recyclerView.setVisibility(View.GONE);
            setNotFoundVisibility("VISIBLE");
            setLeftRightButtonVisibility("GONE");
        }
    }

    private void setLeftRightButtonVisibility(String visibility){
        switch (visibility.toUpperCase()){
            case "VISIBLE":
                leftButton.setVisibility(View.VISIBLE);
                rightButton.setVisibility(View.VISIBLE);
                break;

            case "INVISIBLE":
                leftButton.setVisibility(View.INVISIBLE);
                rightButton.setVisibility(View.INVISIBLE);
                break;

            case "GONE":
                leftButton.setVisibility(View.GONE);
                rightButton.setVisibility(View.GONE);
                break;
        }
    }

    private void setNotFoundVisibility(String visibility){
        switch (visibility.toUpperCase()){
            case "VISIBLE":
                notFoundImg.setVisibility(View.VISIBLE);
                notFoundText.setVisibility(View.VISIBLE);
                notFoundSubtext.setVisibility(View.VISIBLE);
                break;

            case "INVISIBLE":
                notFoundImg.setVisibility(View.INVISIBLE);
                notFoundText.setVisibility(View.INVISIBLE);
                notFoundSubtext.setVisibility(View.INVISIBLE);
                break;

            case "GONE":
                notFoundImg.setVisibility(View.GONE);
                notFoundText.setVisibility(View.GONE);
                notFoundSubtext.setVisibility(View.GONE);
                break;
        }
    }

    /* Create Recycler View */
    private void initRecyclerView() {
        Log.d("EZDIRECTION/Recycler", "initRecyclerView: init recyclerview");

        layoutManager = new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL,
                false);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(layoutManager);

        adapter = new EZCardRecyclerViewAdapter(textDirectionsList, imageUrlsList, this);
        numView = adapter.getItemCount();
        recyclerView.setAdapter(adapter);
        layoutManager.setSmoothScrollbarEnabled(false);

        // Listening on scrolling
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                updateCounter(layoutManager.findLastCompletelyVisibleItemPosition());
                Log.d("EZDIRECTION/Recycler", "SCROLL STATE CHANGED: " +
                        layoutManager.findLastCompletelyVisibleItemPosition());

            }

            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                Log.d("EZDIRECTION/Recycler", "LAST ITEM: " +
                    layoutManager.findLastCompletelyVisibleItemPosition());

            }
        });

    }


    /*********                     Methods Handling Controlling EZMaps Cards             **********/
    /* -------------------------------------------------------------------------------------------*/
    /* Checking when Card Image has been Swiped */
    private void updateCounter(int newState){
        if(newState != -1){
            counter = newState;
        }
        invalidateOptionsMenu();
    }

    /* Handling Right and Left Card Swipe */
    @Override
    public void onClick(View view){

        switch(view.getId()){
            case R.id.rightButton:
                if(isCardLoaded){
                    swipeRight();
                }

                break;

            case R.id.leftButton:
                if(isCardLoaded){
                    swipeLeft();
                }

                break;

            case R.id.nextStopButton:
                if(isCardLoaded){
                    swipeTo(nextStopCounter);
                }

            case R.id.refreshButton:
                if(isCardLoaded || isLocationNotFound){
                    refresh();
                }
        }

    }

    public void refresh(){
        isCardLoaded = false;
        isLocationNotFound = false;
        progressBar.setVisibility(View.VISIBLE);

        executeURL();

    }

    public void swipeTo(int counter){

        if(counter >= 1){
            this.counter = counter;
        }

        layoutManager.scrollToPosition(counter);
        invalidateOptionsMenu();
        Log.d("EZDIRECTION/Swipe", "SCROLL TO: " + counter + "/" + numView);
        Toast.makeText(getApplicationContext(), "Swipe to " + counter, Toast.LENGTH_SHORT).show();

    }

    public void swipeRight(){
        if(counter < numView - 1){
            counter += 1;
        }

        layoutManager.scrollToPosition(counter);
        invalidateOptionsMenu();
        Log.d("EZDIRECTION/Click", "SCROLL TO: " + counter + "/" + numView);

    }

    public void swipeLeft(){
        if(counter >= 1){
            counter -= 1;
        }
        layoutManager.scrollToPosition(counter);
        invalidateOptionsMenu();
        Log.d("EZDIRECTION/Click", "SCROLL TO: " + counter + "/" + numView);

    }



    /************                   Methods Handling Action Bar                       *************/
    /* -------------------------------------------------------------------------------------------*/
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_ezmap, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        menu.getItem(0).setVisible(true);
        menu.getItem(3).setVisible(true);

        menu.findItem(R.id.title).setTitle("(" + (counter + 1) + "/" + numView + ")");

        if(isCurrentDestinationFavourited){
            menu.getItem(1).setVisible(false);
            menu.getItem(2).setVisible(true);
            Log.d("EZDIRECTION/Bar", "FAVOURITE TRUE");
        }else{
            menu.getItem(1).setVisible(true);
            menu.getItem(2).setVisible(false);
            Log.d("EZDIRECTION/Bar", "FAVOURITE FALSE");
        }

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();

        if(isCardLoaded){
            if(id == R.id.favouriteFull){
                isCurrentDestinationFavourited = false;
                invalidateOptionsMenu();
                return true;
            }

            if(id == R.id.favouriteEmpty){
                isCurrentDestinationFavourited = true;
                invalidateOptionsMenu();
                return true;
            }

            if(id == R.id.options){
                ShareImageDialogFragment bottomSheet = new ShareImageDialogFragment();
                Bundle bundle = new Bundle();
                bundle.putInt("counter", counter);
                bundle.putStringArrayList("imageUrlsList", imageUrlsList);
                bottomSheet.setArguments(bundle);
                bottomSheet.show(getSupportFragmentManager(), "ShareImageDialogFragment");
                return true;
            }
        }

        return super.onOptionsItemSelected(item);
    }


}
