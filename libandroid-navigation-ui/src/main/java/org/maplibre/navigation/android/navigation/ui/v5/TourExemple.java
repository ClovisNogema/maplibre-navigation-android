package org.maplibre.navigation.android.navigation.ui.v5;

import android.content.res.AssetManager;

import org.maplibre.android.geometry.LatLng;
import org.maplibre.geojson.model.BoundingBox;
import org.maplibre.geojson.model.Feature;
import org.maplibre.geojson.model.FeatureCollection;
import org.maplibre.geojson.model.Point;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import kotlinx.serialization.json.JsonElement;

public class TourExemple {
    List<LatLng> myPoints;
    public List<LatLng> csvPoints;
    AssetManager mAssetManager;

    public TourExemple(AssetManager assetManager) {
        mAssetManager = assetManager;
        setupMyPoints();
    }

    private void setupMyPoints() {
        myPoints = new ArrayList<>();
        myPoints.add(new LatLng(48.674017,6.182549));
        myPoints.add(new LatLng(48.672264,6.181440));
    }

    public FeatureCollection readCSVToFeatureCollection(String filepath) {
        List<Feature> list = new ArrayList<>();
        List<String> headers = new ArrayList<>();
        int latIndex = 0, longIndex = 0;
        String line = "";

        try {
            InputStream is = mAssetManager.open(filepath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            boolean readHeader = false;
            while ((line = reader.readLine()) != null) {
                String[] entries = line.split(";");
                if (!readHeader) {
                    for (int i = 0; i < entries.length; i++) {
                        headers.add(entries[i]);
                        if (Objects.equals(entries[i], "GPS Latitude"))
                            latIndex = i;
                        if (Objects.equals(entries[i], "GPS Longitude"))
                            longIndex = i;
                    }
                    readHeader = true;
                    continue;
                }
                Point p = new Point(Double.parseDouble(entries[longIndex]), Double.parseDouble(entries[latIndex]), 0.0, new BoundingBox(0.0, 0.0, 0.0, 0.0));
                Feature feature = new Feature(p);
                for (int i = 0; i < entries.length; i++) {
                    if (i == latIndex || i == longIndex)
                        continue;
                    feature.addProperty(headers.get(i), entries[i]);
                }
                list.add(feature);
            }
            reader.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new FeatureCollection(list);
    }
}
