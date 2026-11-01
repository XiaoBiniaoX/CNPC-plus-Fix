package top.cnpcplus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import top.cnpcplus.data.NpcHitboxStore;

/** Production dimension/NBT logic without constructing Minecraft or running a client. */
public final class HitboxRegression {
    public static void main(String[] args) {
        EntityDimensions nativeSize = EntityDimensions.scalable(0.6f, 1.8f);
        check(NpcHitboxStore.AUTO.dimensions(nativeSize, 5) == nativeSize, "old NPC dimensions changed");
        NpcHitboxStore.Settings settings = new NpcHitboxStore.Settings(3, 2, 4);
        EntityDimensions size = settings.dimensions(nativeSize, 5);
        box(size.makeBoundingBox(10, 20, 30), 2, 4, 3, 10, 20, 30);
        check(size.width == 3, "vanilla search/navigation width is not conservative");
        box(settings.dimensions(nativeSize, 10).makeBoundingBox(1, 2, 3), 4, 8, 6, 1, 2, 3);
        box(size.scale(2).makeBoundingBox(1, 2, 3), 4, 8, 6, 1, 2, 3);
        box(size.scale(2, 3).makeBoundingBox(1, 2, 3), 4, 12, 6, 1, 2, 3);
        check(settings.dimensions(size, 10) == size, "super/subclass applied dimensions twice");
        EntityDimensions corpse = EntityDimensions.scalable(1.0E-5f, 0.4f);
        check(settings.dimensions(corpse, 5) == corpse, "custom hitbox expanded disabled/corpse box");
        EntityDimensions partial = new NpcHitboxStore.Settings(3, 0, 0).dimensions(nativeSize, 5);
        box(partial.makeBoundingBox(0, 0, 0), 0.6, 1.8, 3, 0, 0, 0);
        // EntityCustomNpc scales a Gecko model's dimensions before our final return hook.
        EntityDimensions gecko = EntityDimensions.scalable(1.4f, 3.2f).scale(2);
        check(NpcHitboxStore.AUTO.dimensions(gecko, 10) == gecko, "automatic Gecko model dimensions changed");
        box(new NpcHitboxStore.Settings(3, 0, 0).dimensions(gecko, 10).makeBoundingBox(0, 0, 0),
                2.8, 6.4, 6, 0, 0, 0);
        box(settings.dimensions(gecko, 10).makeBoundingBox(0, 0, 0), 4, 8, 6, 0, 0, 0);
        check(gecko.width == 2.8f && gecko.height == 6.4f, "host settings mutated Gecko model dimensions");
        check(new NpcHitboxStore.Settings(Float.NaN, -3, Float.POSITIVE_INFINITY).equals(NpcHitboxStore.AUTO), "unsafe values accepted");
        check(new NpcHitboxStore.Settings(200, 0, 0).length() == NpcHitboxStore.MAX, "limit missing");

        CompoundTag nbt = new CompoundTag();
        nbt.put("Unrelated", new CompoundTag());
        settings.write(nbt);
        check(settings.equals(NpcHitboxStore.Settings.read(nbt.copy())), "three-axis NBT round trip failed");
        nbt.copy().getCompound(NpcHitboxStore.KEY).putFloat("Length", 99);
        check(settings.equals(NpcHitboxStore.Settings.read(nbt)), "copy mutated source hitbox");
        check(NpcHitboxStore.AUTO.equals(NpcHitboxStore.Settings.read(new CompoundTag())), "legacy NBT not automatic");
        NpcHitboxStore.AUTO.write(nbt);
        check(!nbt.contains(NpcHitboxStore.KEY) && nbt.contains("Unrelated"), "automatic reset changed unrelated NBT");
        for (int i = 0; i <= 4; i++) {
            NpcHitboxStore.Settings shadow = new NpcHitboxStore.Settings(0, 0, 0, i * 0.25f);
            shadow.write(nbt);
            check(shadow.equals(NpcHitboxStore.Settings.read(nbt.copy())), "shadow percentage NBT failed");
            check(shadow.dimensions(nativeSize, 10) == nativeSize, "shadow-only setting changed hitbox");
        }
        settings.write(nbt);
        nbt.getCompound(NpcHitboxStore.KEY).remove("ShadowScale");
        check(NpcHitboxStore.Settings.read(nbt).shadowScale() == 1, "old hitbox NBT hid shadow");
        check(new NpcHitboxStore.Settings(3, 2, 4, Float.NaN).shadowScale() == 1, "NaN shadow accepted");
        check(new NpcHitboxStore.Settings(3, 2, 4, -1).shadowScale() == 0
                && new NpcHitboxStore.Settings(3, 2, 4, 2).shadowScale() == 1, "shadow limits missing");
        System.out.println("HITBOX_REGRESSION=PASS");
    }

    private static void box(AABB box, double width, double height, double length, double x, double y, double z) {
        close(box.getXsize(), width);
        close(box.getYsize(), height);
        close(box.getZsize(), length);
        close((box.minX + box.maxX) / 2, x);
        close(box.minY, y);
        close((box.minZ + box.maxZ) / 2, z);
    }

    private static void close(double actual, double expected) {
        check(Math.abs(actual - expected) < 1.0E-6, "box mismatch: " + actual + " != " + expected);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
