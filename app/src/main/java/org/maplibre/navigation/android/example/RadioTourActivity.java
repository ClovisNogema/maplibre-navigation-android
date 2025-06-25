package org.maplibre.navigation.android.example;

import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.OnMapReadyCallback;
import org.maplibre.android.maps.Style;
import org.maplibre.android.plugins.annotation.SymbolManager;
import org.maplibre.android.plugins.annotation.SymbolOptions;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.model.Feature;
import org.maplibre.geojson.model.Point;
import org.maplibre.navigation.android.example.databinding.ActivityNavigationUiBinding;
import org.maplibre.navigation.android.navigation.ui.v5.MeterPointsManager;
import org.maplibre.navigation.android.navigation.ui.v5.NavigationLauncher;
import org.maplibre.navigation.android.navigation.ui.v5.NavigationLauncherOptions;
import org.maplibre.navigation.android.navigation.ui.v5.TourExemple;
import org.maplibre.navigation.android.navigation.ui.v5.route.NavigationMapRoute;
import org.maplibre.navigation.core.models.DirectionsResponse;
import org.maplibre.navigation.core.models.DirectionsRoute;
import org.maplibre.navigation.core.models.RouteLeg;
import org.maplibre.navigation.core.models.RouteOptions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import timber.log.Timber;

public class RadioTourActivity extends AppCompatActivity implements OnMapReadyCallback, MapLibreMap.OnMapClickListener {

    private MapLibreMap mMapLibreMap = null;

    private String mLanguage = Locale.getDefault().getLanguage();
    private DirectionsRoute mRoute;
    private NavigationMapRoute mNavigationMapRoute;
    private Point mDestination;
    private LocationComponent mLocationComponent;

    OkHttpClient mClient;
    SymbolManager mSymbolManager;

    private ActivityNavigationUiBinding binding;

    private boolean mSimulateRoute = false;
    private Style mStyle;

