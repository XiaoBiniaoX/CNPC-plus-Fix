package top.cnpcplus.persist;

import java.io.File;
import java.nio.file.Files;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** Real compressed-NBT IO, clone isolation and failure retention, without a Minecraft client. */
public final class ExternalDataRegression {
    public static void main(String[] args) throws Exception {
        File root = new File(args[0], UUID.randomUUID().toString());
        NpcDataExternalStore store = NpcDataExternalStore.INVENTORY;
        Object original = new Object(), clone = new Object();
        UUID owner = UUID.randomUUID(), cloneOwner = UUID.randomUUID();
        CompoundTag full = equipment();
        CompoundTag saved = full.copy();
        store.saveNpc(original, owner, saved, root);
        check(saved.contains("CNPCPlusInvId") && !saved.contains("Weapons"), "not externalized");
        check(saved.getInt("Unrelated") == 42, "unrelated data changed");
        String originalId = saved.getString("CNPCPlusInvId");

        CompoundTag loaded = saved.copy();
        check(store.prepareLoad(original, owner, loaded, root), "cannot load saved blob");
        check(loaded.getList("Weapons", 10).equals(full.getList("Weapons", 10)), "item Tag/ForgeCaps lost");
        CompoundTag cloned = saved.copy();
        check(store.prepareLoad(clone, cloneOwner, cloned, root), "clone cannot load");
        String cloneId = cloned.getString("CNPCPlusInvId");
        check(!cloneId.equals(originalId), "clone shares source id");
        cloned.getList("Weapons", 10).getCompound(0).getCompound("tag").putString("GunId", "edited-clone");
        store.saveNpc(clone, cloneOwner, cloned, root);
        CompoundTag fresh = full.copy(); // Entity save starts with a fresh CompoundTag each time.
        store.saveNpc(clone, cloneOwner, fresh, root);
        check(fresh.getString("CNPCPlusInvId").equals(cloneId), "fresh save lost stable fork id");
        CompoundTag sourceAgain = saved.copy();
        store.prepareLoad(new Object(), owner, sourceAgain, root);
        check(sourceAgain.getList("Weapons", 10).equals(full.getList("Weapons", 10)), "clone overwrote source");

        CompoundTag portable = saved.copy();
        check(store.makePortable(portable, root), "portable export failed");
        check(!portable.contains("CNPCPlusInvId") && portable.getList("Weapons", 10).equals(full.getList("Weapons", 10)), "portable snapshot incomplete");
        portable.getList("Weapons", 10).getCompound(0).getCompound("ForgeCaps").putInt("Ammo", 0);
        check(sourceAgain.getList("Weapons", 10).getCompound(0).getCompound("ForgeCaps").getInt("Ammo") == 17, "portable snapshot aliased");

        File legacyDir = new File(root, "legacy");
        NpcDataExternalStore old = new NpcDataExternalStore("legacy", "CNPCPlusInvId", "CNPCPlusInvState",
                key -> key.equals("NpcInv") || key.equals("DropChance"), tag -> !tag.isEmpty());
        CompoundTag legacy = full.copy();
        old.saveNpc(new Object(), owner, legacy, legacyDir);
        store.prepareLoad(new Object(), owner, legacy, legacyDir);
        check(legacy.getList("Armor", 10).equals(full.getList("Armor", 10))
                && legacy.getList("Weapons", 10).equals(full.getList("Weapons", 10)), "old drops blob erased inline equipment");

        File denied = new File(root, "not-a-directory");
        Files.writeString(denied.toPath(), "blocking file");
        CompoundTag failedSave = full.copy();
        store.saveNpc(new Object(), UUID.randomUUID(), failedSave, denied);
        check(failedSave.equals(full), "write failure stripped NBT");
        File corruptDir = new File(root, "corrupt");
        Files.createDirectories(corruptDir.toPath());
        UUID corruptOwner = UUID.randomUUID();
        File corrupt = new File(corruptDir, corruptOwner + ".dat");
        Files.writeString(corrupt.toPath(), "corrupt blob");
        CompoundTag retained = full.copy();
        store.prepareLoad(new Object(), corruptOwner, retained, corruptDir);
        store.saveNpc(new Object(), corruptOwner, retained, corruptDir);
        check(retained.equals(full) && Files.readString(corrupt.toPath()).equals("corrupt blob"), "corrupt file overwritten or inline lost");
        failureReloadAndActive(store, full, root);
        scriptsAndEmpty(full, root);

        CompoundTag model = new CompoundTag();
        CompoundTag part = new CompoundTag();
        part.putFloat("ScaleX", 1.25f);
        part.putBoolean("Dismembered", true);
        model.put("NpcModelData", part.copy());
        model.put("PuppetHead2", part.copy());
        File models = new File(root, "model");
        NpcDataExternalStore.MODEL.saveNpc(new Object(), owner, model, models);
        check(NpcDataExternalStore.MODEL.makePortable(model, models), "model export failed");
        check(model.getCompound("NpcModelData").equals(part) && model.getCompound("PuppetHead2").equals(part), "model/puppet extension lost");
        System.out.println("EXTERNAL_DATA_REGRESSION=PASS (expected IO errors above)");
    }

