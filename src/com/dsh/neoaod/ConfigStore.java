package io.local.neoaod.typeguard;

import android.content.ContentResolver;
import android.provider.Settings;
import org.json.JSONObject;

public final class ConfigStore {
    public static final String KEY = "neo9_aod_schedule_config";
    public static final String LEGACY_KEY = "neo9_aod_brightness_percent";
    private ConfigStore() {}
    public static Schedule read(ContentResolver resolver) {
        int legacy = Settings.Secure.getInt(resolver, LEGACY_KEY, 60);
        try {
            String raw = Settings.Secure.getString(resolver, KEY);
            return raw == null ? Schedule.defaults(legacy) : decode(raw);
        } catch (Exception e) {
            android.util.Log.w("NeoAODBrightness", "Invalid configuration; using manual fallback", e);
            return Schedule.defaults(legacy);
        }
    }
    public static Schedule decode(String raw) throws Exception {
        JSONObject o = new JSONObject(raw);
        if (o.getInt("version") != 1) throw new IllegalArgumentException("不支持的配置版本");
        return new Schedule(o.getInt("manual"), rule(o.getJSONObject("first")), rule(o.getJSONObject("second")));
    }
    private static Schedule.Rule rule(JSONObject o) throws Exception {
        return new Schedule.Rule(o.getBoolean("enabled"), o.getInt("start"), o.getInt("end"), o.getInt("percent"));
    }
    public static String encode(Schedule s) {
        try {
            return new JSONObject().put("version", 1).put("manual", s.manual)
                    .put("first", encodeRule(s.first)).put("second", encodeRule(s.second)).toString();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static JSONObject encodeRule(Schedule.Rule r) throws Exception {
        return new JSONObject().put("enabled", r.enabled).put("start", r.start).put("end", r.end).put("percent", r.percent);
    }
    public static boolean save(ContentResolver resolver, Schedule s) {
        // One settings write commits both rules and manual brightness atomically.
        return Settings.Secure.putString(resolver, KEY, encode(s));
    }
}
