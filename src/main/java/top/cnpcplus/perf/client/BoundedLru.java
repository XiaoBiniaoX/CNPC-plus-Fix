package top.cnpcplus.perf.client;

import java.util.LinkedHashMap;

/** Render-thread-owned LRU. Eviction never removes source data. */
public final class BoundedLru<K, V> {
    private final LinkedHashMap<K, V> entries = new LinkedHashMap<>(16, 0.75f, true);
    private int capacity;
    public BoundedLru(int capacity) { setCapacity(capacity); }
    public V get(K key) { return entries.get(key); }
    public void put(K key, V value) { entries.put(key, value); trim(); }
    public void clear() { entries.clear(); }
    public int size() { return entries.size(); }
    public void setCapacity(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
        trim();
    }
    private void trim() {
        while (entries.size() > capacity) {
            var it = entries.keySet().iterator();
            it.next(); it.remove();
        }
    }
}
