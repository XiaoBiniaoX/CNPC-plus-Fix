package top.cnpcplus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import noppes.npcs.controllers.data.TransportCategory;
import noppes.npcs.controllers.data.TransportLocation;
import top.cnpcplus.transport.TransportLocationIndex;

public final class TransportRemovalRegression {
    public static void main(String[] args) throws Exception {
        Map<Integer, TransportLocation> global = new LinkedHashMap<>();
        Map<Integer, TransportCategory> categories = new HashMap<>();
        TransportCategory first = new TransportCategory(), second = new TransportCategory();
        first.id = 1;
        second.id = 2;
        categories.put(1, first);
        categories.put(2, second);
        TransportLocation old = location(7, first), moved = location(7, second), unloaded = location(8, first);
        TransportLocationIndex.put(global, categories, old);
        TransportLocationIndex.put(global, categories, unloaded);
        TransportLocationIndex.put(global, categories, moved);
        check(!first.locations.containsKey(7) && second.locations.get(7) == moved && global.get(7) == moved,
                "cross-category save retained duplicate");
        first.locations.put(7, old); // Reproduce a world already damaged by the native path.
        check(TransportLocationIndex.remove(global, categories, 7) == moved, "did not return global location");
        check(!global.containsKey(7) && !first.locations.containsKey(7) && !second.locations.containsKey(7), "residual index survived");
        check(TransportLocationIndex.remove(global, categories, 7) == null, "reentrant/repeated delete failed");
        first.locations.put(7, old); // Category-only residue was previously undeletable until restart.
        check(TransportLocationIndex.remove(global, categories, 7) == old && !first.locations.containsKey(7), "category-only deletion failed");
        check(global.get(8) == unloaded && first.locations.get(8) == unloaded, "unrelated/unloaded NPC point removed");
        java.util.UUID owner = java.util.UUID.randomUUID(), clone = java.util.UUID.randomUUID();
        check(top.cnpcplus.transport.TransportOwnership.owns(owner, owner), "owner not recognized");
        check(!top.cnpcplus.transport.TransportOwnership.owns(owner, clone)
                && !top.cnpcplus.transport.TransportOwnership.owns(null, owner), "clone/unknown owner can delete original");
        System.out.println("TRANSPORT_REMOVAL_REGRESSION=PASS");
    }

    private static TransportLocation location(int id, TransportCategory category) throws Exception {
        // The dependency constructor uses SRG fields; JavaExec is not a ModLauncher runtime.
        java.lang.reflect.Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        TransportLocation location = (TransportLocation) ((sun.misc.Unsafe) field.get(null))
                .allocateInstance(TransportLocation.class);
        location.id = id;
        location.category = category;
        return location;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
