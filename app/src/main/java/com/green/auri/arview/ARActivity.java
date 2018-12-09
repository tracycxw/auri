package com.green.auri.arview;

import android.annotation.SuppressLint;
import android.hardware.Sensor;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SensorEvent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.Pose;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotTrackingException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.ux.ArFragment;
import com.green.auri.GetNearbyPlacesTask;
import com.green.auri.R;
import com.green.auri.RestaurantResult;
import com.green.auri.SearchAndPosition;
import com.green.auri.dsensor.DProcessedSensor;
import com.green.auri.dsensor.DSensor;
import com.green.auri.dsensor.DSensorEvent;
import com.green.auri.dsensor.DSensorManager;
import com.green.auri.dsensor.interfaces.DProcessedEventListener;
import com.green.auri.utils.LocationListener;
import com.green.auri.utils.LocationUtils;
import com.green.auri.utils.PlaceSearchListener;
import com.green.auri.utils.PlaceSearchUtils;
import com.green.auri.utils.placeData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


/* The main activity that is loaded by the launcher to display the camera screen */
public class ARActivity extends AppCompatActivity implements LocationListener, PlaceSearchListener, SensorEventListener {
    /* Requested to install the ARCore package. */
    private boolean installRequested;
    private DisplayRotationHelper displayRotationHelper;

    private SensorManager sm;
    /* List of Anchors for the current session */
    private final ArrayList<Node> nodes = new ArrayList<>();

    /*
     *  A provided fragment from the Sceneform/AR library.
     *  1. Checks for camera and ARCore requirements on load and during application use.
     *  2. Provides the functionality to render and add the rendered component to the activity.
     *  3. Provides listeners for actions on top of the camera/AR display.
     *  No visible component, just a helper fragment
     */
    private ArFragment arFragment;
    private ArSceneView arSceneView;

    private double latitude;
    private double longitude;
    private double angle;
    private List<HashMap<String, String>> nearbyPlaceList;

    float[] mGravity;
    float[] mGeomagnetic;

    // Locks to coordinate async execution
    private boolean gotLocation = false;    // Will wait until we receive a location
    private boolean gotPlaces = false;      // Will wait until we receive place list
    private boolean executed = false;       // Will only allow one execution to trigger
    private boolean finishedExecuting = true; // Will prevent polling while execution in progress


    private AnchorNode anchorNode;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /* First check if the device is supported or not. */
        if (!CompatUtils.checkIsSupportedDeviceOrFinish(this)) {
            CompatUtils.displayError(this, "Not a supported device.", null);
        }

        /* Sets the ContentView to the custom Layout: res/layout/activity_index.xml */
        setContentView(R.layout.activity_index);
        displayRotationHelper = new DisplayRotationHelper(/*context=*/ this);

        /* Finds the ArFragment added to the activity through the fragment manager. */
        arFragment = (ArFragment) getSupportFragmentManager().findFragmentById(R.id.ux_fragment);
        arFragment.getPlaneDiscoveryController().hide();
        arFragment.getPlaneDiscoveryController().setInstructionView(null);
        arSceneView = arFragment.getArSceneView();
        arSceneView.getPlaneRenderer().setEnabled(false);

        installRequested = false;

        arSceneView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (arSceneView.getSession() == null) {
                    return false;
                }

//                startPollUpdating();
//                updateNearbyPlaces();

