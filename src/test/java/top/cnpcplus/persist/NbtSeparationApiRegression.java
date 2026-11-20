package top.cnpcplus.persist;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import top.cnpcplus.api.NbtSeparationApi;

/** Public registration plus the production store's real IO; no running game or packet test. */
public final class NbtSeparationApiRegression {
    public static void main(String[] args) throws Exception {
        File world = Files.createTempDirectory(Path.of("Temp"), "nbt-api-").toFile();
        NbtSeparationApi api = NbtSeparationApi.register(new ResourceLocation("testaddon", "npc_data"), "TestAddonData");
        NbtSeparationApi otherApi = NbtSeparationApi.register(new ResourceLocation("otheraddon", "npc_data"), "OtherAddonData");
        var field = NbtSeparationApi.class.getDeclaredField("store");
        field.setAccessible(true);
        NpcDataExternalStore store = (NpcDataExternalStore) field.get(api);
        NpcDataExternalStore other = (NpcDataExternalStore) field.get(otherApi);
        File directory = new File(world, "data/cnpcplus/addons/testaddon/npc_data");
        File otherDirectory = new File(world, "data/cnpcplus/addons/otheraddon/npc_data");
        String reference = "CNPCPlusAddon:testaddon:npc_data:Id";
        UUID owner = UUID.randomUUID();
        Object identity = new Object();
        CompoundTag payload = new CompoundTag();
        payload.putString("Text", "NBT API 中文");
        payload.putIntArray("Values", new int[]{1, 2, 3});
        CompoundTag tag = new CompoundTag();
        tag.put("TestAddonData", payload.copy());
        tag.putString("OtherAddonData", "untouched");
        tag.putInt("Unrelated", 42);
        CompoundTag original = tag.copy();
        store.saveNpc(identity, owner, tag, directory);
        check(tag.contains(reference) && !tag.contains("TestAddonData"), "addon data not separated");
        check(tag.getString("OtherAddonData").equals("untouched") && tag.getInt("Unrelated") == 42, "foreign keys changed");
        check(NbtIo.readCompressed(new File(directory, tag.getString(reference) + ".dat"))
                .getCompound("Data").getCompound("TestAddonData").equals(payload), "disk payload mismatch");
        CompoundTag saved = tag.copy();
        other.saveNpc(new Object(), owner, tag, otherDirectory);
        check(!tag.contains("OtherAddonData") && tag.contains("CNPCPlusAddon:otheraddon:npc_data:Id"), "addon references overlap");
        check(store.prepareLoad(identity, owner, tag, directory), "reload failed");
        check(tag.getCompound("TestAddonData").equals(payload), "round trip mismatch");
        CompoundTag clone = saved.copy();
        check(store.prepareLoad(new Object(), UUID.randomUUID(), clone, directory)
                && !clone.getString(reference).equals(saved.getString(reference)), "clone did not fork");
        CompoundTag inline = saved.copy();
        inline.putString("TestAddonData", "newer-inline");
        check(store.makePortable(inline, directory) && inline.getString("TestAddonData").equals("newer-inline")
                && !inline.contains(reference), "portable export lost inline authority");
        CompoundTag empty = new CompoundTag();
        empty.put("TestAddonData", new CompoundTag());
        store.saveNpc(identity, owner, empty, directory);
        check(store.prepareLoad(identity, owner, empty, directory) && empty.getCompound("TestAddonData").isEmpty(), "empty payload resurrected");
        File blocked = new File(world, "blocked");
        Files.writeString(blocked.toPath(), "blocking file");
        CompoundTag failed = original.copy();
        store.saveNpc(new Object(), UUID.randomUUID(), failed, blocked);
        check(failed.equals(original), "failed save stripped inline data");
        File source = new File(directory, owner + ".dat");
        Files.writeString(source.toPath(), "corrupt");
        CompoundTag corrupt = saved.copy();
        check(!store.makePortable(corrupt, directory) && corrupt.equals(saved), "failed export removed reference");

        for (String path : new String[]{"../escape", "nested/../escape", "nested/./escape", "/absolute", "trailing/", "a//b"}) {
            reject(() -> NbtSeparationApi.register(new ResourceLocation("testaddon", path), "UnsafeData"));
        }
        reject(() -> NbtSeparationApi.register(new ResourceLocation("..", "escape"), "UnsafeData"));
        for (String key : new String[]{"", " ", "UUID", "ForgeData", "ForgeCaps", "Passengers", "TraderPages", "CNPCPlusInvId", "Armor", "PuppetHead", "TestAddonData"}) {
            reject(() -> NbtSeparationApi.register(new ResourceLocation("testaddon", "rejected"), key));
        }
        reject(() -> NbtSeparationApi.register(new ResourceLocation("testaddon", "empty")));
        boolean duplicateRejected = false;
        try { NbtSeparationApi.register(new ResourceLocation("testaddon", "npc_data"), "UnusedKey"); }
        catch (IllegalStateException expected) { duplicateRejected = true; }
        check(duplicateRejected, "duplicate store ID accepted");
        CompoundTag noServer = saved.copy();
        check(!api.makePortable(noServer) && noServer.equals(saved), "public export accessed disk without a server");
        api.saveNpc(null, noServer);
        check(!api.prepareLoad(null, noServer) && noServer.equals(saved), "null NPC mutated NBT");
        System.out.println("NBT_SEPARATION_API_REGRESSION=PASS (registration, real IO, isolation, forks, inline authority, empty data, failure retention, unsafe IDs/keys, no-server export; expected IO errors above)");
    }

    private static void reject(Runnable call) {
        try { call.run(); }
        catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("unsafe registration accepted");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
