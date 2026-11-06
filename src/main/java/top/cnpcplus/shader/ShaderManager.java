package top.cnpcplus.shader;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

public final class ShaderManager {
    public static final ShaderManager SERVER = new ShaderManager();
    private final Map<String, ShaderDefinition> definitions = new LinkedHashMap<>();
    private static final Map<UUID, Map<String, ShaderDefinition>> PLAYER_PACKS = new HashMap<>();
    public ShaderManager() {
        for (String id : new String[]{"cnpcplus:post/black_hole", "cnpcplus:post/invert", "cnpcplus:render/tint",
                "cnpcplus:post/blood_thorns", "cnpcplus:post/hexagram", "cnpcplus:post/strange_pattern", "cnpcplus:post/super_strike",
                "cnpcplus:post/wormhole", "cnpcplus:post/time_dilation", "cnpcplus:post/reality_fracture", "cnpcplus:post/prism",
                "cnpcplus:post/plasma_shield", "cnpcplus:post/arcane_beam", "cnpcplus:post/heat_haze", "cnpcplus:post/world_collapse",
                "cnpcplus:post/moving_singularity", "cnpcplus:post/gravity_propagation", "cnpcplus:post/surface_glitch",
                "cnpcplus:post/aurora_curtain", "cnpcplus:post/thunder_orb", "cnpcplus:post/prismatic_crystal",
                "cnpcplus:post/flame_vortex", "cnpcplus:post/ice_lotus", "cnpcplus:post/orbital_armillary",
                "cnpcplus:post/eldritch_tentacles", "cnpcplus:post/nebula_cube", "cnpcplus:post/living_star",
                "cnpcplus:post/runic_obelisk", "cnpcplus:post/meteor_shower", "cnpcplus:post/water_sphere",
                "cnpcplus:post/living_flesh", "cnpcplus:post/liquid_mirror", "cnpcplus:post/kaleidoscope",
                "cnpcplus:post/ink_corrosion", "cnpcplus:post/history_ghost", "cnpcplus:post/sonar_scan",
                "cnpcplus:post/digital_rain", "cnpcplus:post/chromatic_spiral", "cnpcplus:post/dream_fog",
                "cnpcplus:post/neural_lattice", "cnpcplus:post/burning_dissolve", "cnpcplus:post/rain_lens",
                "cnpcplus:post/wave_interference", "cnpcplus:post/warp_speed", "cnpcplus:post/abyssal_eye"}) {
            ResourceLocation key = ResourceLocation.parse(id);
            try (InputStream input = ShaderManager.class.getResourceAsStream("/assets/" + key.getNamespace() + "/cnpcplus/shaders/" + key.getPath() + ".json");
                 Reader reader = new InputStreamReader(Objects.requireNonNull(input, id), StandardCharsets.UTF_8)) {
                definitions.put(id, new ShaderDefinition(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (IOException | RuntimeException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] definition {}", id, e); }
        }
    }
    public void reloadDefinitions(ResourceManager resources) {
        definitions.clear();
        definitions.putAll(new ShaderManager().definitions);
        for (var entry : resources.listResources("cnpcplus/shaders", key -> key.getPath().endsWith(".json")).entrySet()) {
            String path = entry.getKey().getPath();
            String id = ResourceLocation.fromNamespaceAndPath(entry.getKey().getNamespace(), path.substring("cnpcplus/shaders/".length(), path.length() - 5)).toString();
            try (Reader reader = entry.getValue().openAsReader()) {
                definitions.put(id, new ShaderDefinition(id, JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (IOException | RuntimeException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] definition {}", id, e); }
        }
    }
    public ShaderDefinition get(String id) {
        ShaderDefinition definition = definitions.get(ShaderDefinition.resource(id));
        if (definition == null) throw new IllegalArgumentException("Unregistered shader: " + id);
        return definition;
    }
    public static ShaderDefinition forPlayer(UUID player, String id) {
        String key = ShaderDefinition.resource(id);
        ShaderDefinition packed = PLAYER_PACKS.getOrDefault(player, Map.of()).get(key);
        return packed == null ? SERVER.get(key) : packed;
    }
    public static void clearPack(UUID player) { PLAYER_PACKS.remove(player); }
    public static String[] available(UUID player) {
        Set<String> ids = new LinkedHashSet<>(Arrays.asList(SERVER.getAll()));
        ids.addAll(PLAYER_PACKS.getOrDefault(player, Map.of()).keySet());
        return ids.toArray(String[]::new);
    }
    public static void clearPacks() { PLAYER_PACKS.clear(); }
    public static void acceptPack(UUID player, String id, JsonObject json) {
        ShaderDefinition definition = new ShaderDefinition(id, json);
        if (!definition.remote || SERVER.exists(id) && !SERVER.get(id).remote) throw new IllegalArgumentException("Not remotely callable: " + id);
        Map<String, ShaderDefinition> pack = PLAYER_PACKS.computeIfAbsent(player, key -> new LinkedHashMap<>());
        if (!pack.containsKey(definition.id) && pack.size() >= 128) throw new IllegalArgumentException("At most 128 resource-pack effects per player");
        pack.put(definition.id, definition);
    }
    public ShaderDefinition getDefinition(String id) { return get(id); }
    public boolean exists(String id) { return definitions.containsKey(ShaderDefinition.resource(id)); }
    public String[] getAll() { return definitions.keySet().toArray(String[]::new); }
    public boolean isLoaded(String id) { return ShaderRuntime.isLoaded(id); }
    public boolean load(String id) { get(id); return ShaderRuntime.load(id); }
    public boolean unload(String id) { get(id); return ShaderRuntime.unload(id); }
    public boolean reload(String id) { get(id); return ShaderRuntime.reload(id); }
    public void reloadAll() { ShaderRuntime.reloadAll(); }
    public String[] getLoaded() { return ShaderRuntime.loaded(); }
}
