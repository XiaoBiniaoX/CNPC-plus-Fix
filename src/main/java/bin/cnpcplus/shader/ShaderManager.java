package bin.cnpcplus.shader;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.util.ResourceLocation;
import bin.cnpcplus.CnpcPlus;

public final class ShaderManager {
    public static final ShaderManager SERVER = new ShaderManager();
    private final Map<String, ShaderDefinition> definitions = new LinkedHashMap<>();
    private static final Map<UUID, Map<String, ShaderDefinition>> PLAYER_PACKS = new HashMap<>();
    public ShaderManager() {
        try (InputStream input = ShaderManager.class.getResourceAsStream("/assets/cnpcplus/cnpcplus/shaders/catalogue.json"); Reader reader = new InputStreamReader(Objects.requireNonNull(input), StandardCharsets.UTF_8)) {
            for (JsonElement element : new JsonParser().parse(reader).getAsJsonArray()) {
                String id = element.getAsString(); ResourceLocation key = new ResourceLocation(id);
                try (InputStream data = ShaderManager.class.getResourceAsStream("/assets/" + key.getNamespace() + "/cnpcplus/shaders/" + key.getPath() + ".json"); Reader json = new InputStreamReader(Objects.requireNonNull(data, id), StandardCharsets.UTF_8)) { register(id, new JsonParser().parse(json).getAsJsonObject()); }
            }
        } catch (IOException | RuntimeException e) { CnpcPlus.LOGGER.error("[shader] built-in definitions", e); }
    }
    public void register(String id, JsonObject json) { ShaderDefinition definition = new ShaderDefinition(id, json); definitions.put(definition.id, definition); }
    public ShaderDefinition get(String id) { ShaderDefinition definition = definitions.get(ShaderDefinition.resource(id)); if (definition == null) throw new IllegalArgumentException("Unregistered shader: " + id); return definition; }
    public static ShaderDefinition forPlayer(UUID player, String id) { String key = ShaderDefinition.resource(id); ShaderDefinition packed = PLAYER_PACKS.getOrDefault(player, Collections.emptyMap()).get(key); return packed == null ? SERVER.get(key) : packed; }
    public static void clearPack(UUID player) { PLAYER_PACKS.remove(player); } public static void clearPacks() { PLAYER_PACKS.clear(); }
    public static String[] available(UUID player) { Set<String> ids = new LinkedHashSet<>(Arrays.asList(SERVER.getAll())); ids.addAll(PLAYER_PACKS.getOrDefault(player, Collections.emptyMap()).keySet()); return ids.toArray(new String[0]); }
    public static void acceptPack(UUID player, String id, JsonObject json) {
        ShaderDefinition definition = new ShaderDefinition(id, json);
        if (!definition.remote || SERVER.exists(id) && !SERVER.get(id).remote) throw new IllegalArgumentException("Not remotely callable: " + id);
        Map<String, ShaderDefinition> pack = PLAYER_PACKS.computeIfAbsent(player, key -> new LinkedHashMap<>());
        if (!pack.containsKey(definition.id) && pack.size() >= 128) throw new IllegalArgumentException("At most 128 resource-pack effects per player"); pack.put(definition.id, definition);
    }
    public ShaderDefinition getDefinition(String id) { return get(id); } public boolean exists(String id) { return definitions.containsKey(ShaderDefinition.resource(id)); }
    public String[] getAll() { return definitions.keySet().toArray(new String[0]); }
    public boolean isLoaded(String id) { return ShaderRuntime.isLoaded(id); } public boolean load(String id) { get(id); return ShaderRuntime.load(id); }
    public boolean unload(String id) { get(id); return ShaderRuntime.unload(id); } public boolean reload(String id) { get(id); return ShaderRuntime.reload(id); }
    public void reloadAll() { ShaderRuntime.reloadAll(); } public String[] getLoaded() { return ShaderRuntime.loaded(); }
}