    private static CompoundTag equipment() {
        CompoundTag tag = new CompoundTag();
        ListTag weapons = new ListTag(), armor = new ListTag();
        for (int slot = 0; slot < 3; slot++) {
            CompoundTag stack = new CompoundTag(), data = new CompoundTag(), caps = new CompoundTag();
            stack.putInt("Slot", slot);
            stack.putString("id", "tacz:modern_kinetic_gun");
            stack.putByte("Count", (byte) 1);
            data.putString("GunId", "configured-" + slot);
            caps.putInt("Ammo", 17);
            stack.put("tag", data);
            stack.put("ForgeCaps", caps);
            weapons.add(stack);
        }
        for (int slot = 0; slot < 4; slot++) {
            CompoundTag stack = new CompoundTag();
            stack.putInt("Slot", slot);
            stack.putString("id", "minecraft:diamond_helmet");
            stack.put("tag", weapons.getCompound(0).getCompound("tag").copy());
            armor.add(stack);
        }
        tag.put("Weapons", weapons);
        tag.put("Armor", armor);
        tag.put("NpcInv", weapons.copy());
        tag.putInt("Unrelated", 42);
        return tag;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void failureReloadAndActive(NpcDataExternalStore store, CompoundTag full, File root) throws Exception {
        File directory = new File(root, "active");
        UUID owner = UUID.randomUUID();
        Object identity = new Object();
        CompoundTag saved = full.copy();
        store.saveNpc(identity, owner, saved, directory);
        File file = new File(directory, saved.getString("CNPCPlusInvId") + ".dat");
        // A fresh entity save after failed IO leaves newer inline fields beside the old pointer.
        CompoundTag newer = saved.copy();
        newer.put("Weapons", full.getList("Weapons", 10).copy());
        newer.getList("Weapons", 10).getCompound(0).getCompound("tag").putString("GunId", "newer-inline");
        store.prepareLoad(identity, owner, newer, directory);
        check(newer.getList("Weapons", 10).getCompound(0).getCompound("tag").getString("GunId").equals("newer-inline"),
                "old external blob overwrote newer inline value");
        check(newer.getList("Armor", 10).equals(full.getList("Armor", 10)), "missing keys not supplemented");
        CompoundTag exported = saved.copy();
        exported.put("Weapons", newer.getList("Weapons", 10).copy());
        check(store.makePortable(exported, directory)
                && exported.getList("Weapons", 10).equals(newer.getList("Weapons", 10)), "portable overwrote inline");

        Files.writeString(file.toPath(), "active-corrupted");
        CompoundTag current = full.copy();
        store.saveNpc(identity, owner, current, directory);
        check(current.equals(full) && Files.readString(file.toPath()).equals("active-corrupted"),
                "ACTIVE fast path ignored file corruption");
        CompoundTag failedPortable = saved.copy();
        failedPortable.put("Weapons", full.getList("Weapons", 10).copy());
        CompoundTag beforeExport = failedPortable.copy();
        check(!store.makePortable(failedPortable, directory) && failedPortable.equals(beforeExport),
                "failed portable export stripped pointer/inline data");
        Files.delete(file.toPath()); // Only the self-created fixture file, not user data.
        current = full.copy();
        store.saveNpc(identity, owner, current, directory);
        check(current.equals(full) && !file.exists(), "missing ACTIVE blob silently recreated/stripped");

        // Even valid gzip is invalid if its Data field is not a compound.
        CompoundTag invalid = new CompoundTag();
        invalid.putString("Owner", owner.toString());
        invalid.putString("Data", "wrong type");
        NbtIo.writeCompressed(invalid, file);
        current = full.copy();
        store.prepareLoad(new Object(), owner, current, directory);
        check(current.equals(full), "invalid Data type accepted");
    }

    private static void scriptsAndEmpty(CompoundTag full, File root) throws Exception {
        File directory = new File(root, "scripts");
        UUID owner = UUID.randomUUID();
        Object identity = new Object();
        CompoundTag scripts = new CompoundTag(), container = new CompoundTag();
        container.putString("Script", "function damaged(e){\n哈哈哈\n}");
        container.putString("CNPCPlusDiagnostic", "opaque extension");
        ListTag list = new ListTag();
        list.add(container);
        scripts.put("Scripts", list);
        scripts.putString("ScriptLanguage", "ECMAScript");
        scripts.putBoolean("ScriptEnabled", true);
        CompoundTag original = scripts.copy();
        NpcDataExternalStore store = NpcDataExternalStore.SCRIPTS;
        store.saveNpc(identity, owner, scripts, directory);
        check(scripts.contains(NpcScriptExternalStore.ID_KEY) && !scripts.contains("Scripts"), "script delegate keys changed");
        check(store.prepareLoad(identity, owner, scripts, directory) && scripts.getList("Scripts", 10).equals(list),
                "legacy script format/diagnostic lost");
        CompoundTag empty = new CompoundTag();
        empty.put("Scripts", new ListTag());
        empty.putString("ScriptLanguage", "ECMAScript");
        empty.putBoolean("ScriptEnabled", false);
        store.saveNpc(identity, owner, empty, directory);
        store.prepareLoad(new Object(), owner, empty, directory);
        check(empty.getList("Scripts", 10).isEmpty() && !empty.getBoolean("ScriptEnabled"), "legitimate empty scripts resurrected");

        File legacyDir = new File(root, "legacy-scripts");
        store.saveNpc(new Object(), owner, original, legacyDir);
        CompoundTag clone = original.copy();
        check(store.prepareLoad(new Object(), UUID.randomUUID(), clone, legacyDir)
                && !clone.getString(NpcScriptExternalStore.ID_KEY).equals(original.getString(NpcScriptExternalStore.ID_KEY)),
                "script clone shared external ID");
        store.makePortable(clone, legacyDir);
        check(!clone.contains(NpcScriptExternalStore.ID_KEY) && clone.getList("Scripts", 10).equals(list), "script clone incomplete");

        // Broad prefixes must never capture the store's own pointer keys.
        NpcDataExternalStore broad = new NpcDataExternalStore("broad", "DataId", "DataState", key -> true, tag -> !tag.isEmpty());
        CompoundTag input = full.copy();
        File broadDir = new File(root, "broad");
        broad.saveNpc(new Object(), owner, input, broadDir);
        CompoundTag disk = NbtIo.readCompressed(new File(broadDir, input.getString("DataId") + ".dat"));
        check(!disk.getCompound("Data").contains("DataId") && !disk.getCompound("Data").contains("DataState"), "pointer captured as data");
    }
}
