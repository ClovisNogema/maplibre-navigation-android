package org.maplibre.navigation.android.navigation.ui.v5;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.Gson;

import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.maps.Style;
import org.maplibre.android.style.layers.CircleLayer;
import org.maplibre.android.style.layers.PropertyFactory;
import org.maplibre.android.style.sources.GeoJsonSource;
import org.maplibre.geojson.model.Feature;
import org.maplibre.geojson.model.FeatureCollection;
import org.maplibre.geojson.model.Point;
import org.maplibre.navigation.android.navigation.ui.v5.listeners.NavigationListener;
import org.maplibre.navigation.core.location.Location;
import org.maplibre.navigation.core.models.DirectionsResponse;
import org.maplibre.navigation.core.models.DirectionsRoute;
import org.maplibre.navigation.core.models.RouteLeg;
import org.maplibre.navigation.core.models.RouteOptions;
import org.maplibre.navigation.core.navigation.MapLibreNavigationOptions;
import org.maplibre.navigation.core.navigation.NavigationConstants;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import timber.log.Timber;

/**
 * Serves as a launching point for the custom drop-in UI, {@link NavigationView}.
 * <p>
 * Demonstrates the proper setup and usage of the view, including all lifecycle methods.
 */
public class RadioTourNavigationActivity extends AppCompatActivity implements OnNavigationReadyCallback, NavigationListener {

  private NavigationView navigationView;

  public MeterPointsManager meterPointsManager;

  private Style mStyle;

  public static RadioTourNavigationActivity instance = null;

  OkHttpClient mClient;
  Handler mHandler = new Handler();

  @Override
  protected void onCreate(@Nullable Bundle savedInstanceState) {
    setTheme(androidx.appcompat.R.style.Theme_AppCompat_NoActionBar);
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_navigation);
    navigationView = findViewById(R.id.navigationView);
    navigationView.onCreate(savedInstanceState);
    initialize();
    TourExemple te = new TourExemple(getAssets());
    meterPointsManager = new MeterPointsManager();
    meterPointsManager.addMeters(te.readCSVToFeatureCollection("nancy_spe.csv"));
    mClient = new OkHttpClient();

    mHandler.postDelayed(new Runnable() {
      @Override
      public void run() {
        if (meterPointsManager.metersReading.isEmpty() && meterPointsManager.meters.isEmpty())
          return;
        List<Feature> a = new ArrayList<>();
        List<Feature> b = new ArrayList<>();
        List<Feature> c = new ArrayList<>();
        List<Feature> it = new ArrayList<>(meterPointsManager.meters.values());
        for (int i = 0; i < 25 && i < it.size(); i++) {
          a.add(it.get(i));
        }
        meterPointsManager.updateMeters(a, MeterPointsManager.UNREAD, MeterPointsManager.READING);
        it = new ArrayList<>(meterPointsManager.metersReading.values());
        for (int i = 0; i < 10 && i < it.size(); i++) {
          b.add(it.get(i));
        }
        for (int i = 10; i < 20 && i < it.size(); i++) {
          c.add(it.get(i));
        }
        meterPointsManager.updateMeters(b, MeterPointsManager.READING, MeterPointsManager.VALID);
        meterPointsManager.updateMeters(c, MeterPointsManager.READING, MeterPointsManager.ALERT);
        updateLayers();

        mHandler.postDelayed(this, 750);
      }
    }, 2000);


