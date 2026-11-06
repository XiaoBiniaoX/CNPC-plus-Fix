package top.cnpcplus.shader;

import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.ResourceLocation;

/** CPU-only definition: safe to inspect and validate on a dedicated server. */
public final class ShaderDefinition {
    public record Parameter(String type, float[] defaults, float min, float max) {}
    public final String id, type, description, shaderResource;
    public final boolean remote, multiple, loop;
    public final int duration, priority;
    public final Map<String, Parameter> uniforms = new LinkedHashMap<>();
    public final Map<String, String> samplers = new LinkedHashMap<>();
    public final JsonObject json;
    public ShaderDefinition(String id, JsonObject json) {
        this.id = resource(id);
        this.json = json.deepCopy();
        type = json.get("type").getAsString();
        if (!type.equals("POST") && !type.equals("RENDER")) throw new IllegalArgumentException("Shader type must be POST or RENDER");
        shaderResource = resource(json.get("shaderResource").getAsString());
        description = json.has("description") ? json.get("description").getAsString() : id;
        remote = json.has("remote") && json.get("remote").getAsBoolean();
        multiple = json.has("supportsMultipleInstances") && json.get("supportsMultipleInstances").getAsBoolean();
        loop = json.has("supportsLoop") && json.get("supportsLoop").getAsBoolean();
        duration = json.has("defaultDuration") ? json.get("defaultDuration").getAsInt() : 200;
        priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
        if (duration != -1 && (duration < 1 || duration > 1000000) || Math.abs((long)priority) > 1000000) throw new IllegalArgumentException("Invalid duration/priority");
        if (json.has("uniforms")) for (JsonElement element : json.getAsJsonArray("uniforms")) {
            JsonObject value = element.getAsJsonObject();
            String name = value.get("name").getAsString(), kind = value.get("type").getAsString();
            int length = length(kind);
            JsonElement defaults = value.get("default");
            float[] data = new float[length];
            if (defaults.isJsonArray()) {
                if (defaults.getAsJsonArray().size() != length) throw new IllegalArgumentException("Wrong default length: " + name);
                for (int i = 0; i < length; i++) data[i] = defaults.getAsJsonArray().get(i).getAsFloat();
            } else {
                if (length != 1) throw new IllegalArgumentException("Vector default required: " + name);
                data[0] = kind.equals("bool") ? (defaults.getAsBoolean() ? 1 : 0) : defaults.getAsFloat();
            }
            float min = value.has("min") ? value.get("min").getAsFloat() : -1000000;
            float max = value.has("max") ? value.get("max").getAsFloat() : 1000000;
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || uniforms.containsKey(name) || !Float.isFinite(min) || !Float.isFinite(max) || min > max) throw new IllegalArgumentException("Invalid/duplicate parameter: " + name);
            uniforms.put(name, new Parameter(kind, data, min, max));
            validate(name, data);
        }
        if (uniforms.size() > 64) throw new IllegalArgumentException("At most 64 uniforms");
        if (json.has("samplers")) for (JsonElement element : json.getAsJsonArray("samplers")) {
            JsonObject value = element.getAsJsonObject();
            samplers.put(value.get("name").getAsString(), resource(value.get("default").getAsString()));
        }
        if (samplers.size() > 8) throw new IllegalArgumentException("At most 8 samplers");
    }
    public static String resource(String id) {
        ResourceLocation key = id == null ? null : ResourceLocation.tryParse(id);
        if (key == null || id.length() > 256 || key.getPath().contains("..")) throw new IllegalArgumentException("Invalid shader resource ID: " + id);
        return key.toString();
    }
    public static int length(String type) {
        return switch (type) { case "int", "float", "bool" -> 1; case "vec2" -> 2; case "vec3" -> 3; case "vec4" -> 4; case "matrix4" -> 16; default -> throw new IllegalArgumentException("Unsupported uniform type: " + type); };
    }
    public void validate(String name, float[] values) {
        Parameter parameter = uniforms.get(name);
        if (parameter == null) throw new IllegalArgumentException(id + ": unknown uniform " + name);
        if (values.length != length(parameter.type)) throw new IllegalArgumentException(id + ": wrong length for " + name);
        for (float value : values) {
            if (!Float.isFinite(value) || value < parameter.min || value > parameter.max) throw new IllegalArgumentException(id + ": out of range " + name);
            if ((parameter.type.equals("int") || parameter.type.equals("bool")) && value != Math.rint(value)) throw new IllegalArgumentException(id + ": integer required " + name);
            if (parameter.type.equals("bool") && value != 0 && value != 1) throw new IllegalArgumentException(id + ": boolean required " + name);
        }
    }
    public String getId() { return id; }
    public String getType() { return type; }
    public String getDescription() { return description; }
    public String[] getUniformNames() { return uniforms.keySet().toArray(String[]::new); }
    public String[] getSamplerNames() { return samplers.keySet().toArray(String[]::new); }
    public boolean supports(String feature) { return switch (feature) { case "fade" -> uniforms.containsKey("Opacity"); case "loop" -> loop; case "multipleInstances" -> multiple; case "remote" -> remote; default -> false; }; }
    public boolean validate() { return true; }
}
