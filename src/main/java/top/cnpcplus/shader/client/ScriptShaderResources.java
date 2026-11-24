package top.cnpcplus.shader.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/** Keeps vanilla blit overrides out of script passes without changing their IDs or uniforms. */
final class ScriptShaderResources implements ResourceManager {
    private static final String BLIT = "cnpcplus:script_blit";
    private static final ResourceLocation BLIT_JSON = ResourceLocation.fromNamespaceAndPath("cnpcplus", "shaders/program/script_blit.json");
    private final ResourceManager delegate;

    ScriptShaderResources(ResourceManager delegate) { this.delegate = delegate; }

    private static ResourceLocation resolve(ResourceLocation id) {
        return id.getNamespace().equals("minecraft") && id.getPath().equals("shaders/program/blit.json") ? BLIT_JSON : id;
    }

    private static Resource adapt(ResourceLocation id, Resource resource) {
        if (!id.getPath().startsWith("shaders/program/") || !id.getPath().endsWith(".json")) return resource;
        return new Resource(resource.source(), () -> {
            JsonObject json;
            try (Reader reader = resource.openAsReader()) { json = JsonParser.parseReader(reader).getAsJsonObject(); }
            // EffectInstance caches compiled stages globally by name. Redirect the
            // JSON references before that lookup, including legacy borrowed vertices.
            for (String stage : new String[]{"vertex", "fragment"}) {
                if (json.has(stage)) {
                    String name = json.get(stage).getAsString();
                    if (name.equals("blit") || name.equals("minecraft:blit")) json.addProperty(stage, BLIT);
                }
            }
            return new ByteArrayInputStream(json.toString().getBytes(StandardCharsets.UTF_8));
        }, resource::metadata);
    }

    @Override public Optional<Resource> getResource(ResourceLocation id) {
        ResourceLocation resolved = resolve(id);
        return delegate.getResource(resolved).map(resource -> adapt(resolved, resource));
    }

    @Override public List<Resource> getResourceStack(ResourceLocation id) {
        ResourceLocation resolved = resolve(id);
        return delegate.getResourceStack(resolved).stream().map(resource -> adapt(resolved, resource)).toList();
    }

    @Override public Set<String> getNamespaces() { return delegate.getNamespaces(); }
    @Override public Stream<PackResources> listPacks() { return delegate.listPacks(); }
    @Override public Map<ResourceLocation, Resource> listResources(String path, Predicate<ResourceLocation> filter) {
        return delegate.listResources(path, filter);
    }
    @Override public Map<ResourceLocation, List<Resource>> listResourceStacks(String path, Predicate<ResourceLocation> filter) {
        return delegate.listResourceStacks(path, filter);
    }
}