    instance = this;
  }

  @Override
  public void onStart() {
    super.onStart();
    navigationView.onStart();
  }

  @Override
  public void onResume() {
    super.onResume();
    navigationView.onResume();
    instance = this;
  }

  @Override
  public void onLowMemory() {
    super.onLowMemory();
    navigationView.onLowMemory();
  }

  @Override
  public void onBackPressed() {
    // If the navigation view didn't need to do anything, call super
    if (!navigationView.onBackPressed()) {
      super.onBackPressed();
    }
  }

  @Override
  protected void onSaveInstanceState(Bundle outState) {
    navigationView.onSaveInstanceState(outState);
    super.onSaveInstanceState(outState);
  }

  @Override
  protected void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    navigationView.onRestoreInstanceState(savedInstanceState);
  }

  @Override
  public void onPause() {
    super.onPause();
    navigationView.onPause();
    instance = null;
  }

  @Override
  public void onStop() {
    super.onStop();
    navigationView.onStop();
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    navigationView.onDestroy();
  }

  @Override
  public void onNavigationReady(boolean isRunning) {
    mStyle = navigationView.retrieveNavigationmapLibreMap().retrieveMap().getStyle();
    NavigationViewOptions.Builder options = NavigationViewOptions.builder();
    options.navigationListener(this);
    extractRoute(options);
    extractPoints();
    extractConfiguration(options);
    options.navigationOptions(new MapLibreNavigationOptions());
    navigationView.startNavigation(options.build());
  }

  @Override
  public void onCancelNavigation() {
    finishNavigation();
  }

  @Override
  public void onNavigationFinished() {
    finishNavigation();
  }

  @Override
  public void onNavigationRunning() {
    // Intentionally empty
  }

  private void initialize() {
    Parcelable position = getIntent().getParcelableExtra(NavigationConstants.NAVIGATION_VIEW_INITIAL_MAP_POSITION);
    if (position != null) {
      navigationView.initialize(this, (CameraPosition) position);
    } else {
      navigationView.initialize(this);
    }
  }

  private void extractRoute(NavigationViewOptions.Builder options) {
    DirectionsRoute route = NavigationLauncher.extractRoute(this);
    options.directionsRoute(route);
  }

  private void extractConfiguration(NavigationViewOptions.Builder options) {
    SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
    options.shouldSimulateRoute(preferences.getBoolean(NavigationConstants.NAVIGATION_VIEW_SIMULATE_ROUTE, false));
  }

  private void setupLayer(String sourceId, String layerId, String geoJson, String color, float radius) {
    GeoJsonSource source = new GeoJsonSource(sourceId);
    // If later there is a way to pass w/o cast then do this.
    source.setGeoJson(geoJson);
    mStyle.addSource(source);
    CircleLayer layer = new CircleLayer(layerId, sourceId);
    layer.withProperties(
            PropertyFactory.circleRadius(radius),
            PropertyFactory.circleOpacity(0.75f),
            PropertyFactory.circleColor(color)
    );
    mStyle.addLayer(layer);
  }

  private void updateLayer(String sourceId, String layerId, String geoJson) {
    try {
      if (this.isDestroyed() || this.isFinishing())
        return;
      if (mStyle.getLayer(layerId) != null || mStyle.getSource(sourceId) != null) {
        ((GeoJsonSource) mStyle.getSourceAs(sourceId)).setGeoJson(geoJson);
      }
    } catch (Exception e) {
      Log.e("MPM", "updateLayer: exception", e);
      throw new RuntimeException(e);
    }
  }

  private void updateLayers() {
    updateLayer("base_src", "base_layer", meterPointsManager.metersJson);
    updateLayer("reading_src", "reading_layer", meterPointsManager.metersReadingJson);
    updateLayer("valid_src", "valid_layer", meterPointsManager.metersValidJson);
    updateLayer("alert_src", "alert_layer", meterPointsManager.metersAlertJson);
    updateLayer("no_index_src", "no_index_layer", meterPointsManager.metersNoIndexJson);
  }

  private void extractPoints() {
    setupLayer("base_src", "base_layer", meterPointsManager.metersJson, "#555555", 5.0f);
    setupLayer("reading_src", "reading_layer", meterPointsManager.metersReadingJson, "#00FF00", 7.5f);
    setupLayer("valid_src", "valid_layer", meterPointsManager.metersValidJson, "#0000FF", 2.5f);
    setupLayer("alert_src", "alert_layer", meterPointsManager.metersAlertJson, "#FF0000", 5.0f);
    setupLayer("no_index_src", "no_index_layer", meterPointsManager.metersNoIndexJson, "#FF5500", 5.0f);
  }

  private void finishNavigation() {
    NavigationLauncher.cleanUpPreferences(this);
    finish();
  }

  private Point getLastLocationFromRoutes(DirectionsResponse directionsResponse) {
    return directionsResponse.getWaypoints().get(directionsResponse.getWaypoints().size() - 1).getLocation();
  }

  public void reroute(Location location) {
    Point origin = new Point(Arrays.asList(location.getLatitude(), location.getLongitude()));

    RequestBody formBody = new FormBody.Builder()
            .add("starting_position", location.getLatitude() + "," + location.getLongitude())
            //.add("end_position", location.getLatitude() + "," + location.getLongitude())
            .add("points", meterPointsManager.toRequestBody())
            .build();

    Request request = new Request.Builder()
            .header("User-Agent", "Modified MapLibre Android Navigation SDK Demo App")
            .url(getString(R.string.routing_url))
            .header("Api-Key", getString(R.string.api_key))
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
                    .withLanguage("de")
                    .withRequestUuid("0000-0000-0000-0000")
                    .build();

            mRoute = directionsRoutes.get(0).copy(geometry, legs, 0.0, 0.0, 0.0, 0.0, "", copyRouteOptions, "fr");

            restartNavigation(location);

          } else {
            Timber.e("calculateRoute Request to Demo server failed with status code: %s: %s", response.code(), response.body());
          }
        }
      }
    });
  }

  public DirectionsRoute mRoute;

  public void restartNavigation(Location location) {
    NavigationLauncherOptions options = NavigationLauncherOptions.builder()
            .directionsRoute(mRoute)
            //.shouldSimulateRoute(mSimulateRoute)
            .initialMapCameraPosition(new CameraPosition.Builder()
                    .target(new LatLng(location.getLatitude(), location.getLongitude()))
                    .build())
            .lightThemeResId(R.style.TestNavigationViewLight)
            .darkThemeResId(R.style.TestNavigationViewDark)
            .build();
    NavigationLauncher.startNavigation(this, options);
  }
}
