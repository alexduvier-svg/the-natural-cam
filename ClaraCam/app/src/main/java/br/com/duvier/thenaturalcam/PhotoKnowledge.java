package br.com.duvier.thenaturalcam;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On-demand, offline reference memory. Does not change any camera or editor parameter.
 * Call load/readGuide on a worker thread. No Context, photo or fifty-million-row table is retained.
 */
public final class PhotoKnowledge {
    public static final String VERSION = "1.0.0";
    public static final String MANIFEST = "knowledge/photo_memory_v1.json";
    public static final String GUIDE = "knowledge/diretriz_fotografica_50_milhoes.md";
    private static PhotoKnowledge cached;
    private final JSONObject catalog;

    private PhotoKnowledge(JSONObject catalog) throws JSONException {
        this.catalog = catalog;
        validate();
    }

    public static synchronized PhotoKnowledge load(Context context) throws IOException {
        if (cached == null) {
            try {
                cached = new PhotoKnowledge(new JSONObject(readAsset(context, MANIFEST)));
            } catch (JSONException e) {
                throw new IOException("Invalid photographic reference memory", e);
            }
        }
        return cached;
    }

    public static String readGuide(Context context) throws IOException {
        return readAsset(context, GUIDE);
    }

    public long scenarioCount() { return PhotoScenarioSpace.COUNT; }

    /** Names are stable machine-readable IDs, interpreted by the accompanying Portuguese guide. */
    public Map<String, String> labels(long scenarioId) {
        int[] indices = PhotoScenarioSpace.decode(scenarioId);
        Map<String, String> labels = new LinkedHashMap<>();
        try {
            JSONArray axes = catalog.getJSONArray("axes");
            for (int i = 0; i < indices.length; i++) {
                labels.put(PhotoScenarioSpace.axisName(i),
                        axes.getJSONObject(i).getJSONArray("values").getString(indices[i]));
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Validated catalog changed", e);
        }
        return Collections.unmodifiableMap(labels);
    }

    /** All applicable references, in descending priority. A UI should select at most three useful notices. */
    public List<String> advice(long scenarioId) {
        return adviceForKnownAxes(labels(scenarioId));
    }

    /** Missing observations stay unknown: conditional rules require ALL their axes to be known. */
    public List<String> adviceForKnownAxes(Map<String, String> scene) {
        List<JSONObject> matches = new ArrayList<>();
        try {
            JSONArray rules = catalog.getJSONArray("rules");
            for (int i = 0; i < rules.length(); i++) {
                JSONObject rule = rules.getJSONObject(i);
                JSONObject when = rule.getJSONObject("when");
                boolean match = true;
                for (java.util.Iterator<String> conditionKeys = when.keys(); conditionKeys.hasNext();) {
                    String axis = conditionKeys.next();
                    if (!contains(when.getJSONArray(axis), scene.get(axis))) {
                        match = false;
                        break;
                    }
                }
                if (match) matches.add(rule);
            }
            matches.sort((a, b) -> Integer.compare(b.optInt("priority"), a.optInt("priority")));
            List<String> result = new ArrayList<>();
            for (JSONObject rule : matches)
                result.add(rule.getString("id") + ": " + rule.getString("guideline"));
            return Collections.unmodifiableList(result);
        } catch (JSONException e) {
            throw new IllegalStateException("Validated catalog changed", e);
        }
    }

    private void validate() throws JSONException {
        if (!"tnc.photo-memory.v1".equals(catalog.getString("id"))
                || !VERSION.equals(catalog.getString("version"))
                || !"reference_only".equals(catalog.getString("authority"))
                || catalog.getBoolean("automatic_capture_changes")
                || !GUIDE.equals(catalog.getString("guide_asset"))
                || !"mixed_radix_zero_based_axis_order".equals(catalog.getString("encoding"))
                || catalog.getLong("scenario_count") != PhotoScenarioSpace.COUNT)
            throw new JSONException("Incompatible memory contract");
        JSONArray axes = catalog.getJSONArray("axes");
        if (axes.length() != PhotoScenarioSpace.axisCount()) throw new JSONException("Invalid axes");
        Map<String, JSONArray> valuesByAxis = new LinkedHashMap<>();
        long count = 1;
        for (int i = 0; i < axes.length(); i++) {
            JSONObject axis = axes.getJSONObject(i);
            String name = PhotoScenarioSpace.axisName(i);
            JSONArray values = axis.getJSONArray("values");
            if (!name.equals(axis.getString("id")) || values.length() != PhotoScenarioSpace.axisSize(i))
                throw new JSONException("Incompatible axis: " + name);
            Set<String> unique = new HashSet<>();
            for (int j = 0; j < values.length(); j++) {
                String value = values.getString(j);
                if (value.isEmpty() || !unique.add(value)) throw new JSONException("Invalid axis value");
            }
            valuesByAxis.put(name, values);
            count *= values.length();
        }
        if (count != PhotoScenarioSpace.COUNT) throw new JSONException("Invalid scenario count");
        JSONArray rules = catalog.getJSONArray("rules");
        if (rules.length() == 0) throw new JSONException("Missing rules");
        Set<String> ruleIds = new HashSet<>();
        for (int i = 0; i < rules.length(); i++) {
            JSONObject rule = rules.getJSONObject(i);
            String id = rule.getString("id");
            if (id.isEmpty() || !ruleIds.add(id) || rule.getString("guideline").isEmpty()
                    || rule.getInt("priority") < 0 || rule.getInt("priority") > 100)
                throw new JSONException("Invalid rule");
            JSONObject when = rule.getJSONObject("when");
            for (java.util.Iterator<String> conditionKeys = when.keys(); conditionKeys.hasNext();) {
                String axis = conditionKeys.next();
                JSONArray allowed = valuesByAxis.get(axis);
                JSONArray candidates = when.getJSONArray(axis);
                if (allowed == null || candidates.length() == 0) throw new JSONException("Invalid condition");
                for (int j = 0; j < candidates.length(); j++)
                    if (!contains(allowed, candidates.getString(j))) throw new JSONException("Unknown value");
            }
        }
    }

    private static boolean contains(JSONArray array, String value) throws JSONException {
        for (int i = 0; i < array.length(); i++) if (array.getString(i).equals(value)) return true;
        return false;
    }

    private static String readAsset(Context context, String name) throws IOException {
        try (InputStream in = context.getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                if (out.size() + n > 262_144) throw new IOException("Reference asset exceeds 256 KiB");
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
