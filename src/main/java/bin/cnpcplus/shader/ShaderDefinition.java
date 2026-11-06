package bin.cnpcplus.shader;

import com.google.gson.*;
import java.util.*;
import net.minecraft.util.ResourceLocation;

/** CPU-only metadata. No client classes are referenced by the script contract. */
public final class ShaderDefinition {
    public static final class Parameter {
        final String type; final float[] defaults; final float min, max;
        Parameter(String type, float[] defaults, float min, float max) { this.type = type; this.defaults = defaults; this.min = min; this.max = max; }
        public String type() { return type; } public float[] defaults() { return defaults.clone(); }
        public float min() { return min; } public float max() { return max; }
    }
    public final String id, type, description, shaderResource;
    public final boolean remote, multiple, loop;
    public final int duration, priority;
    public final Map<String, Parameter> uniforms = new LinkedHashMap<>();
    public final Map<String, String> samplers = new LinkedHashMap<>();
    public final JsonObject json;
    public ShaderDefinition(String id, JsonObject json) {
        this.id = resource(id); this.json = new JsonParser().parse(json.toString()).getAsJsonObject();
        type = json.get("type").getAsString(); shaderResource = resource(json.get("shaderResource").getAsString());
        if (!type.equals("POST") && !type.equals("RENDER")) throw new IllegalArgumentException("Shader type must be POST or RENDER");
        description = json.has("description") ? json.get("description").getAsString() : id;
        remote = json.has("remote") && json.get("remote").getAsBoolean();
        multiple = json.has("supportsMultipleInstances") && json.get("supportsMultipleInstances").getAsBoolean();
        loop = json.has("supportsLoop") && json.get("supportsLoop").getAsBoolean();
        duration = json.has("defaultDuration") ? json.get("defaultDuration").getAsInt() : 200;
        priority = json.has("priority") ? json.get("priority").getAsInt() : 0;
        if (duration != -1 && (duration < 1 || duration > 1000000) || Math.abs((long)priority) > 1000000) throw new IllegalArgumentException("Invalid duration/priority");
        if (json.has("uniforms")) for (JsonElement element : json.getAsJsonArray("uniforms")) {
            JsonObject value = element.getAsJsonObject(); String name = value.get("name").getAsString(), kind = value.get("type").getAsString();
            int length = length(kind); JsonElement defaults = value.get("default"); float[] data = new float[length];
            if (defaults.isJsonArray()) {
                if (defaults.getAsJsonArray().size() != length) throw new IllegalArgumentException("Wrong default length: " + name);
                for (int i = 0; i < length; i++) data[i] = defaults.getAsJsonArray().get(i).getAsFloat();
            } else {
                if (length != 1) throw new IllegalArgumentException("Vector default required: " + name);
                data[0] = kind.equals("bool") ? (defaults.getAsBoolean() ? 1 : 0) : defaults.getAsFloat();
            }
            float min = value.has("min") ? value.get("min").getAsFloat() : -1000000, max = value.has("max") ? value.get("max").getAsFloat() : 1000000;
            if (!name.matches("[A-Za-z_][A-Za-z0-9_]*") || uniforms.containsKey(name) || !Float.isFinite(min) || !Float.isFinite(max) || min > max) throw new IllegalArgumentException("Invalid/duplicate parameter: " + name);
            uniforms.put(name, new Parameter(kind, data, min, max)); validate(name, data);
        }
        if (uniforms.size() > 64) throw new IllegalArgumentException("At most 64 uniforms");
        if (json.has("samplers")) for (JsonElement element : json.getAsJsonArray("samplers")) {
            JsonObject value = element.getAsJsonObject(); samplers.put(value.get("name").getAsString(), resource(value.get("default").getAsString()));
        }
        if (samplers.size() > (type.equals("RENDER") ? 6 : 8)) throw new IllegalArgumentException("Shader exceeds available texture units");
    }
    public static String resource(String id) {
        if (id == null || id.length() > 256 || !id.matches("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+") || id.contains("..")) throw new IllegalArgumentException("Invalid shader resource ID: " + id);
        return new ResourceLocation(id).toString();
    }
    public static int length(String type) {
        switch (type) { case "int": case "float": case "bool": return 1; case "vec2": return 2; case "vec3": return 3; case "vec4": return 4; case "matrix4": return 16; default: throw new IllegalArgumentException("Unsupported uniform type: " + type); }
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
    public String getId() { return id; } public String getType() { return type; } public String getDescription() { return description; }
    public String[] getUniformNames() { return uniforms.keySet().toArray(new String[0]); }
    public String[] getSamplerNames() { return samplers.keySet().toArray(new String[0]); }
    public boolean supports(String feature) {
        switch (feature) { case "fade": return uniforms.containsKey("Opacity"); case "loop": return loop; case "multipleInstances": return multiple; case "remote": return remote; default: return false; }
    }
    public boolean validate() { return true; }
}
