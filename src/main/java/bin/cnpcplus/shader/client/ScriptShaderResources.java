package bin.cnpcplus.shader.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;

/** Script-only resource view; redirect stage names before ShaderLoader's global cache lookup. */
final class ScriptShaderResources implements IResourceManager {
    private final IResourceManager delegate;
    ScriptShaderResources(IResourceManager delegate) { this.delegate = delegate; }
    private IResource adapt(ResourceLocation id, IResource source) throws IOException {
        if (!id.getPath().startsWith("shaders/program/") || !id.getPath().endsWith(".json")) return source;
        final byte[] data; final String packName = source.getResourcePackName();
        try {
            byte[] original = org.apache.commons.io.IOUtils.toByteArray(source.getInputStream()); JsonObject json = new JsonParser().parse(new String(original,StandardCharsets.UTF_8)).getAsJsonObject();
            boolean redirected = false;
            for (String stage : new String[]{"vertex", "fragment"}) if (json.has(stage)) {
                String name = json.get(stage).getAsString();
                if (name.equals("blit") || name.equals("minecraft:blit")) { json.addProperty(stage, "cnpcplus:script_blit"); redirected = true; }
            }
            if (!redirected) data = original;
            else {
                JsonArray uniforms = json.has("uniforms") ? json.getAsJsonArray("uniforms") : new JsonArray(); json.add("uniforms",uniforms);
                ensureUniform(uniforms,"OutSize","float",2,"[1,1]"); ensureUniform(uniforms,"ColorModulate","float",4,"[1,1,1,1]");
                JsonArray samplers = json.has("samplers") ? json.getAsJsonArray("samplers") : new JsonArray(); json.add("samplers",samplers);
                boolean diffuse = false; for (JsonElement sampler : samplers) if (sampler.getAsJsonObject().get("name").getAsString().equals("DiffuseSampler")) diffuse = true;
                if (!diffuse) { JsonObject sampler = new JsonObject(); sampler.addProperty("name","DiffuseSampler"); samplers.add(sampler); }
                data = json.toString().getBytes(StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException error) { org.apache.commons.io.IOUtils.closeQuietly(source); throw error; }
        return new IResource() {
            public ResourceLocation getResourceLocation() { return id; }
            public InputStream getInputStream() { return new ByteArrayInputStream(data); }
            public boolean hasMetadata() { return source.hasMetadata(); }
            public <T extends IMetadataSection> T getMetadata(String name) { return source.getMetadata(name); }
            public String getResourcePackName() { return packName; }
            public void close() throws IOException { source.close(); }
        };
    }
    private static void ensureUniform(JsonArray uniforms, String name, String type, int count, String values) {
        for (JsonElement element : uniforms) if (element.getAsJsonObject().get("name").getAsString().equals(name)) return;
        JsonObject uniform = new JsonObject(); uniform.addProperty("name",name); uniform.addProperty("type",type); uniform.addProperty("count",count); uniform.add("values",new JsonParser().parse(values)); uniforms.add(uniform);
    }
    public Set<String> getResourceDomains() { return delegate.getResourceDomains(); }
    public IResource getResource(ResourceLocation id) throws IOException { return adapt(id, delegate.getResource(id)); }
    public List<IResource> getAllResources(ResourceLocation id) throws IOException {
        List<IResource> result = new ArrayList<>();
        for (IResource resource : delegate.getAllResources(id)) result.add(adapt(id, resource));
        return result;
    }
}
