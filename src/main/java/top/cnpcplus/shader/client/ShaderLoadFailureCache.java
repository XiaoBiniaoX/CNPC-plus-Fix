package top.cnpcplus.shader.client;

import java.util.HashSet;
import java.util.Set;

/** Failed GPU loads are retried only after an explicit reload or resource reload. */
final class ShaderLoadFailureCache {
    private final Set<String> ids = new HashSet<>();

    boolean contains(String id) { return ids.contains(id); }
    void add(String id) { ids.add(id); }
    void remove(String id) { ids.remove(id); }
    void clear() { ids.clear(); }
}