                return false;
            }
        });

        // Get an instance of the SensorManager
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if(sm.getSensorList(Sensor.TYPE_ACCELEROMETER).size()!=0){
            Sensor s = sm.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
            Sensor s2 = sm.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).get(0);
            sm.registerListener(this,s, SensorManager.SENSOR_DELAY_NORMAL);
            sm.registerListener(this,s2, SensorManager.SENSOR_DELAY_NORMAL);
        }
        startPollUpdating();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (arSceneView == null) {
            return;
        }

        if (arSceneView.getSession() == null) {
            try {
                /* Uses the CompatUtils helper class to check is the device is a supported ARCore device */
                Session session = CompatUtils.createArSession(this, installRequested);
                if (session == null) {
                    installRequested = CompatUtils.hasCameraPermission(this);
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                CompatUtils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
            startPollUpdating();
        } catch (CameraNotAvailableException e) {
            // In some cases (such as another camera app launching) the camera may be given to
            // a different app instead. Handle this properly by showing a message and recreate the
            // session at the next iteration.
            CompatUtils.displayError(this, "Camera not available. Please restart the app.", null);
            finish();
            return;
        }

        displayRotationHelper.onResume();

        // Get an instance of the SensorManager
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);
        if(sm.getSensorList(Sensor.TYPE_ACCELEROMETER).size()!=0){
            Sensor s = sm.getSensorList(Sensor.TYPE_ACCELEROMETER).get(0);
            Sensor s2 = sm.getSensorList(Sensor.TYPE_MAGNETIC_FIELD).get(0);
            sm.registerListener(this,s, SensorManager.SENSOR_DELAY_NORMAL);
            sm.registerListener(this,s2, SensorManager.SENSOR_DELAY_NORMAL);
        }

//        DSensorManager.startDProcessedSensor(this, DProcessedSensor.TYPE_COMPASS_FLAT_ONLY_AND_DEPRECIATED_ORIENTATION,
//                new DProcessedEventListener() {
//                    @Override
//                    public void onProcessedValueChanged(DSensorEvent dSensorEvent) {
//                        // update UI
//                        // dSensorEvent.values[0] is the azimuth.
//                        if (dSensorEvent.sensorType == DSensor.TYPE_DEPRECIATED_ORIENTATION) {
//                            angle = Math.round(dSensorEvent.values[0]);
//                        }
//                    }
//                });
    }

    public void onSensorChanged(SensorEvent event) {

        Log.i("ANGLE", "SENSOR CHANGE EVENT");
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
            mGravity = event.values;

        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD)
            mGeomagnetic = event.values;

        if (mGravity != null && mGeomagnetic != null) {
            float R[] = new float[9];
            float I[] = new float[9];

            if (sm.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {

                // orientation contains azimut, pitch and roll
                float orientation[] = new float[3];
                sm.getOrientation(R, orientation);

                double azimut = orientation[0];
                angle = -azimut * 360 / (2 * Math.PI);
                Log.i("ANGLE2", "THE CALCULATED ANGLE FROM NORTH "+angle);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onPause() {
        super.onPause();

        DSensorManager.stopDSensor();

        if (arSceneView != null) {
            // GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            displayRotationHelper.onPause();
            arSceneView.pause();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void startPollUpdating() {
        // Create the Handler object (on the main thread by default)
        Handler handler = new Handler();
        // Define the code block to be executed
        Runnable runnableCode = new Runnable() {
            @Override
            public void run() {
                Log.i("POLL", "Polling");
                if (finishedExecuting) {
                    handler.removeCallbacks(this);
                    Log.i("POLL", "Executing update");

                    updateNearbyPlaces();
                    handler.postDelayed(this, 20000);
                } else {
                    Log.i("POLL", "NOT FINISHED");
                }
            }
        };

        // Run after 1 second
        handler.postDelayed(runnableCode, 1000);
    }

    //Execute updates
    private void updateNearbyPlaces() {
//        if (done){
//            finishedExecuting = true;
//            return;
//        }
        gotLocation = false;
        gotPlaces = false;
        executed = false;
        finishedExecuting = false;
        String url = PlaceSearchUtils.getUrl(latitude, longitude, "restaurant"); // get the url of nearby restaurant
        Log.d("onClick", url);
        new GetNearbyPlacesTask().execute(url, ARActivity.this);
        LocationUtils.getCurrentLocation(ARActivity.this, this);
    }

    // Get positioned places
    private void getPositionedPlaces() {

        try {
            Pose cameraRelativePose = Pose.makeTranslation(0, 0, 0);
            Anchor anchor = arSceneView.getSession().createAnchor(cameraRelativePose);
            deleteAllCards();
            anchorNode = new AnchorNode(anchor);
            anchorNode.setParent(arSceneView.getScene());
        } catch (NotTrackingException e) {
            /* Camera is not tracking yet, return and wait for next poll */
            Log.i("POLL", "NOT tracking");
            finishedExecuting = true;
            return;
        }
//        HashMap<String, List<HashMap<String,String>>> result = SearchAndPosition.PositionNearbyPlaces(nearbyPlaceList, latitude, longitude, angle);
        HashMap<Double, List<placeData>> result = SearchAndPosition.PositionNearbyPlaces(nearbyPlaceList, latitude, longitude, angle);


        for(Double bucket: result.keySet()){
            List<placeData> placesInBucket = result.get(bucket);
            List<RestaurantResult> bucketPlaces = new ArrayList<>();

            for (int i = 0; i < placesInBucket.size(); i++) {
                placeData currentGooglePlace = placesInBucket.get(i);

                RestaurantResult restaurantResult = currentGooglePlace.toRestaurantResult();

                bucketPlaces.add(restaurantResult);
            }

            double currentAngle = result.get(bucket).get(0).getBucket();
            double currentDistance = result.get(bucket).get(0).getDistance();

            Log.i("APOS", String.valueOf(bucketPlaces));
            Vector3 cardVector = ARUtils.buildVectorFromAngle(currentAngle, currentDistance);
            addAndCreateCard(bucketPlaces, cardVector);
        }
        finishedExecuting = true;
    }

    // TESTING FUNCTION
    public void createDirectionalCards() {
        Pose cameraRelativePose = Pose.makeTranslation(0, 0, 0);
        Anchor anchor = arSceneView.getSession().createAnchor(cameraRelativePose);
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arSceneView.getScene());
    }

    public void addCard(Node card, Vector3 direction) {
        card.setLocalPosition(direction);
        anchorNode.addChild(card);
    }

    public void addAndCreateCard(List<RestaurantResult> bucket, Vector3 direction) {
        Node card = new RestaurantBucketNode(this, bucket);
        addCard(card, direction);
    }

    public void deleteAllCards() {
        Log.i("POLL", "Removing all cards");
        if (anchorNode != null) {
            List<Node> children = anchorNode.getChildren();
            for (int i = 0; i < children.size(); i++) {
                Node child = children.get(i);
                anchorNode.removeChild(child);
                i--;
            }
        }
    }

    /*
     *  Makes sure that the app has permissions to access the camera.
     *  Does not ask if it was denied with the option of do not ask again.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        /* If the Camera permission has not been given. */
        if (!CompatUtils.hasCameraPermission(this)) {

            /* If the user still wants us to ask for permissions */
            if (CompatUtils.shouldShowRequestPermissionRationale(this)) {
                // Permission denied without checking "Do not ask again".
                // Launch the permissions settings again for the user to select.
                CompatUtils.launchPermissionSettings(this);
            } else {
                Toast.makeText(this, "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onLocationUpdated(double latitude, double longitude) {
        Log.i("POSITIONED", "Location updated");
        this.latitude = latitude;
        this.longitude = longitude;
        if (gotPlaces && !executed) {
            executed = true;
            getPositionedPlaces();

        }
        gotLocation = true;
    }

    @Override
    public void onPlaceSearchComplete(List<HashMap<String, String>> nearbyPlacesList) {
        this.nearbyPlaceList = nearbyPlacesList;
        if (!nearbyPlacesList.isEmpty()) {
            if (gotLocation && !executed) {
                executed = true;
                getPositionedPlaces();
            }
            gotPlaces = true;
        }
        if (nearbyPlacesList.isEmpty()) {
            String url = PlaceSearchUtils.getUrl(latitude, longitude, "restaurant"); // get the url of nearby restaurant
            new GetNearbyPlacesTask().execute(url, ARActivity.this);
        }
    }

    private void restartARSession() {
        try {
            arSceneView.getSession().pause();
            arSceneView.getSession().resume();
        } catch (CameraNotAvailableException e) {
            Log.i("ARTAG", "Camera not available?");
        }
    }

}
