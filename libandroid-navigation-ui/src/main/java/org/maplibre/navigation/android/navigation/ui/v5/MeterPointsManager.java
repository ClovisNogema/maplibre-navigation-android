package org.maplibre.navigation.android.navigation.ui.v5;

import android.util.Log;

import androidx.core.util.Pair;

import org.maplibre.geojson.Geometry;
import org.maplibre.geojson.model.BoundingBox;
import org.maplibre.geojson.model.Feature;
import org.maplibre.geojson.model.FeatureCollection;
import org.maplibre.geojson.model.Point;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class MeterPointsManager {
    public HashMap<Pair<String, String>, Feature> meters;
    public HashMap<Pair<String, String>, Feature> metersReading;
    public HashMap<Pair<String, String>, Feature> metersValid;
    public HashMap<Pair<String, String>, Feature> metersAlert;
    public HashMap<Pair<String, String>, Feature> metersNoIndex;

    public String metersJson = "";
    public String metersReadingJson = "";
    public String metersValidJson = "";
    public String metersAlertJson = "";
    public String metersNoIndexJson = "";

    public static final int UNREAD = 0;
    public static final int READING = 1;
    public static final int VALID = 2;
    public static final int ALERT = 3;
    public static final int NO_INDEX = 4;

    ThreadPoolExecutor pool;

    public MeterPointsManager() {
        meters = new HashMap<>();
        metersReading = new HashMap<>();
        metersValid = new HashMap<>();
        metersAlert = new HashMap<>();
        metersNoIndex = new HashMap<>();
        pool = (ThreadPoolExecutor) Executors.newCachedThreadPool();
        threadJson(UNREAD);
        threadJson(READING);
        threadJson(VALID);
        threadJson(ALERT);
        threadJson(NO_INDEX);
    }

    public FeatureCollection toFeatureCollection(HashMap<?, Feature> m) {
        return new FeatureCollection(new ArrayList<>(m.values()));
    }

    private FeatureCollection toFeatureCollectionNoDup(HashMap<?, Feature> m) {
        List<Feature> list = new ArrayList<>(m.values());
        List<Feature> res = new ArrayList<>();

        for (Feature f : list) {
            boolean dontMerge = false;
            if (f == null)
                break;
            Point a = (Point) f.getGeometry();
            for (Feature i: res) {
                Point b = (Point) i.getGeometry();
                if (a == b) {
                    dontMerge = true;
                    break;
                }
            }
            if (!dontMerge)
                res.add(f);
        }
        return new FeatureCollection(res);
    }

    private String mapToJson(HashMap<?, Feature> m) {
        return toFeatureCollectionNoDup(m).toJson();
    }

    public void updateJson(int state) {
        switch (state) {
            case UNREAD -> metersJson = mapToJson(meters);
            case READING -> metersReadingJson = mapToJson(metersReading);
            case VALID -> metersValidJson = mapToJson(metersValid);
            case ALERT -> metersAlertJson = mapToJson(metersAlert);
            case NO_INDEX -> metersNoIndexJson = mapToJson(metersNoIndex);
        }
    }

    public void threadJson(int state) {
        pool.execute(new Runnable() {
            @Override
            public void run() {
                updateJson(state);
            }
        });
    }

    /**
     * Ajoute un compteur dans la liste des compteurs non lus
     * @param lat latitude
     * @param lon longitude
     * @param serial numero SN
     * @param type type de releve
     */
    public void addMeter(double lat, double lon, String serial, String type) {
        Feature f = new Feature(new Point(List.of(lon, lat)));
        f.addProperty("serial", serial);
        f.addProperty("type", type);
        meters.put(Pair.create(serial, type), f);
    }

    public void addMeters(double[] lat, double[] lon, String[] serial, String[] type) {
        for (int i = 0; i < lat.length; i++)
            addMeter(lat[i], lon[i], serial[i], type[i]);
    }

    public void addMeters(FeatureCollection toAdd) {
        for (Feature f : toAdd.getFeatures()) {
            meters.put(new Pair<>(f.getStringProperty("serial"), f.getStringProperty("type")), f);
        }
        Log.d("MPM", "addMeters: " + meters.size());
        updateJson(UNREAD);
    }

    private int lastMeterCategory = UNREAD;

    public Feature getMeter(String serial, String type) {
        Pair<String, String> key = new Pair<>(serial, type);

        Feature res = meters.get(key);
        lastMeterCategory = UNREAD;
        if (res != null)
            return res;

        res = metersReading.get(key);
        lastMeterCategory = READING;
        if (res != null)
            return res;

        res = metersValid.get(key);
        lastMeterCategory = VALID;
        if (res != null)
            return res;

        res = metersAlert.get(key);
        lastMeterCategory = ALERT;
        if (res != null)
            return res;

        res = metersNoIndex.get(key);
        lastMeterCategory = NO_INDEX;
        return res;
    }


    public void updateMeter(String serial, String type, int state) {
        Feature f = getMeter(serial, type);
        if (f == null)
            return;

        moveMeter(f, lastMeterCategory, state);
    }

    // Prefer this one
    public void updateMeter(String serial, String type, int from, int to) {
        Pair<String, String> key = new Pair<>(serial, type);
        Feature meter = null;

        switch (from) {
            case UNREAD -> meter = meters.remove(key);
            case READING -> meter = metersReading.remove(key);
            case VALID -> meter = metersValid.remove(key);
            case ALERT -> meter = metersAlert.remove(key);
            case NO_INDEX -> meter = metersNoIndex.remove(key);
        }
        switch (to) {
            case UNREAD -> meters.put(key, meter);
            case READING -> metersReading.put(key, meter);
            case VALID -> metersValid.put(key, meter);
            case ALERT -> metersAlert.put(key, meter);
            case NO_INDEX -> metersNoIndex.put(key, meter);
        }
        threadJson(from);
        threadJson(to);
    }

    public void moveMeter(Feature meter, int from, int to) {
        Pair<String, String> key = new Pair<>(meter.getStringProperty("serial"), meter.getStringProperty("type"));

        switch (from) {
            case UNREAD -> meters.remove(key);
            case READING -> metersReading.remove(key);
            case VALID -> metersValid.remove(key);
            case ALERT -> metersAlert.remove(key);
            case NO_INDEX -> metersNoIndex.remove(key);
        }
        switch (to) {
            case UNREAD -> meters.put(key, meter);
            case READING -> metersReading.put(key, meter);
            case VALID -> metersValid.put(key, meter);
            case ALERT -> metersAlert.put(key, meter);
            case NO_INDEX -> metersNoIndex.put(key, meter);
        }
        threadJson(from);
        threadJson(to);
    }

    /**
     * Update a list of meters stored as a features
     * @param meterList list of Features representing the meters
     * @param from UNREAD, READING, VALID, ALERT, NO_INDEX
     * @param to UNREAD, READING, VALID, ALERT, NO_INDEX
     */
    public void updateMeters(List<Feature> meterList, int from, int to) {
        List<Pair<String, String>> keys = new ArrayList<>();
        for (Feature m : meterList)
            keys.add(new Pair<>(m.getStringProperty("serial"), m.getStringProperty("type")));
        switch (from) {
            case UNREAD -> {
                for (Pair<String, String> k : keys) meters.remove(k);
            }
            case READING -> {
                for (Pair<String, String> k : keys) metersReading.remove(k);
            }
            case VALID -> {
                for (Pair<String, String> k : keys) metersValid.remove(k);
            }
            case ALERT -> {
                for (Pair<String, String> k : keys) metersAlert.remove(k);
            }
            case NO_INDEX -> {
                for (Pair<String, String> k : keys) metersNoIndex.remove(k);
            }
        }
        switch (to) {
            case UNREAD -> {
                for (int i = 0; i < meterList.size(); i++) meters.put(keys.get(i), meterList.get(i));
            }
            case READING -> {
                for (int i = 0; i < meterList.size(); i++) metersReading.put(keys.get(i), meterList.get(i));
            }
            case VALID -> {
                for (int i = 0; i < meterList.size(); i++) metersValid.put(keys.get(i), meterList.get(i));
            }
            case ALERT -> {
                for (int i = 0; i < meterList.size(); i++) metersAlert.put(keys.get(i), meterList.get(i));
            }
            case NO_INDEX -> {
                for (int i = 0; i < meterList.size(); i++) metersNoIndex.put(keys.get(i), meterList.get(i));
            }
        }
        threadJson(to);
        threadJson(from);
    }

    /**
     * Update a list of meters defined by a pair of serial and readType
     * @param keys pair of serial (SN) and read type (type de releve)
     * @param from UNREAD, READING, VALID, ALERT, NO_INDEX
     * @param to UNREAD, READING, VALID, ALERT, NO_INDEX
     */
    public void updatePairs(List<Pair<String, String>> keys, int from, int to) {
        List<Feature> meterList = new ArrayList<>();
        switch (from) {
            case UNREAD -> {
                for (Pair<String, String> k : keys) meterList.add(meters.remove(k));
            }
            case READING -> {
                for (Pair<String, String> k : keys) meterList.add(metersReading.remove(k));
            }
            case VALID -> {
                for (Pair<String, String> k : keys) meterList.add(metersValid.remove(k));
            }
            case ALERT -> {
                for (Pair<String, String> k : keys) meterList.add(metersAlert.remove(k));
            }
            case NO_INDEX -> {
                for (Pair<String, String> k : keys) meterList.add(metersNoIndex.remove(k));
            }
        }
        switch (to) {
            case UNREAD -> {
                for (int i = 0; i < meterList.size(); i++) meters.put(keys.get(i), meterList.get(i));
            }
            case READING -> {
                for (int i = 0; i < meterList.size(); i++) metersReading.put(keys.get(i), meterList.get(i));
            }
            case VALID -> {
                for (int i = 0; i < meterList.size(); i++) metersValid.put(keys.get(i), meterList.get(i));
            }
            case ALERT -> {
                for (int i = 0; i < meterList.size(); i++) metersAlert.put(keys.get(i), meterList.get(i));
            }
            case NO_INDEX -> {
                for (int i = 0; i < meterList.size(); i++) metersNoIndex.put(keys.get(i), meterList.get(i));
            }
        }
        threadJson(to);
        threadJson(from);
    }
}
