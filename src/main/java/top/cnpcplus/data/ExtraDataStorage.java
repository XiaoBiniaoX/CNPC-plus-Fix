package top.cnpcplus.data;

import java.util.Map;
import java.util.WeakHashMap;

public class ExtraDataStorage {
    private static final Map<Object, float[]> floatData = new WeakHashMap<>();
    private static final Map<Object, Boolean> boolData = new WeakHashMap<>();

    public static synchronized float getFloat(Object key, int index) {
        float[] arr = floatData.get(key);
        return arr != null ? arr[index] : -1.0f;
    }

    public static synchronized void setFloat(Object key, int index, float value) {
        floatData.computeIfAbsent(key, k -> new float[10])[index] = value;
    }

    public static synchronized boolean getBool(Object key) {
        return boolData.getOrDefault(key, false);
    }

    public static synchronized void setBool(Object key, boolean value) {
        boolData.put(key, value);
    }
}