package top.cnpcplus.clone;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import net.minecraft.nbt.*;
import top.cnpcplus.persist.NpcDataExternalStore;

/** Read-only display snapshots; never load an entity's saved scripts. */
public final class ClonePreviewData {
    public static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    private static final Set<String> DISPLAY = Set.of("Name", "Title", "Texture", "SkinUrl", "CloakTexture", "GlowTexture",
            "OverlayGlowing", "ShowLayers", "UsingSkinUrl", "SkinUsername", "Size", "SkinColor", "IsStatue", "LineColors",
            "StandingState", "MovingState", "Orientation", "MaxHealth", "Armor", "Weapons", "CNPCPlusHitbox");
    private ClonePreviewData() {}

    public static boolean validSelection(int tab, String name) {
        if (tab < 1 || tab > 9 || name == null || name.isBlank() || name.length() > 256
                || name.equals(".") || name.equals("..") || name.endsWith(".") || name.endsWith(" ")) return false;
        for (int i = 0; i < name.length(); i++) if (name.charAt(i) < 32 || "\\/:*?\"<>|".indexOf(name.charAt(i)) >= 0) return false;
        String base = name.split("\\.", 2)[0].toUpperCase(java.util.Locale.ROOT);
        return !base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]");
    }

    public static Path cloneFile(File clones, int tab, String name) throws IOException {
        if (!validSelection(tab, name)) throw new IOException("invalid clone selection");
        Path root = clones.toPath().toRealPath();
        if (!root.getParent().equals(clones.toPath().toAbsolutePath().getParent().toRealPath()))
            throw new IOException("clone directory outside expected parent");
        Path folder = root.resolve(Integer.toString(tab)).toRealPath();
        Path file = folder.resolve(name + ".json").toRealPath();
        if (!folder.getParent().equals(root) || !file.getParent().equals(folder) || !Files.isRegularFile(file))
            throw new IOException("clone outside selected tab");
        return file;
    }

    public static byte[] read(File clones, File world, int tab, String name) {
        try {
            Path file = cloneFile(clones, tab, name);
            CompoundTag saved = readSaved(file.toFile());
            if (saved.contains("Passengers") && !saved.getString("id").startsWith("customnpcs:")) {
                var candidates = new java.util.ArrayList<CompoundTag>();
                candidates.add(saved);
                saved = null;
                for (int i = 0; i < candidates.size(); i++) {
                    CompoundTag member = candidates.get(i);
                    if (member.getString("id").startsWith("customnpcs:")) { saved = member; break; }
                    for (Tag child : member.getList("Passengers", Tag.TAG_COMPOUND)) candidates.add((CompoundTag) child);
                }
                if (saved == null) return new byte[0];
            }
            NpcDataExternalStore.INVENTORY.readPreview(saved, world);
            NpcDataExternalStore.MODEL.readPreview(saved, world);
            return encode(snapshot(saved));
        } catch (Exception | LinkageError e) {
            return new byte[0];
        }
    }

    /** Keep the CNPC writer's format but avoid its long-array truncation and type guessing. */
    public static CompoundTag readSaved(File file) throws IOException {
        if (Files.size(file.toPath()) > MAX_FILE_BYTES) throw new IOException("clone file too large");
        byte[] bytes;
        try (var input = Files.newInputStream(file.toPath())) { bytes = input.readNBytes(MAX_FILE_BYTES + 1); }
        if (bytes.length > MAX_FILE_BYTES) throw new IOException("clone file too large");
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        validateText(text);
        try { return TagParser.parseTag(text); }
        catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new IOException("invalid clone NBT", e); }
    }

    public static void validateText(String text) throws IOException {
        int depth = 0, nodes = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') quote = c;
            else if (c == '{' || c == '[') { if (++depth > 64) throw new IOException("clone nesting too deep"); }
            else if (c == '}' || c == ']') { if (--depth < 0) throw new IOException("unbalanced clone"); }
            else if (c == ',' || c == ':') { if (++nodes > 131072) throw new IOException("clone too complex"); }
        }
        if (depth != 0 || quote != 0) throw new IOException("incomplete clone");
    }

    public static CompoundTag snapshot(CompoundTag saved) throws IOException {
        CompoundTag out = new CompoundTag();
        for (String key : DISPLAY) copy(saved, out, key, key);
        copy(saved, out, "NpcModelData", "ModelData");
        copy(saved, out, "MoveSpeed", "Speed");
        for (String axis : new String[]{"X", "Y", "Z"}) copy(saved, out, "PositionOffset" + axis, "Position" + axis + "Offset");
        if (saved.getInt("NpcJob") == 9) {
            CompoundTag puppet = new CompoundTag();
            for (String key : saved.getAllKeys()) if (key.startsWith("Puppet") || key.startsWith("CNPCPlusPuppet")) copy(saved, puppet, key, key);
            out.put("Puppet", puppet);
            out.putInt("Job", 9);
        }
        if (saved.contains("id", Tag.TAG_STRING)) out.putString("CNPCPlusPreviewType", saved.getString("id"));
        out.putInt("Role", 0);
        out.putInt("ShowName", 1);
        out.putInt("NpcVisible", 0);
        out.putBoolean("NoLivingAnimation", true);
        out.putByte("BossBar", (byte) 0);
        if (!out.contains("Size", Tag.TAG_ANY_NUMERIC)) out.putFloat("Size", 5);
        if (!out.contains("MaxHealth", Tag.TAG_ANY_NUMERIC)) out.putInt("MaxHealth", 20);
        finite(out, 0);
        if (out.getFloat("Size") <= 0 || out.getFloat("Size") > 10000) throw new IOException("invalid preview size");
        return out;
    }

    private static void copy(CompoundTag from, CompoundTag to, String key, String target) {
        if (from.contains(key)) to.put(target, from.get(key).copy());
    }
    static void finite(Tag tag, int depth) throws IOException {
        if (depth > 64) throw new IOException("preview nesting too deep");
        if (tag instanceof NumericTag number && !Double.isFinite(number.getAsDouble())) throw new IOException("non-finite preview value");
        if (tag instanceof CompoundTag compound) for (String key : compound.getAllKeys()) finite(compound.get(key), depth + 1);
        else if (tag instanceof ListTag list) for (Tag value : list) finite(value, depth + 1);
    }
    public static byte[] encode(CompoundTag tag) throws IOException {
        if (tag.sizeInBytes() > 2 * 1024 * 1024) throw new IOException("preview too large");
        var bytes = new ByteArrayOutputStream();
        NbtIo.write(tag, new DataOutputStream(bytes));
        if (bytes.size() > MAX_BYTES) throw new IOException("preview too large");
        return bytes.toByteArray();
    }
    public static CompoundTag decode(byte[] bytes) throws IOException {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) return null;
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            CompoundTag tag = NbtIo.read(input, NbtAccounter.create(2 * 1024 * 1024));
            finite(tag, 0);
            return tag;
        }
    }
}
