package org.maplibre.navigation.android.navigation.ui.v5.route;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.maplibre.navigation.android.navigation.ui.v5.RadioTourNavigationActivity;
import org.maplibre.navigation.core.location.Location;
import org.maplibre.navigation.core.routeprogress.RouteProgress;
import org.maplibre.navigation.core.utils.RouteUtils;

import java.lang.ref.WeakReference;

public class BypassRouteFetcher extends MapLibreRouteFetcher {
    private static final double BEARING_TOLERANCE = 90d;
    private static final String SEMICOLON = ";";
    private static final int ORIGIN_APPROACH_THRESHOLD = 1;
    private static final int ORIGIN_APPROACH = 0;
    private static final int FIRST_POSITION = 0;
    private static final int SECOND_POSITION = 1;
    private final WeakReference<Context> contextWeakReference;

    private RouteProgress routeProgress;

    private NavigationRoute navigationRoute;
    private final RouteUtils routeUtils = new RouteUtils();


    public BypassRouteFetcher(Context context) {
        super(context);
        contextWeakReference = new WeakReference<>(context);

    }

    @Override
    public void findRouteFromRouteProgress(@NonNull Location location, @NonNull RouteProgress routeProgress) {
        return;
    }

    private boolean invalid(Context context, Location location, RouteProgress routeProgress) {
        return context == null || location == null || routeProgress == null;
    }

    @Nullable
    @Override
    public NavigationRoute.Builder buildRequest(Location location, RouteProgress progress) {
        // TODO: reroute in a cleaner way.
        //  Currently, this bypasses the intended goal of the function to restart the activity.
        //  This may arise many problem, lack stability and be inefficient performance wise.
        //  Also this means that until there is a better way, we must fake a mapBox token.
        RadioTourNavigationActivity a = RadioTourNavigationActivity.instance;
        a.reroute(location);
        return null;
    }

    @Override
    public void cancelRouteCall() {
        if (navigationRoute != null) {
            navigationRoute.cancelCall();
        }
    }
}
