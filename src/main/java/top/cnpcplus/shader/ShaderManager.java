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
    public ShaderManager() {
        for (String id : new String[]{"cnpcplus:post/black_hole", "cnpcplus:post/invert", "cnpcplus:render/tint",
                "cnpcplus:post/blood_thorns", "cnpcplus:post/hexagram", "cnpcplus:post/strange_pattern", "cnpcplus:post/super_strike",
                "cnpcplus:post/wormhole", "cnpcplus:post/time_dilation", "cnpcplus:post/reality_fracture", "cnpcplus:post/prism",
                "cnpcplus:post/plasma_shield", "cnpcplus:post/arcane_beam", "cnpcplus:post/heat_haze", "cnpcplus:post/world_collapse"}) {
            ResourceLocation key = new ResourceLocation(id);
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
            String id = new ResourceLocation(entry.getKey().getNamespace(), path.substring("cnpcplus/shaders/".length(), path.length() - 5)).toString();
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
