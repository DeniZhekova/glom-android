package com.abborg.glom.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.abborg.glom.Const;
import com.abborg.glom.R;
import com.abborg.glom.utils.RequestHandler;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This service handles sending background push message to the server
 * This can include location updates and other updates within circles
 * This service does not spawn new thread to do the network operations because
 * Volley automatically handles that.
 *
 * This class expects data to be passed in primitive format (i.e. String, integers)
 * and not serialized or parcelled objects.
 *
 * TODO possibly spawning another thread to execute tasks
 *
 * Created by Boat on 8/10/58.
 */
public class CirclePushService extends Service implements LocationListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "CirclePushService";

    /* The location request that will be sent to retrieve location */
    private LocationRequest locationRequest;

    /* List of circle id to broadcast location to */
    private List<String> circles;

    /* Current user id */
    private String userId;

    /* Google Play API client */
    private GoogleApiClient apiClient;

    /* Helper class that verifies Google's Api client */
    private GoogleApiAvailability apiAvailability;

    /* Current user's location */
    private Location userLocation;

    /* Polling interval of the location request to update the location */
    public static final long LOCATION_REQUEST_INTERVAL = 3000;

    public static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Toast.makeText(this, TAG + " starting", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Service starting");

        handleCommand(intent);

        return START_STICKY;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "Location has changed.");
        if (location != null) {
            // broadcast to MainActivity
            userLocation = location;
            Intent intent = new Intent(getResources().getString(R.string.ACTION_USER_LOCATION_UPDATE));
            intent.putExtra(getResources().getString(R.string.EXTRA_USER_LOCATION_UPDATE), location);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

            // loop through list of circles and send updates to them
            for (String circleId : circles) {
                sendLocationUpdateRequest(circleId, userLocation);
            }
        }
    }

    /**
     * Retrieves user's current location based on interval, intent, or last known location
     * @param lastLocation if true, use the last known location
     * @param interval The interval in milliseconds to poll for location updates, specify 0 to make a single request
     */
    private void getUserLocation(boolean lastLocation, long interval) {
        if (lastLocation) {
            userLocation = LocationServices.FusedLocationApi.getLastLocation(apiClient);
            if (userLocation == null) {
                Log.i(TAG, "Requesting new user location with interval of " + interval);
                if (interval > 0) locationRequest.setInterval(interval);
                LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, this);
            }
            else {
                Log.i(TAG, "User last location is intact");

                // broadcast to MainActivity
                Intent intent = new Intent(getResources().getString(R.string.ACTION_USER_LOCATION_UPDATE));
                intent.putExtra(getResources().getString(R.string.EXTRA_USER_LOCATION_UPDATE), userLocation);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

                // loop through list of circles and send updates to them
                for (String circleId : circles) {
                    sendLocationUpdateRequest(circleId, userLocation);
                }
            }
        }
        else {
            Log.i(TAG, "Requesting new user location with interval of " + interval);
            if (interval > 0) locationRequest.setInterval(interval);
            LocationServices.FusedLocationApi.requestLocationUpdates(apiClient, locationRequest, this);
        }
    }

    /**
     * Sends location update to the server
     *
     * @param location
     */
    private void sendLocationUpdateRequest(String circleId, Location location) {
        // initialize the body
        JSONObject body =  new JSONObject();
        final Context context = this;

        try {
            body.put("id", userId);
            body.put("circle", circleId);

            JSONObject loc = new JSONObject();
            loc.put("lat", location.getLatitude());
            loc.put("long", location.getLongitude());

            body.put("location", loc);
        }
        catch (JSONException ex) {
            Log.e(TAG, ex.getMessage());
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, Const.HOST_ADDRESS, body,
                new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject response) {
                        RequestHandler.getInstance(context).handleResponse(context, response);
                    }
                },
                new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        RequestHandler.getInstance(context).handleError(error);
                    }
                })

        {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
                headers.put("AUTHORIZATION", "GLOM-AUTH-TOKEN abcdefghijklmnopqrstuvwxyz0123456789");
                return headers;
            }

//            @Override
//            protected Map<String, String> getParams() {
//                Map<String, String> params = new HashMap<String, String>();
//                params.put("email", "rm@test.com.br");
//                params.put("senha", "aaa");
//                return params;
//            }
        };

        RequestHandler.getInstance(this).addToRequestQueue(request);
    }

    private void handleCommand(Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            userId = intent.getStringExtra(getResources().getString(R.string.EXTRA_BROADCAST_LOCATION_USER_ID));
            String circleId = intent.getStringExtra(getResources().getString(R.string.EXTRA_BROADCAST_LOCATION_CIRCLE_ID));

            // action to add the user's circle to list of broadcast
            if (action.equals(getResources().getString(R.string.ACTION_CIRCLE_ENABLE_LOCATION_BROADCAST))) {
                if (!circles.contains(circleId)) {
                    circles.add(circleId);
                }
            }

            // action to remove the user's circle to list of broadcast
            else if (action.equals(getResources().getString(R.string.ACTION_CIRCLE_DISABLE_LOCATION_BROADCAST))) {
                circles.remove(circleId);
            }

            // disconnect Google API client
            if (circles.isEmpty()) {
                if (apiClient.isConnected()) {
                    apiClient.disconnect();
                    Log.d(TAG, "No more circle to broadcast. Location services disconnected");
                }
            }
            else {
                if (!apiClient.isConnected()) apiClient.connect();
                for (String broadcastCircleId : circles) {
                    Log.d(TAG, "Broadcasting locations to " + broadcastCircleId);
                }
            }
        }
    }

    //TODO verify in onCreate and onResume in the main activity
    public boolean verifyGooglePlayServices(Context context) {
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(context);
        if (resultCode != ConnectionResult.SUCCESS) {
//            if (apiAvailability.isUserResolvableError(resultCode)) {
//                apiAvailability.getErrorDialog(activity, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
//                        .show();
//            } else {
//                Log.i(TAG, "This device is not supported.");
//                activity.finish();
//            }
            return false;
        }

        return true;
    }

    /**
     * One-time initialization of service. If service is already started, this is not called.
     */
    @Override
    public void onCreate() {
        circles = new ArrayList<String>();

        locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);

        apiAvailability = GoogleApiAvailability.getInstance();

        if (verifyGooglePlayServices(this)) {
            apiClient = new GoogleApiClient
                    .Builder(this)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
        }
    }

    /**
     * Clean up before service is destroyed from stopService()
     */
    @Override
    public void onDestroy() {
        Toast.makeText(this, this.getClass().getName() + " done", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Service done");
    }

    @Override
    public void onConnected(Bundle bundle) {
        Log.i(TAG, "Location services connected.");
        getUserLocation(false, LOCATION_REQUEST_INTERVAL);
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.i(TAG, "Location services suspended.");
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.i(TAG, "Location services failed.");
    }
}
