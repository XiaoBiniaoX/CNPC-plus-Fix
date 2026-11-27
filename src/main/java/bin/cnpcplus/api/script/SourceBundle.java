package bin.cnpcplus.api.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Immutable source snapshot; sourceId is unique within the bundle. */
public final class SourceBundle {
    private final SourceUnit main;
    private final List<SourceUnit> linked;
    private final long revision;

    public SourceBundle(SourceUnit main, List<SourceUnit> linked, long revision) {
        if (main == null) throw new IllegalArgumentException("main");
        List<SourceUnit> copy = new ArrayList<>(linked == null ? Collections.<SourceUnit>emptyList() : linked);
        Set<String> ids = new HashSet<>();
        ids.add(main.sourceId());
        for (SourceUnit unit : copy) if (unit == null || !ids.add(unit.sourceId())) throw new IllegalArgumentException("Duplicate or null source unit");
        this.main = main;
        this.linked = Collections.unmodifiableList(copy);
        this.revision = revision;
    }

    public SourceUnit main() { return main; }
    public List<SourceUnit> linked() { return linked; }
    public long revision() { return revision; }
}
