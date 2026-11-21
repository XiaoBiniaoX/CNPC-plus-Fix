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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;
import top.cnpcplus.client.render.CameraRelative;
import top.cnpcplus.mixin.ShaderPostChainAccess;
import top.cnpcplus.mixin.ShaderEffectInstanceAccess;
import top.cnpcplus.shader.*;
import top.cnpcplus.shader.ShaderInstance;

/** All GPU objects live on the render thread. No script callback is executed here. */
public final class ClientShaders {
    public static ShaderManager MANAGER = new ShaderManager();
    public static final ShaderController CONTROLLER = ShaderController.localController();
    private static final Map<String, Gpu> LOADED = new LinkedHashMap<>();
    private static final Map<String, ShaderController> OBJECTS = new HashMap<>();
    private static final PostPipeline POST_PIPELINE = new PostPipeline();
    private static final List<Gpu> RETIRED = new ArrayList<>();
    private static boolean resetPostResources;
    private static Object world;
    private static ShaderInstance currentPost;
    private static PostRenderState postState;
    private static float partial;
    private static CameraRelative postCamera;
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
        // Explicit API preloads are synchronous, but cannot leak allocation state
        // into whichever game pipeline happens to be active at the call site.
        try (PostRenderState state = postState == null ? new PostRenderState(mc.getMainRenderTarget()) : null) {
            disposeRetired();
            try {
                if (LOADED.size() >= 32) {
                    for (String old : new ArrayList<>(LOADED.keySet())) if (unload(old)) break;
                    if (LOADED.size() >= 32) throw new IllegalStateException("At most 32 loaded shader effects");
                }
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
                    RenderTarget scriptTarget = POST_PIPELINE.target(mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
                    gpu.post = new PostChain(mc.getTextureManager(), mc.getResourceManager(), scriptTarget, new ResourceLocation(definition.shaderResource));
                    gpu.width = mc.getMainRenderTarget().width; gpu.height = mc.getMainRenderTarget().height;
                    gpu.post.resize(gpu.width, gpu.height);
                    for (PostPass pass : ((ShaderPostChainAccess)gpu.post).cnpcplus$passes()) {
                        if (pass.inTarget.getColorTextureId() == pass.outTarget.getColorTextureId())
                            throw new IllegalArgumentException("Post pass reads and writes the same color texture: " + pass.getName());
                    }
                    for (String name : definition.uniforms.keySet()) {
                        if (((ShaderPostChainAccess)gpu.post).cnpcplus$passes().stream().noneMatch(p -> p.getEffect().getUniform(name) != null
                                || name.equals("WorldPosition") && definition.uniforms.get(name).type().equals("vec3")
                                && p.getEffect().getUniform("CameraRelativeWorldPosition") != null))
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
    }
    public static boolean unload(String id) {
        if (CONTROLLER.isActive(id) || OBJECTS.values().stream().anyMatch(c -> c.isActive(id))) return false;
        Gpu gpu = LOADED.remove(id); if (gpu == null) return false; RETIRED.add(gpu); return true;
    }
    public static boolean reload(String id) {
        Gpu gpu = LOADED.remove(id); if (gpu != null) RETIRED.add(gpu); return load(id);
    }
    public static void reloadAll() {
        clear(); MANAGER = new ShaderManager(); MANAGER.reloadDefinitions(Minecraft.getInstance().getResourceManager());
        syncCatalog();
    }
    public static void syncCatalog() {
        if (Minecraft.getInstance().getConnection() == null) return;
        ShaderPacket.CHANNEL.sendToServer(new ShaderCatalogPacket("", ""));
        int count = 0;
        for (String id : MANAGER.getAll()) {
            ShaderDefinition definition = MANAGER.get(id);
            if (!definition.remote) continue;
            String json = definition.json.toString();
            if (json.length() > 12288 || count >= 128) {
                org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] pack catalog limit: {}", id);
                continue;
            }
            ShaderPacket.CHANNEL.sendToServer(new ShaderCatalogPacket(id, json));
            count++;
        }
    }
    @SubscribeEvent public static void login(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) { syncCatalog(); }
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
        CONTROLLER.instances.clear(); OBJECTS.clear(); RETIRED.addAll(LOADED.values()); LOADED.clear();
        resetPostResources = true; currentPost = null; world = null;
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

    /** FRAME_START: dispose GPU resources independently of script stop/tick/network callbacks. */
    public static void beginFrame() {
        POST_PIPELINE.beginFrame();
        boolean releasePost = POST_PIPELINE.isAllocated() && LOADED.values().stream().noneMatch(gpu -> gpu.post != null);
        if (RETIRED.isEmpty() && !resetPostResources && !releasePost) return;
        try (PostRenderState state = new PostRenderState(Minecraft.getInstance().getMainRenderTarget())) {
            disposeRetired();
            POST_PIPELINE.retainHistories(CONTROLLER.instances.keySet());
            if (releasePost) POST_PIPELINE.close();
        }
    }

    private static void disposeRetired() {
        RETIRED.forEach(Gpu::close); RETIRED.clear();
        if (resetPostResources) { POST_PIPELINE.close(); resetPostResources = false; }
    }

    /** WORLD_COMPLETE: read-only depth handoff, before vanilla post passes and GUI. */
    public static void capturePostDepth() {
        if (CONTROLLER.getAllInstances().length == 0) return;
        try (PostRenderState state = new PostRenderState(Minecraft.getInstance().getMainRenderTarget())) {
            POST_PIPELINE.captureDepth(Minecraft.getInstance().getMainRenderTarget());
        } catch (RuntimeException e) {
            for (ShaderInstance instance : CONTROLLER.getAllInstances()) fail(instance, "World depth capture failed: " + e);
        }
    }

    public static void renderPost(float framePartial, Matrix4f view, Matrix4f projection) {
        renderPost(framePartial, view, projection, Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
    }
    public static void renderPost(float framePartial, Matrix4f view, Matrix4f projection, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        ShaderInstance[] instances = CONTROLLER.getAllInstances();
        if (mc.level == null || instances.length == 0) return;
        partial = mc.isPaused() ? 0 : framePartial;
        CameraRelative.viewProjection(view, projection, worldViewProjection, inverseWorldViewProjection);
        RenderTarget main = mc.getMainRenderTarget();
        if (org.lwjgl.opengl.GL33C.glGetInteger(org.lwjgl.opengl.GL33C.GL_DRAW_FRAMEBUFFER_BINDING) != main.frameBufferId) {
            for (ShaderInstance instance : instances) fail(instance, "Script composite stage does not own the final game target");
            return;
        }
        try (PostRenderState state = new PostRenderState(main)) {
            postState = state;
            postCamera = new CameraRelative(camera);
            // AFTER_GAME_POST -> SCRIPT_EFFECTS -> BEFORE_GUI. The game target is
            // only read here and receives one color copy after all private passes.
            POST_PIPELINE.captureScene(main);
            POST_PIPELINE.retainHistories(CONTROLLER.instances.keySet());
            for (ShaderInstance instance : Arrays.stream(instances).sorted(Comparator.comparingInt(ShaderInstance::getPriority)).toList()) {
                currentPost = instance;
                if (!load(instance.getId())) { fail(instance, "GPU resource loading failed"); continue; }
                Gpu gpu = LOADED.get(instance.getId());
                if (gpu.width != main.width || gpu.height != main.height) { gpu.post.resize(main.width, main.height); gpu.width = main.width; gpu.height = main.height; }
                POST_PIPELINE.beginEffect(instance.token, instance.textures.values().stream().anyMatch(id -> id.startsWith("cnpcplus:history")));
                try {
                    gpu.post.process(partial);
                    POST_PIPELINE.finishEffect(instance.token, !mc.isPaused() && !instance.isPaused());
                } catch (RuntimeException e) {
                    fail(instance, e.toString());
                    POST_PIPELINE.rollbackEffect();
                }
            }
            currentPost = null;
            POST_PIPELINE.present(main);
        } catch (RuntimeException e) {
            for (ShaderInstance instance : instances) fail(instance, "Script post pipeline failed: " + e);
        } finally {
            currentPost = null; postState = null; postCamera = null;
        }
    }
    private static void fail(ShaderInstance instance, String message) {
        instance.error = message; instance.state = "ERROR";
        org.slf4j.LoggerFactory.getLogger("cnpcplus").error("[shader] {} {}", instance.getId(), message);
    }
    /** Prepare the pass while keeping bindPost(EffectInstance) available to addon mixins. */
    public static void preparePost(PostPass pass) {
        if (currentPost == null) return;
        EffectInstance effect = pass.getEffect();
        bindPost(effect);
        ShaderEffectInstanceAccess access = (ShaderEffectInstanceAccess)effect;
        // Resolve once before outTarget.clear; auxiliary and script samplers can also
        // alias the output even when intarget and outtarget are different targets.
        for (String name : access.cnpcplus$samplerNames()) {
            var sampler = access.cnpcplus$samplers().get(name);
            if (sampler == null) continue;
            int texture = POST_PIPELINE.readTexture(sampler.getAsInt());
            if (texture != -1 && (texture == pass.outTarget.getColorTextureId() || texture == pass.outTarget.getDepthTextureId()))
                throw new IllegalArgumentException("Post sampler reads its output texture: " + pass.getName() + "/" + name);
            effect.setSampler(name, () -> texture);
        }
    }
    /** A script blit copies processed color even when the source alpha is zero. */
    public static void preparePostDraw(PostPass pass) {
        if (currentPost != null && pass.getName().equals("blit")) RenderSystem.disableBlend();
    }
    /** Called before EffectInstance.apply; addons inject here after our uniforms are set. */
    public static void bindPost(EffectInstance effect) {
        if (currentPost == null) return;
        ShaderEffectInstanceAccess access = (ShaderEffectInstanceAccess)effect;
        postState.beforePass(effect, access.cnpcplus$samplerNames().size());
        bindUniforms(currentPost, effect::getUniform, partial);
        effect.safeGetUniform("WorldViewProjection").set(worldViewProjection);
        effect.safeGetUniform("InverseWorldViewProjection").set(inverseWorldViewProjection);
        Vec3 camera = postCamera.origin();
        effect.safeGetUniform("CameraPosition").set((float)camera.x, (float)camera.y, (float)camera.z);
        Uniform relative = effect.getUniform("CameraRelativeWorldPosition");
        Uniform absolute = effect.getUniform("WorldPosition");
        if (relative != null || absolute != null) {
            Minecraft mc = Minecraft.getInstance();
            Vec3 followed = null;
            if (currentPost.followEntity != null) {
                for (var entity : mc.level.entitiesForRendering()) if (entity.getUUID().equals(currentPost.followEntity)) {
                    followed = entity.getPosition(partial).add(0, entity.getBbHeight() / 2.0, 0);
                    break;
                }
            } else if (mc.player != null && currentPost.definition.json.has("followPlayer")
                    && currentPost.definition.json.get("followPlayer").getAsBoolean()) {
                followed = mc.player.getPosition(partial).add(0, 0.9, 0);
            }
            if (absolute != null && followed != null) absolute.set((float)followed.x, (float)followed.y, (float)followed.z);
            if (relative != null) {
                Vec3 anchor = followed != null ? followed : currentPost.worldPosition(partial);
                Vec3 position = postCamera.position(anchor != null ? anchor : Vec3.ZERO);
                relative.set((float)position.x, (float)position.y, (float)position.z);
            }
        }
        currentPost.textures.forEach((name, id) -> { if (!name.equals("DiffuseSampler")) effect.setSampler(name, () -> texture(id)); });
    }
    private static int texture(String id) {
        Minecraft mc = Minecraft.getInstance();
        if (id.equals("minecraft:main")) return currentPost == null ? mc.getMainRenderTarget().getColorTextureId() : POST_PIPELINE.sceneColor();
        if (id.equals("minecraft:depth")) return currentPost == null ? mc.getMainRenderTarget().getDepthTextureId() : POST_PIPELINE.sceneDepth();
        if (id.equals("cnpcplus:history") || id.equals("cnpcplus:history_2") || id.equals("cnpcplus:history_3")) {
            return POST_PIPELINE.history(currentPost.token, id.equals("cnpcplus:history") ? 0 : id.equals("cnpcplus:history_2") ? 1 : 2);
        }
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
