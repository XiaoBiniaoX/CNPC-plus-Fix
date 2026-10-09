package top.cnpcplus.perf.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/** 不强引用已淘汰的模型实例。调用者保证注册和回收在渲染线程。 */
public final class WeakInvalidationRegistry {
    public interface Resettable { void cnpcyouhua$resetRenderCache(); }
    private final Set<Resettable> models = Collections.newSetFromMap(new WeakHashMap<>());
    public synchronized void register(Resettable model) { models.add(model); }
    public void resetAll() {
        RuntimeException first = null;
        ArrayList<Resettable> snapshot;
        synchronized (this) { snapshot = new ArrayList<>(models); }
        for (Resettable model : snapshot) {
            try { model.cnpcyouhua$resetRenderCache(); }
            catch (RuntimeException e) { if (first == null) first = e; else first.addSuppressed(e); }
        }
        if (first != null) throw first;
    }
}