    MeterPointsManager meterPointsManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
        }

        TourExemple tourExemple = new TourExemple(getAssets());
        binding = ActivityNavigationUiBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        binding.mapView.onCreate(savedInstanceState);
        binding.mapView.getMapAsync(this);

        meterPointsManager = new MeterPointsManager();
        meterPointsManager.addMeters(tourExemple.readCSVToFeatureCollection("nancy_spe.csv"));
        meterPointsManager.moveMeter(meterPointsManager.meters.values().iterator().next(), MeterPointsManager.UNREAD, MeterPointsManager.READING);

        binding.startRouteButton.setOnClickListener(v -> {
            if (mRoute != null) {
                Location userLocation = mMapLibreMap.getLocationComponent().getLastKnownLocation();
                if (userLocation == null)
                    return;

                NavigationLauncherOptions options = NavigationLauncherOptions.builder()
                        .directionsRoute(mRoute)
                        .shouldSimulateRoute(mSimulateRoute)
                        .initialMapCameraPosition(new CameraPosition.Builder()
                                .target(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()))
                                .build())
                        .lightThemeResId(R.style.TestNavigationViewLight)
                        .darkThemeResId(R.style.TestNavigationViewDark)
                        .build();
                NavigationLauncher.startNavigation(this, options);
            }
        });

        binding.simulateRouteSwitch.setOnCheckedChangeListener(((buttonView, isChecked) -> mSimulateRoute = isChecked));

        mClient = new OkHttpClient();
    }

    @Override
    public void onMapReady(@NonNull MapLibreMap mapLibreMap) {
        mMapLibreMap = mapLibreMap;
        RadioTourActivity context = this;

        mapLibreMap.setStyle(new Style.Builder().fromUri(getString(R.string.map_style_light)), new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                enableLocationComponent(style);
                mNavigationMapRoute = new NavigationMapRoute(binding.mapView, mapLibreMap);
                mMapLibreMap.addOnMapClickListener(context); // replace with actual context
                mSymbolManager = new SymbolManager(binding.mapView, mMapLibreMap, style);


                Snackbar.make(findViewById(R.id.container), "Waiting for radio tour", Snackbar.LENGTH_LONG).show();
                mStyle = style;
            }
        });
    }

    private void addMarkers(Point[] locations) {
        for (Point location: locations) {
            SymbolOptions symbolOptions = new SymbolOptions();
            symbolOptions.withLatLng(new LatLng(location.getLatitude(), location.getLongitude()));
            mSymbolManager.create(symbolOptions);
        }
    }

    private void enableLocationComponent(Style style) {
        mLocationComponent = mMapLibreMap.getLocationComponent();

        mLocationComponent.activateLocationComponent(LocationComponentActivationOptions.builder(this, style).build());
        mLocationComponent.setLocationComponentEnabled(true);
        mLocationComponent.setCameraMode(CameraMode.TRACKING_GPS_NORTH);
        mLocationComponent.setRenderMode(RenderMode.NORMAL);
    }

    @Override
    public boolean onMapClick(@NonNull LatLng latLng) {
        binding.clearPoints.setVisibility(View.VISIBLE);
        //askForRoute();
        //askForMarkers();
        calculateRoute();
        placeMarkers();
        return false;
    }

    private void askForMarkers() {
        clearMarkers();
        RequestBody formBody = new FormBody.Builder()
                //.add("starting_position", new Gson().toJson(origin))
                //.add("end_position", new Gson().toJson(origin))
                .add("raw_locations", "")
                .build();

        Request request = new Request.Builder()
                .header("User-Agent", "Modified MapLibre Android Navigation SDK Demo App")
                .url(getString(R.string.routing_url))
                .post(formBody)
                .build();

        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) {
                Timber.e(e, "calculateRoute failed to get route from DemoServer");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (!res.isSuccessful())
                        return;
                    String responseJson = res.body().string();
                    JSONArray outerArray = new JSONArray(responseJson);

                    runOnUiThread(() -> {
                        try {
                            for (int i = 0; i < outerArray.length(); i++) {
                                JSONArray coords = outerArray.getJSONArray(i);
                                LatLng latLng = new LatLng(coords.getDouble(0), coords.getDouble(1));
                                SymbolOptions symbolOptions = new SymbolOptions();
                                symbolOptions.withLatLng(latLng);
                                mSymbolManager.create(symbolOptions);
                                mMapLibreMap.addMarker(new MarkerOptions().position(latLng));
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    private void askForRoute() {
        binding.startRouteLayout.setVisibility(View.GONE);

        Location lastLocation = mMapLibreMap.getLocationComponent().getLastKnownLocation();

        if (lastLocation == null) {
            Timber.e("calculateRoute couldn't get lastKnownLocation");
            return;
        }

        Point origin = new Point(Arrays.asList(lastLocation.getLatitude(), lastLocation.getLongitude()));

        RequestBody formBody = new FormBody.Builder()
                //.add("starting_position", new Gson().toJson(origin))
                //.add("end_position", new Gson().toJson(origin))
                .build();

        Request request = new Request.Builder()
                .header("User-Agent", "Modified MapLibre Android Navigation SDK Demo App")
                .url(getString(R.string.routing_url))
                .post(formBody)
                .build();

        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) {
                Timber.e(e, "calculateRoute failed to get route from DemoServer");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (res.isSuccessful()) {
                        Timber.d("calculateRoute to DemoServer successful with status code: %s", response.code());

                        String responseBodyJson = res.body().string();
                        DirectionsResponse maplibreResponse = DirectionsResponse.fromJson(responseBodyJson);

                        Point destination = getLastLocationFromRoutes(maplibreResponse);
                        List coordinates = List.of(origin, destination);
                        List<DirectionsRoute> directionsRoutes = maplibreResponse.getRoutes();


                        String geometry = directionsRoutes.get(0).getGeometry();
                        List<RouteLeg> legs = directionsRoutes.get(0).getLegs();
                        RouteOptions copyRouteOptions = new RouteOptions.Builder("https://valhalla.routing", "valhalla", "valhalla", coordinates)
                                .withAccessToken("valhalla")
                                .withVoiceInstructions(true)
                                .withBannerInstructions(true)
                                .withLanguage("fr")
                                .withRequestUuid("0000-0000-0000-0000")
                                .build();


                        mRoute = directionsRoutes.get(0).copy(geometry, legs, 0.0, 0.0, 0.0, 0.0, "", copyRouteOptions, "fr");

                        runOnUiThread(() -> {
                            mNavigationMapRoute.addRoutes(directionsRoutes);
                            binding.startRouteLayout.setVisibility(View.VISIBLE);
                        });

                    } else {
                        Timber.e("calculateRoute Request to Demo server failed with status code: %s: %s", response.code(), response.body());
                    }
                }
            }
        });
    }

    private Point getLastLocationFromRoutes(DirectionsResponse directionsResponse) {
        return directionsResponse.getWaypoints().get(directionsResponse.getWaypoints().size() - 1).getLocation();
    }

    private void calculateRoute() {
        Location lastLocation = mMapLibreMap.getLocationComponent().getLastKnownLocation();

        if (lastLocation == null) {
            Timber.e("calculateRoute couldn't get lastKnownLocation");
            return;
        }

        Point origin = new Point(Arrays.asList(lastLocation.getLatitude(), lastLocation.getLongitude()));

        RequestBody formBody = new FormBody.Builder()
                .add("lang", "fr")
                .add("starting_position", lastLocation.getLatitude() + "," + lastLocation.getLongitude())
                //.add("end_position", new Gson().toJson(origin))
                .add("points", meterPointsManager.toRequestBody())
                .build();

        Request request = new Request.Builder()
                .header("User-Agent", "Modified MapLibre Android Navigation SDK Demo App")
                .header("Api-Key", getString(R.string.api_key))
                .url(getString(R.string.routing_url))
                .post(formBody)
                .build();

        mClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, IOException e) {
                Timber.e(e, "calculateRoute failed to get route from DemoServer");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                try (Response res = response) {
                    if (res.isSuccessful()) {
                        Timber.d("calculateRoute to DemoServer successful with status code: %s", response.code());

                        String responseBodyJson = res.body().string();
                        DirectionsResponse maplibreResponse = DirectionsResponse.fromJson(responseBodyJson);

                        Point destination = getLastLocationFromRoutes(maplibreResponse);
                        List coordinates = List.of(origin, destination);
                        List<DirectionsRoute> directionsRoutes = maplibreResponse.getRoutes();


                        String geometry = directionsRoutes.get(0).getGeometry();
                        List<RouteLeg> legs = directionsRoutes.get(0).getLegs();
                        RouteOptions copyRouteOptions = new RouteOptions.Builder("https://valhalla.routing", "valhalla", "valhalla", coordinates)
                                .withAccessToken("valhalla")
                                .withVoiceInstructions(true)
                                .withBannerInstructions(true)
                                .withLanguage("fr")
                                .withRequestUuid("0000-0000-0000-0000")
                                .build();

                        mRoute = directionsRoutes.get(0).copy(geometry, legs, 0.0, 0.0, 0.0, 0.0, "", copyRouteOptions, "fr");

                        runOnUiThread(() -> {
                            mNavigationMapRoute.addRoutes(directionsRoutes);
                            binding.startRouteLayout.setVisibility(View.VISIBLE);
                        });

                    } else {
                        Timber.e("calculateRoute Request to Demo server failed with status code: %s: %s", response.code(), response.body());
                    }
                }
            }
        });
    }

    private void clearMarkers() {
        for (Marker marker : mMapLibreMap.getMarkers())
            mMapLibreMap.removeMarker(marker);
        if (mStyle.getLayer(LAYER_ID) != null)
            mStyle.removeLayer(LAYER_ID);
        if (mStyle.getSource(SOURCE_ID) != null)
            mStyle.removeSource(SOURCE_ID);
    }

    final String LAYER_ID = "32";
    final String SOURCE_ID = "src";

    private void placeMarkers() {
        clearMarkers();

        GeoJsonSource source = new GeoJsonSource(SOURCE_ID);
        source.setGeoJson(meterPointsManager.metersJson);
        mStyle.addSource(source);
        CircleLayer layer = new CircleLayer(LAYER_ID, SOURCE_ID);
        layer.withProperties(
                PropertyFactory.circleRadius(5.0f), // radius in pixels
                PropertyFactory.circleColor("#FF5722"), // optional: set a color
                PropertyFactory.circleOpacity(0.75f) // optional: transparency
        );
        mStyle.addLayer(layer);
    }

    @Override
    protected void onResume() {
        super.onResume();
        binding.mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        binding.mapView.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        binding.mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        binding.mapView.onStop();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        binding.mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mMapLibreMap != null)
            mMapLibreMap.removeOnMapClickListener(this);
        binding.mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        binding.mapView.onSaveInstanceState(outState);
    }
}
