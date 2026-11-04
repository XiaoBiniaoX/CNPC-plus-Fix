package top.cnpcplus.shader.client;

import com.google.gson.*;
import com.mojang.blaze3d.pipeline.*;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import java.io.*;
import java.util.*;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import top.cnpcplus.mixin.ShaderPostChainAccess;
import top.cnpcplus.shader.*;
import top.cnpcplus.shader.ShaderInstance;

/** All GPU objects live on the render thread. No script callback is executed here. */
public final class ClientShaders {
    public static ShaderManager MANAGER = new ShaderManager();
    public static final ShaderController CONTROLLER = ShaderController.localController();
    private static final Map<String, Gpu> LOADED = new LinkedHashMap<>();
    private static final Map<String, ShaderController> OBJECTS = new HashMap<>();
    private static Object world;
    private static TextureTarget depth;
    private static ShaderInstance currentPost;
    private static float partial;
    private static final Matrix4f worldViewProjection = new Matrix4f(), inverseWorldViewProjection = new Matrix4f();
    private ClientShaders() {}

    private static final class Gpu implements AutoCloseable {
        PostChain post;
        final Map<VertexFormat, net.minecraft.client.renderer.ShaderInstance> render = new HashMap<>();
        int width, height;
        @Override public void close() { if (post != null) post.close(); render.values().forEach(net.minecraft.client.renderer.ShaderInstance::close); }
    }
    public static boolean isLoaded(String id) { return LOADED.containsKey(id); }
    public static String[] loaded() { return LOADED.keySet().toArray(String[]::new); }
    public static boolean load(String id) {
        RenderSystem.assertOnRenderThread();
        if (isLoaded(id)) return true;
        ShaderDefinition definition = MANAGER.get(id);
        Minecraft mc = Minecraft.getInstance();
        Gpu gpu = new Gpu();
        try {
            if (LOADED.size() >= 8) throw new IllegalStateException("At most 8 loaded shader effects");
            if (definition.type.equals("POST")) {
                // Bound resource-pack target allocation before vanilla PostChain allocates GPU memory.
                try (Reader reader = mc.getResourceManager().getResourceOrThrow(new ResourceLocation(definition.shaderResource)).openAsReader()) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    JsonArray targets = json.has("targets") ? json.getAsJsonArray("targets") : new JsonArray();
                    long bytes = 0;
                    if (targets.size() > 4) throw new IllegalArgumentException("At most 4 temporary shader targets");
                    for (JsonElement target : targets) {
                        int w = mc.getMainRenderTarget().width, h = mc.getMainRenderTarget().height;
                        if (target.isJsonObject()) {
                            JsonObject value = target.getAsJsonObject();
                            if (value.has("width")) w = value.get("width").getAsInt();
                            if (value.has("height")) h = value.get("height").getAsInt();
                        }
                        if (w < 1 || h < 1 || w > 8192 || h > 8192) throw new IllegalArgumentException("Invalid shader target dimensions");
                        bytes += (long)w * h * 8;
                    }
                    if (bytes > 268435456L) throw new IllegalArgumentException("Shader targets exceed 256 MiB");
                }
                gpu.post = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), new ResourceLocation(definition.shaderResource));
                gpu.width = mc.getMainRenderTarget().width; gpu.height = mc.getMainRenderTarget().height;
                gpu.post.resize(gpu.width, gpu.height);
                for (String name : definition.uniforms.keySet()) {
                    if (((ShaderPostChainAccess)gpu.post).cnpcplus$passes().stream().noneMatch(p -> p.getEffect().getUniform(name) != null))
                        throw new IllegalArgumentException(id + ": GPU uniform missing: " + name);
                }
            } else {
                gpu.render.put(DefaultVertexFormat.NEW_ENTITY, new net.minecraft.client.renderer.ShaderInstance(mc.getResourceManager(), new ResourceLocation(definition.shaderResource), DefaultVertexFormat.NEW_ENTITY));
                if (definition.json.has("blockShaderResource")) gpu.render.put(DefaultVertexFormat.BLOCK,
                        new net.minecraft.client.renderer.ShaderInstance(mc.getResourceManager(), new ResourceLocation(definition.json.get("blockShaderResource").getAsString()), DefaultVertexFormat.BLOCK));
                for (var shader : gpu.render.values()) for (String name : definition.uniforms.keySet()) if (shader.getUniform(name) == null)
                    throw new IllegalArgumentException(id + ": GPU uniform missing: " + name);
            }
            LOADED.put(id, gpu);
            return true;
        } catch (IOException | RuntimeException e) {
            gpu.close();
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] load {}", id, e);
            return false;
        }
    }
    public static boolean unload(String id) {
        if (CONTROLLER.isActive(id) || OBJECTS.values().stream().anyMatch(c -> c.isActive(id))) return false;
        Gpu gpu = LOADED.remove(id); if (gpu == null) return false; gpu.close(); return true;
    }
    public static boolean reload(String id) {
        Gpu gpu = LOADED.remove(id); if (gpu != null) gpu.close(); return load(id);
    }
    public static void reloadAll() {
        clear(); MANAGER = new ShaderManager(); MANAGER.reloadDefinitions(Minecraft.getInstance().getResourceManager());
    }
    public static ShaderController objectController(String target) { return OBJECTS.computeIfAbsent(target, key -> ShaderController.localController(key)); }
    public static void accept(CompoundTag tag) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            if (world != mc.level) clear();
            world = mc.level;
            if (tag.contains("Dimension") && !tag.getString("Dimension").equals(mc.level.dimension().location().toString())) return;
            ShaderInstance instance = ShaderInstance.read(tag, MANAGER);
            if (!instance.definition.remote) throw new IllegalArgumentException("Shader is not remotely callable: " + instance.getId());
            ShaderController controller = instance.target.equals("post") ? CONTROLLER : objectController(instance.target);
            if (!instance.isActive()) controller.instances.remove(instance.token);
        else { instance.controller = controller; controller.instances.put(instance.token, instance); }
        } catch (RuntimeException e) { org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] receive", e); }
    }
    public static void clear() {
        CONTROLLER.instances.clear(); OBJECTS.clear(); LOADED.values().forEach(Gpu::close); LOADED.clear();
        if (depth != null) { depth.destroyBuffers(); depth = null; } currentPost = null; world = null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (world != mc.level) { clear(); world = mc.level; }
        if (mc.isPaused()) return;
        CONTROLLER.tick(); OBJECTS.values().removeIf(c -> { c.tick(); return c.instances.isEmpty(); });
        for (String id : loaded()) if (!CONTROLLER.isActive(id) && OBJECTS.values().stream().noneMatch(c -> c.isActive(id))) unload(id);
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    public static void renderPost(float framePartial, Matrix4f view, Matrix4f projection) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || CONTROLLER.instances.isEmpty()) {
            if (depth != null) { depth.destroyBuffers(); depth = null; }
            return;
        }
        partial = mc.isPaused() ? 0 : framePartial;
        worldViewProjection.set(projection).mul(view);
        inverseWorldViewProjection.set(worldViewProjection).invert();
        RenderTarget main = mc.getMainRenderTarget();
        if (depth == null) depth = new TextureTarget(main.width, main.height, true, Minecraft.ON_OSX);
        else if (depth.width != main.width || depth.height != main.height) depth.resize(main.width, main.height, Minecraft.ON_OSX);
        depth.copyDepthFrom(main);
        RenderSystem.disableBlend(); RenderSystem.disableDepthTest(); RenderSystem.resetTextureMatrix();
        try {
            for (ShaderInstance instance : Arrays.stream(CONTROLLER.getAllInstances()).sorted(Comparator.comparingInt(ShaderInstance::getPriority)).toList()) {
                currentPost = instance;
                if (!load(instance.getId())) { fail(instance, "GPU resource loading failed"); continue; }
                Gpu gpu = LOADED.get(instance.getId());
                if (gpu.width != main.width || gpu.height != main.height) { gpu.post.resize(main.width, main.height); gpu.width = main.width; gpu.height = main.height; }
                gpu.post.process(partial);
            }
        } catch (RuntimeException e) { if (currentPost != null) fail(currentPost, e.toString()); }
        finally { currentPost = null; main.copyDepthFrom(depth); main.bindWrite(true); RenderSystem.enableDepthTest(); }
    }
    private static void fail(ShaderInstance instance, String message) {
        instance.error = message; instance.state = "ERROR";
        org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] {} {}", instance.getId(), message);
    }
    /** Called after vanilla has set Time and auxiliary inputs, immediately before EffectInstance.apply. */
    public static void bindPost(EffectInstance effect) {
        if (currentPost == null) return;
        bindUniforms(currentPost, effect::getUniform, partial);
        effect.safeGetUniform("WorldViewProjection").set(worldViewProjection);
        effect.safeGetUniform("InverseWorldViewProjection").set(inverseWorldViewProjection);
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        effect.safeGetUniform("CameraPosition").set((float)camera.x, (float)camera.y, (float)camera.z);
        currentPost.textures.forEach((name, id) -> { if (!name.equals("DiffuseSampler")) effect.setSampler(name, () -> texture(id)); });
    }
    private static int texture(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (id.equals("minecraft:main")) return mc.getMainRenderTarget().getColorTextureId();
        if (id.equals("minecraft:depth")) return depth == null ? mc.getMainRenderTarget().getDepthTextureId() : depth.getDepthTextureId();
        ResourceLocation resource = new ResourceLocation(id);
        if (mc.getResourceManager().getResource(resource).isEmpty()) throw new IllegalArgumentException("Missing sampler texture " + id);
        return mc.getTextureManager().getTexture(resource).getId();
    }
    private static void bindUniforms(ShaderInstance instance, Function<String, Uniform> lookup, float framePartial) {
        float now = instance.time + (instance.isPaused() ? 0 : framePartial * instance.speed);
        instance.uniforms.forEach((name, value) -> {
            Uniform uniform = lookup.apply(name); if (uniform == null) return; // Parameters may belong to a different pass.
            float[] data = instance.animations.containsKey(name) ? instance.animations.get(name).value(now) : value;
            if (name.equals("Time")) data = new float[]{data[0] + now / 20};
            if (name.equals("Opacity")) data = new float[]{data[0] * instance.opacity(framePartial)};
            if (uniform.getCount() != data.length) throw new IllegalArgumentException("GPU uniform length mismatch: " + name);
            if (uniform.getType() <= 3) uniform.set((int)data[0]); else uniform.set(data);
        });
    }
    public static MultiBufferSource wrap(String target, MultiBufferSource source, float framePartial) {
        ShaderController controller = OBJECTS.get(target);
        if (controller == null) return source;
        ShaderInstance instance = Arrays.stream(controller.getAllInstances()).max(Comparator.comparingInt(ShaderInstance::getPriority)).orElse(null);
        if (instance == null) return source;
        if (!load(instance.getId())) { fail(instance, "GPU resource loading failed"); return source; }
        return new ObjectBuffers(source, instance, Minecraft.getInstance().isPaused() ? 0 : framePartial);
    }
    public static MultiBufferSource wrapItem(ItemStack stack, MultiBufferSource source) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.hasUUID("CNPCPlusShaderItem")) return source;
        return wrap("item:" + tag.getUUID("CNPCPlusShaderItem"), source, Minecraft.getInstance().getFrameTime());
    }
    public static void finish(MultiBufferSource source) {
        if (source instanceof ObjectBuffers buffers) {
            try { buffers.buffers.endBatch(); }
            catch (RuntimeException e) { fail(buffers.instance, e.toString()); }
        }
    }
    private static final class ObjectBuffers implements MultiBufferSource {
        final MultiBufferSource parent;
        final MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(new BufferBuilder(256));
        final Map<RenderType, RenderType> types = new IdentityHashMap<>();
        final ShaderInstance instance;
        final float framePartial;
        ObjectBuffers(MultiBufferSource parent, ShaderInstance instance, float framePartial) { this.parent = parent; this.instance = instance; this.framePartial = framePartial; }
        @Override public VertexConsumer getBuffer(RenderType original) {
            var shader = LOADED.get(instance.getId()).render.get(original.format());
            if (shader == null || original.isOutline()) return parent.getBuffer(original);
            RenderType type = types.computeIfAbsent(original, key -> new RenderType("cnpcplus_shader", key.format(), key.mode(), key.bufferSize(), key.affectsCrumbling(), true, () -> {
                key.setupRenderState();
                bindUniforms(instance, shader::getUniform, framePartial);
                instance.textures.forEach((name, id) -> shader.setSampler(name, texture(id)));
                RenderSystem.setShader(() -> shader);
            }, key::clearRenderState) {});
            return buffers.getBuffer(type);
        }
    }
}
