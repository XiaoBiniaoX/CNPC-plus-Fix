package bin.cnpcplus.shader.client;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;
import static org.junit.Assert.*;

public class ScriptShaderResourcesTest {
    @Test public void worldProgramsDeclareCameraRelativePosition() throws Exception {
        for (String name : Arrays.asList("world_glyph","collection_world","black_hole","advanced_world","advanced_screen")) {
            try (InputStream input = getClass().getResourceAsStream("/assets/cnpcplus/shaders/program/"+name+".json")) {
                assertNotNull(name,input);
                JsonObject json = new JsonParser().parse(new InputStreamReader(input,StandardCharsets.UTF_8)).getAsJsonObject();
                JsonObject relative = null;
                for (JsonElement element : json.getAsJsonArray("uniforms")) if (element.getAsJsonObject().get("name").getAsString().equals("CameraRelativeWorldPosition")) relative = element.getAsJsonObject();
                assertNotNull(name,relative); assertEquals("float",relative.get("type").getAsString()); assertEquals(3,relative.get("count").getAsInt()); assertEquals(3,relative.getAsJsonArray("values").size());
            }
        }
    }
    @Test public void isolatesBlitAndBorrowedStagesWithoutChangingOtherResources() throws Exception {
        final List<String> requested = new ArrayList<>();
        IResourceManager original = new IResourceManager() {
            public Set<String> getResourceDomains() { return Collections.singleton("test"); }
            public List<IResource> getAllResources(ResourceLocation id) throws IOException { return Collections.singletonList(getResource(id)); }
            public IResource getResource(final ResourceLocation id) {
                requested.add(id.toString());
                final String body = id.getPath().equals("shaders/program/blit.json") && requested.size() == 1
                        ? "{\"vertex\":\"blit\",\"fragment\":\"minecraft:blit\",\"uniforms\":[{\"name\":\"Kept\"},{\"name\":\"ColorModulate\"}]}"
                        : id.getPath().equals("shaders/program/blit.json") ? "{\"vertex\":\"pack:flipped\",\"fragment\":\"pack:copy\"}"
                        : id.getPath().equals("shaders/program/borrowed.json") ? "{\"vertex\":\"minecraft:blit\",\"fragment\":\"test:custom\"}"
                        : "{\"vertex\":\"test:custom\",\"fragment\":\"test:custom\"}";
                return new IResource() {
                    public ResourceLocation getResourceLocation() { return id; }
                    public InputStream getInputStream() { return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
                    public boolean hasMetadata() { return false; }
                    public <T extends IMetadataSection> T getMetadata(String name) { return null; }
                    public String getResourcePackName() { return "test-pack"; }
                    public void close() {}
                };
            }
        };
        ScriptShaderResources scripts = new ScriptShaderResources(original);
        try (IResource resource = scripts.getResource(new ResourceLocation("minecraft:shaders/program/blit.json"))) {
            assertEquals("minecraft:shaders/program/blit.json",requested.get(0));
            assertEquals("test-pack",resource.getResourcePackName());
            JsonObject json = new JsonParser().parse(new InputStreamReader(resource.getInputStream(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("cnpcplus:script_blit",json.get("vertex").getAsString());
            assertEquals("cnpcplus:script_blit",json.get("fragment").getAsString());
            assertEquals("Kept",json.getAsJsonArray("uniforms").get(0).getAsJsonObject().get("name").getAsString());
            assertEquals("ColorModulate",json.getAsJsonArray("uniforms").get(1).getAsJsonObject().get("name").getAsString());
            assertEquals("OutSize",json.getAsJsonArray("uniforms").get(2).getAsJsonObject().get("name").getAsString());
            assertEquals("DiffuseSampler",json.getAsJsonArray("samplers").get(0).getAsJsonObject().get("name").getAsString());
        }
        try (IResource ignored = scripts.getResource(new ResourceLocation("test:shaders/post/chain.json"))) {
            assertEquals("test:shaders/post/chain.json",requested.get(1)); assertEquals("test:custom",new JsonParser().parse(new InputStreamReader(ignored.getInputStream(),StandardCharsets.UTF_8)).getAsJsonObject().get("vertex").getAsString());
        }
        try (IResource ignored = original.getResource(new ResourceLocation("minecraft:shaders/program/blit.json"))) {
            assertEquals("minecraft:shaders/program/blit.json",requested.get(2));
            assertEquals("pack:flipped",new JsonParser().parse(new InputStreamReader(ignored.getInputStream(),StandardCharsets.UTF_8)).getAsJsonObject().get("vertex").getAsString());
        }
        try (IResource resource = scripts.getResource(new ResourceLocation("test:shaders/program/borrowed.json"))) {
            JsonObject json = new JsonParser().parse(new InputStreamReader(resource.getInputStream(),StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("cnpcplus:script_blit",json.get("vertex").getAsString()); assertEquals("test:custom",json.get("fragment").getAsString());
        }
        try (IResource resource = scripts.getResource(new ResourceLocation("test:shaders/program/unrelated.json"))) {
            assertEquals("{\"vertex\":\"test:custom\",\"fragment\":\"test:custom\"}",org.apache.commons.io.IOUtils.toString(resource.getInputStream(),StandardCharsets.UTF_8));
        }
    }
}
