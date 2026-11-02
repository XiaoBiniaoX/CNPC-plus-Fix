package top.cnpcplus.persist;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import noppes.npcs.controllers.data.DataTransform;

/** Uses the real arena NPC and old external blobs, only in a new isolated evidence directory. */
public final class ArenaMigrationRegression {
    public static void main(String[] args) throws Exception {
        File evidence = new File(args[0]);
        File root = new File(args.length > 1 ? new File(args[1]) : evidence, "migration-regression/" + UUID.randomUUID());
        CompoundTag original = NbtIo.readCompressed(new File(evidence, "target-npc-00.nbt"));
        UUID owner = original.getUUID("UUID");
        check(original.getCompound("TransformInv").getList("Weapons", 10).size() == 3,
                "fixture does not contain the real three-slot equipment snapshot");
        check(original.getCompound("TransformDisplay").contains("ModelData")
                && original.getCompound("TransformJob").contains("PuppetBody"), "real nested model/puppet missing");
        for (String category : new String[]{"inventory", "model", "advanced", "scripts"}) {
            File directory = new File(root, category);
            Files.createDirectories(directory.toPath());
            Files.copy(new File(evidence, "original-stores/" + category + "/" + owner + ".dat").toPath(),
                    new File(directory, owner + ".dat").toPath());
        }

        Object npc = new Object();
        CompoundTag restored = original.copy();
        NpcDataExternalStore[] stores = {NpcDataExternalStore.MODEL, NpcDataExternalStore.INVENTORY,
                NpcDataExternalStore.ADVANCED, NpcDataExternalStore.SCRIPTS};
        String[] categories = {"model", "inventory", "advanced", "scripts"};
        for (int i = 0; i < stores.length; i++) stores[i].prepareLoad(npc, owner, restored, new File(root, categories[i]));
        for (String key : new String[]{"TransformInv", "TransformDisplay", "TransformJob", "TransformAdvanced", "TransformRole"}) {
            check(restored.get(key).equals(original.get(key)), "migration modified real snapshot " + key);
        }

        // Native DataTransform.readOptions initializes newly enabled snapshots from the NPC.
        // This fixture already enables all snapshots, so initialize those flags as an existing NPC would.
        DataTransform transform = new DataTransform(null);
        transform.hasInv = transform.hasDisplay = transform.hasJob = transform.hasRole = true;
        transform.hasAdvanced = transform.hasAi = transform.hasStats = true;
        transform.readToNBT(restored);
        CompoundTag saved = restored.copy();
        transform.save(saved);
        for (int i = 0; i < stores.length; i++) stores[i].saveNpc(npc, owner, saved, new File(root, categories[i]));
        check(!saved.contains("TransformInv") && !saved.contains("TransformDisplay") && !saved.contains("TransformJob")
                && !saved.contains("TransformAdvanced") && !saved.contains("TransformRole"), "nested snapshots remain inline");
        check(saved.getBoolean("TransformIsActive") == original.getBoolean("TransformIsActive")
                && saved.get("TransformAI").equals(original.get("TransformAI"))
                && saved.get("TransformStats").equals(original.get("TransformStats")), "unrelated transform state changed");
        for (int i = 0; i < stores.length; i++) stores[i].prepareLoad(npc, owner, saved, new File(root, categories[i]));
        check(saved.equals(restored), "real complete load-save-reload did not round trip");

        CompoundTag portable = saved.copy();
        for (int i = 0; i < stores.length; i++) check(stores[i].makePortable(portable, new File(root, categories[i])), "portable failed");
        portable.remove("UUID");
        UUID cloneOwner = UUID.randomUUID();
        Object clone = new Object();
        for (int i = 0; i < stores.length; i++) stores[i].saveNpc(clone, cloneOwner, portable, new File(root, categories[i]));
        for (int i = 0; i < stores.length; i++) stores[i].prepareLoad(clone, cloneOwner, portable, new File(root, categories[i]));
        portable.getCompound("TransformJob").getCompound("PuppetBody").putFloat("RotationX", 99);
        portable.getCompound("TransformDisplay").getCompound("ModelData").putString("CloneOnly", "changed");
        portable.getCompound("TransformInv").getList("Weapons", 10).getCompound(0).putString("CloneOnly", "changed");
        for (int i = 0; i < stores.length; i++) stores[i].saveNpc(clone, cloneOwner, portable, new File(root, categories[i]));
        CompoundTag sourceAgain = original.copy();
        for (int i = 0; i < stores.length; i++) stores[i].prepareLoad(new Object(), owner, sourceAgain, new File(root, categories[i]));
        check(sourceAgain.equals(restored), "clone changed original arena snapshots");
        System.out.println("ARENA_MIGRATION_REGRESSION=PASS (real NBT + native DataTransform + independent clone)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
