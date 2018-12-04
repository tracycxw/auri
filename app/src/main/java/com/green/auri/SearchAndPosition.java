package com.green.auri;

import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class SearchAndPosition {

    private static final String TAG = "SearchAndPosition";

    // Position nearby places relative to current location
    // Iterate over nearbyPlaceList and get each position unit vector based on a reference angle
    public static List<HashMap<String, String>> PositionNearbyPlaces(List<HashMap<String, String>> nearbyPlacesList, double myLatitude, double myLongitude, double theta){
        Log.i("Position", "My Position: "+myLatitude+" "+myLongitude);
        Log.i("Angle", "My angle from North is: "+theta);
        Log.i("POSITIONED", "Positioning nearby places");
        List<HashMap<String, String>> positionedPlaces = new ArrayList<>();

        for (int i = 0; i < nearbyPlacesList.size(); i++) {
            HashMap<String, String> currentGooglePlace = nearbyPlacesList.get(i);

            //Get place information
            double lat = Double.parseDouble(currentGooglePlace.get("lat"));
            double lng = Double.parseDouble(currentGooglePlace.get("lng"));
            String placeName = currentGooglePlace.get("place_name");
            String rating = currentGooglePlace.get("rating");
            String photoRef = currentGooglePlace.get("photoURL");

            Log.i("Position", "Place: "+placeName+" Position: "+lat+" "+lng);

            //Get the relative position data
            double[] relativePositionList = RelativePosition(myLatitude, myLongitude, lat,lng,theta);

            //Create a new Positioned Place item with all relaevent data for AR
            HashMap<String, String> positionedPlace = new HashMap<String, String>();
            positionedPlace.put("Name",placeName);
            positionedPlace.put("X",Double.toString(relativePositionList[0]));
            positionedPlace.put("Y",Double.toString(relativePositionList[1]));
            positionedPlace.put("Distance",Double.toString(relativePositionList[2]));
            positionedPlace.put("Rating",rating);
            positionedPlace.put("photoRef",photoRef);

            positionedPlaces.add(positionedPlace);

            Log.i("Position", relativePositionList[0]+ " " +relativePositionList[1]);
        }

        return positionedPlaces;
    }

    // Calculate relative position
    // 1. Calculate unit vector from current location
    // 2. Rotate from true north using rotation matrix
    // 3. Return position array
    private static double[] RelativePosition(double myLatitude, double myLongitude, double lat, double lng, double theta){
        //get deltas from your position
        double latChange = myLatitude - lat;
        double lngChange = myLongitude - lng;

        Log.i("Position","latChange"+latChange);
        Log.i("Position","lngChange"+lngChange);

        //distance, this will be the scalar to get to unit vector
        double r = Math.sqrt(latChange*latChange + lngChange*lngChange);

        //Generate unit vectors for new position
        double unitLat = latChange/r;
        double unitLng = lngChange/r;

        double radians = Math.PI * theta / 180;

        //Rotate vectors using rotation matrix formula
        double lat2 = Math.cos(radians)*unitLng - Math.sin(radians)*unitLat;
        double lng2 = Math.cos(radians)*unitLat + Math.sin(radians)*unitLng;


        Log.i("Position","unitLat: "+unitLat);
        Log.i("Position","unitLng: "+unitLng);
        Log.i("Position","rotated unitLat: "+lat2);
        Log.i("Position","rotated unitLng: "+lng2);

        double[] positions = {lat2, lng2, r};

        return positions;
    }

}