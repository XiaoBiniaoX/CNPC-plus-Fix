package bin.cnpcplus.shader.client;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.shader.*;
import bin.cnpcplus.shader.ShaderManager;
import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.mixin.shader.*;
import com.google.gson.*;
import java.io.*;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.shader.*;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.lwjgl.util.vector.Matrix4f;

/** Legacy compatibility rendering. All GL work stays on Minecraft's client thread. */
@Mod.EventBusSubscriber(modid = "cnpcplus", value = Side.CLIENT)
public final class ClientShaders {
    public static ShaderManager MANAGER = new ShaderManager();
    public static final ShaderController CONTROLLER = ShaderController.localController();
    private static final Map<String, Gpu> LOADED = new LinkedHashMap<>();
    private static final Map<String, ShaderController> OBJECTS = new HashMap<>();
    private static final Map<String, String> FAILURES = new HashMap<>();
    private static final Map<String, ResourceLocation> TEXTURE_KEYS = new LinkedHashMap<>();
    private static final PostPipeline PIPELINE = new PostPipeline();
    private static final Deque<String> TARGETS = new ArrayDeque<>();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f(), INVERSE = new Matrix4f();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static Object world, player;
    private static boolean catalogPending, postAfterWorld;
    private static ShaderInstance currentPost;
    private static Shader currentPass;
    private static Vec3d camera = Vec3d.ZERO;
    private static int previousProgram = -1;
    private static net.minecraft.client.shader.ShaderManager previousManager;
    public static boolean compilingList;
    private static int previousTextureUnit;
    private static float partial;
    private static final Map<Integer, Integer> RESTORE_TEXTURES = new HashMap<>();
    private ClientShaders() {}
    @SubscribeEvent public static void initialize(net.minecraftforge.client.event.ModelRegistryEvent event) {
        ((IReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(resources -> reloadAll());
    }
    private static final class Gpu {
        ShaderGroup post; net.minecraft.client.shader.ShaderManager render, block;
        int width, height;
        void close() { if (post != null) post.deleteShaderGroup(); if (render != null) render.deleteShader(); if (block != null) block.deleteShader(); }
    }
    private static void renderThread() { if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) throw new IllegalStateException("Shader GPU operations require the render thread"); }
    public static boolean isLoaded(String id) { return LOADED.containsKey(id); }
    public static String[] loaded() { return LOADED.keySet().toArray(new String[0]); }
    public static boolean load(String id) {
        renderThread(); id = ShaderDefinition.resource(id); if (FAILURES.containsKey(id)) return false; if (isLoaded(id)) return true;
        if (!OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) { String error = "OpenGL shaders/framebuffers are disabled"; FAILURES.put(id,error); CnpcPlus.LOGGER.error("[shader] load {}: {}",id,error); return false; }
        ShaderDefinition definition = MANAGER.get(id); Minecraft mc = Minecraft.getMinecraft(); Gpu gpu = new Gpu();
        try (PostRenderState state = new PostRenderState()) {
            if (LOADED.size() >= 32) { for (String old : new ArrayList<>(LOADED.keySet())) if (unload(old)) break; if (LOADED.size() >= 32) throw new IllegalStateException("At most 32 loaded shader effects"); }
            if (definition.type.equals("POST")) {
                try (IResource resource = mc.getResourceManager().getResource(new ResourceLocation(definition.shaderResource)); Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
                    JsonObject json = new JsonParser().parse(reader).getAsJsonObject(); JsonArray targets = json.has("targets") ? json.getAsJsonArray("targets") : new JsonArray();
                    long bytes = 0; if (targets.size() > 4) throw new IllegalArgumentException("At most 4 temporary shader targets");
                    for (JsonElement target : targets) {
                        int w = mc.getFramebuffer().framebufferWidth, h = mc.getFramebuffer().framebufferHeight;
                        if (target.isJsonObject()) { JsonObject value = target.getAsJsonObject(); if (value.has("width")) w = value.get("width").getAsInt(); if (value.has("height")) h = value.get("height").getAsInt(); }
                        if (w < 1 || h < 1 || w > 8192 || h > 8192) throw new IllegalArgumentException("Invalid shader target dimensions"); bytes += (long)w*h*8;
                    }
                    if (bytes > 268435456L) throw new IllegalArgumentException("Shader targets exceed 256 MiB");
                }
                Framebuffer target = PIPELINE.target(mc.getFramebuffer().framebufferWidth,mc.getFramebuffer().framebufferHeight);
                IResourceManager resources = new ScriptShaderResources(mc.getResourceManager());
                // Publish the group before parsing real passes so partial failures can be closed.
                gpu.post = new ShaderGroup(mc.getTextureManager(),resources,target,new ResourceLocation("cnpcplus:shaders/post/script_empty.json"));
                gpu.post.parseGroup(mc.getTextureManager(),new ResourceLocation(definition.shaderResource));
                gpu.width = mc.getFramebuffer().framebufferWidth; gpu.height = mc.getFramebuffer().framebufferHeight; gpu.post.createBindFramebuffers(gpu.width,gpu.height);
                for (Shader pass : ((ShaderGroupAccess)gpu.post).cnpcplus$passes()) {
                    validateProgram(pass.getShaderManager());
                    if (pass.framebufferIn == pass.framebufferOut) throw new IllegalArgumentException("Post pass reads its own output");
                }
            } else {
                gpu.render = new net.minecraft.client.shader.ShaderManager(mc.getResourceManager(), definition.shaderResource);
                if (definition.json.has("blockShaderResource")) gpu.block = new net.minecraft.client.shader.ShaderManager(mc.getResourceManager(), ShaderDefinition.resource(definition.json.get("blockShaderResource").getAsString()));
                validateProgram(gpu.render); if (gpu.block != null) validateProgram(gpu.block);
            }
            LOADED.put(id,gpu); return true;
        } catch (IOException | RuntimeException error) { try (PostRenderState state = new PostRenderState()) { gpu.close(); } FAILURES.put(id,error.toString()); CnpcPlus.LOGGER.error("[shader] load {}",id,error); return false; }
    }
    private static void validateProgram(net.minecraft.client.shader.ShaderManager shader) {
        if (OpenGlHelper.glGetProgrami(shader.getProgram(),OpenGlHelper.GL_LINK_STATUS) == 0) throw new IllegalArgumentException("Shader link failed: " + OpenGlHelper.glGetProgramInfoLog(shader.getProgram(),32768));
        if (((ShaderProgramAccess)shader).cnpcplus$samplerNames().size() > 8) throw new IllegalArgumentException("At most 8 GPU samplers");
    }
    public static boolean unload(String id) { renderThread(); if (CONTROLLER.isActive(id) || OBJECTS.values().stream().anyMatch(c -> c.isActive(id))) return false; Gpu gpu = LOADED.remove(id); if (gpu == null) return false; try (PostRenderState state = new PostRenderState()) { gpu.close(); } return true; }
    public static boolean reload(String id) { renderThread(); id = ShaderDefinition.resource(id); FAILURES.remove(id); Gpu gpu = LOADED.remove(id); if (gpu != null) { try (PostRenderState state = new PostRenderState()) { gpu.close(); } } return load(id); }
    public static void reloadAll() {
        renderThread(); clear(); MANAGER = new ShaderManager(); IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        Set<ResourceLocation> discovered = new LinkedHashSet<>();
        for (FallbackResourceManager domain : ((ResourceManagerAccess)resources).cnpcplus$domains().values()) for (IResourcePack pack : ((FallbackManagerAccess)domain).cnpcplus$packs()) {
            if (!(pack instanceof AbstractResourcePack)) continue; File file = ((ResourcePackAccess)pack).cnpcplus$file();
            // Virtual packs may have no disk file; built-ins still resolve through the resource manager below.
            if (file == null) continue;
            try {
                if (file.isDirectory()) {
                    Path assets = file.toPath().resolve("assets"); if (!Files.isDirectory(assets)) continue;
                    try (java.util.stream.Stream<Path> paths = Files.walk(assets)) { paths.filter(Files::isRegularFile).forEach(path -> discover(assets.relativize(path).toString().replace('\\','/'),discovered)); }
                } else try (ZipFile zip = new ZipFile(file)) { Enumeration<? extends ZipEntry> entries = zip.entries(); while (entries.hasMoreElements()) { String name = entries.nextElement().getName(); if (name.startsWith("assets/")) discover(name.substring(7),discovered); } }
            } catch (IOException error) { CnpcPlus.LOGGER.error("[shader] scan {}",pack.getPackName(),error); }
        }
        // Discovery collects names only; resolution always uses the manager's real pack priority.
        for (String id : MANAGER.getAll()) { ResourceLocation key = new ResourceLocation(id); discovered.add(new ResourceLocation(key.getNamespace(),"cnpcplus/shaders/"+key.getPath()+".json")); }
        for (ResourceLocation key : discovered) {
            String path = key.getPath(), id = new ResourceLocation(key.getNamespace(),path.substring("cnpcplus/shaders/".length(),path.length()-5)).toString();
            try (IResource resource = resources.getResource(key); Reader reader = new InputStreamReader(resource.getInputStream(),StandardCharsets.UTF_8)) { MANAGER.register(id,new JsonParser().parse(reader).getAsJsonObject()); }
            catch (IOException | RuntimeException error) { CnpcPlus.LOGGER.error("[shader] definition {}",id,error); }
        }
        syncCatalog();
    }
    private static void discover(String name, Set<ResourceLocation> ids) {
        int slash = name.indexOf('/'); if (slash < 1) return; String path = name.substring(slash+1);
        if (path.startsWith("cnpcplus/shaders/") && path.endsWith(".json") && !path.equals("cnpcplus/shaders/catalogue.json")) ids.add(new ResourceLocation(name.substring(0,slash),path));
    }
    public static void syncCatalog() {
        if (Minecraft.getMinecraft().player == null || Minecraft.getMinecraft().getConnection() == null) return; CraftingViewNetwork.CHANNEL.sendToServer(new ShaderCatalogPacket("","")); int count = 0;
        for (String id : MANAGER.getAll()) { ShaderDefinition definition = MANAGER.get(id); if (!definition.remote) continue; String json = definition.json.toString();
            if (json.getBytes(StandardCharsets.UTF_8).length > 12288 || count >= 128) { CnpcPlus.LOGGER.error("[shader] pack catalog limit: {}",id); continue; }
            CraftingViewNetwork.CHANNEL.sendToServer(new ShaderCatalogPacket(id,json)); count++;
        }
        CraftingViewNetwork.CHANNEL.sendToServer(new ShaderCatalogPacket("","done"));
    }
    public static ShaderController objectController(String target) { return OBJECTS.computeIfAbsent(target,ShaderController::localController); }
    public static void accept(NBTTagCompound tag) {
        try { Minecraft mc = Minecraft.getMinecraft(); if (mc.world == null) return; if (world != mc.world || player != mc.player) { clear(); catalogPending = true; } world = mc.world; player = mc.player;
            if (tag.hasKey("Dimension") && tag.getInteger("Dimension") != mc.world.provider.getDimension()) return;
            ShaderInstance instance = ShaderInstance.read(tag,MANAGER); if (!instance.definition.remote) throw new IllegalArgumentException("Shader is not remotely callable: " + instance.getId());
            ShaderController controller = instance.target.equals("post") ? CONTROLLER : objectController(instance.target);
            if (!instance.isActive()) controller.instances.remove(instance.token); else { instance.controller = controller; controller.instances.put(instance.token,instance); }
        } catch (RuntimeException error) { CnpcPlus.LOGGER.error("[shader] receive",error); }
    }
    public static void clear() {
        renderThread();
        if (OpenGlHelper.shadersSupported) try (PostRenderState state = new PostRenderState()) { CONTROLLER.instances.clear(); OBJECTS.clear(); LOADED.values().forEach(Gpu::close); LOADED.clear(); TARGETS.clear(); PIPELINE.close(); }
        else { CONTROLLER.instances.clear(); OBJECTS.clear(); LOADED.clear(); TARGETS.clear(); }
        FAILURES.clear(); TEXTURE_KEYS.clear(); currentPost = null; currentPass = null; postAfterWorld = false; world = null; player = null;
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return; Minecraft mc = Minecraft.getMinecraft(); if (world != mc.world || player != mc.player) { clear(); world = mc.world; player = mc.player; catalogPending = mc.world != null; }
        if (catalogPending && mc.player != null && mc.getConnection() != null) { catalogPending = false; syncCatalog(); }
        if (mc.isGamePaused()) return; CONTROLLER.tick(); OBJECTS.values().removeIf(c -> { c.tick(); return c.instances.isEmpty(); });
        for (String id : loaded()) if (!CONTROLLER.isActive(id) && OBJECTS.values().stream().noneMatch(c -> c.isActive(id))) unload(id);
    }
    @SubscribeEvent public static void login(FMLNetworkEvent.ClientConnectedToServerEvent event) { Minecraft.getMinecraft().addScheduledTask(() -> catalogPending = true); }
    @SubscribeEvent public static void logout(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) { Minecraft.getMinecraft().addScheduledTask(() -> { catalogPending = false; clear(); }); }
    // Forge and OptiFine both dispatch this event before the hand depth clear.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void capturePost(RenderWorldLastEvent event) {
        postAfterWorld = false;
        Minecraft mc = Minecraft.getMinecraft(); if (mc.world == null || !OpenGlHelper.isFramebufferEnabled() || !hasPost()) return;
        partial = mc.isGamePaused() ? 0 : event.getPartialTicks();
        MATRIX.clear(); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,MATRIX); Matrix4f view = new Matrix4f(); view.load(MATRIX);
        Matrix4f cameraView = Matrix4f.invert(view,null); Entity viewer = mc.getRenderViewEntity();
        camera = new Vec3d(viewer.lastTickPosX+(viewer.posX-viewer.lastTickPosX)*partial+cameraView.m30,viewer.lastTickPosY+(viewer.posY-viewer.lastTickPosY)*partial+cameraView.m31,viewer.lastTickPosZ+(viewer.posZ-viewer.lastTickPosZ)*partial+cameraView.m32);
        view.m30 = view.m31 = view.m32 = 0; MATRIX.clear(); GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,MATRIX);
        Matrix4f projection = new Matrix4f(); projection.load(MATRIX); Matrix4f.mul(projection,view,VIEW_PROJECTION); Matrix4f.invert(VIEW_PROJECTION,INVERSE);
        // Native ShaderUniform uploads with transpose=true; adapt only our column-major matrices, not vanilla ProjMat.
        Matrix4f.transpose(VIEW_PROJECTION,VIEW_PROJECTION); Matrix4f.transpose(INVERSE,INVERSE);
        Framebuffer main = mc.getFramebuffer();
        int worldDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        try {
            if (worldDraw == main.framebufferObject) PIPELINE.captureDepth(worldDraw,0,0,main.framebufferWidth,main.framebufferHeight);
            else { java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_VIEWPORT,viewport); PIPELINE.captureDepth(worldDraw,viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3)); }
            postAfterWorld = true;
        } catch (RuntimeException error) { for (ShaderInstance instance : CONTROLLER.instances.values()) if (instance.isActive()) fail(instance,error.toString()); }
    }

    public static void beginFrame() {
        postAfterWorld = false; PIPELINE.beginFrame();
        PIPELINE.retireHistories(CONTROLLER.instances);
    }
    private static boolean hasPost() {
        boolean found = false;
        for (ShaderInstance instance : CONTROLLER.instances.values()) if (instance.isActive()) {
            if (FAILURES.containsKey(instance.getId())) fail(instance,FAILURES.get(instance.getId()));
            else if (load(instance.getId())) found = true; else fail(instance,FAILURES.get(instance.getId()));
        }
        return found;
    }

    public static void renderPostAfterWorld() {
        if (!postAfterWorld) return;
        postAfterWorld = false;
        Minecraft mc = Minecraft.getMinecraft();
        renderPost(mc, mc.getFramebuffer());
    }

    private static void renderPost(Minecraft mc, Framebuffer main) {
        if (!hasPost()) return;
        int width = main.framebufferWidth, height = main.framebufferHeight;
        try (PostRenderState state = new PostRenderState()) {
            PIPELINE.captureScene(main);
            boolean rendered = false;
            ShaderInstance[] effects = CONTROLLER.getAllInstances(); Arrays.sort(effects,Comparator.comparingInt(ShaderInstance::getPriority));
            for (ShaderInstance instance : effects) {
                if (!instance.isActive() || FAILURES.containsKey(instance.getId())) continue;
                currentPost = instance; Gpu gpu = LOADED.get(instance.getId());
                PIPELINE.beginEffect(instance.token,instance.textures.containsValue("cnpcplus:history") || instance.textures.containsValue("cnpcplus:history_2") || instance.textures.containsValue("cnpcplus:history_3"));
                try {
                    if (gpu.width != width || gpu.height != height) { gpu.post.createBindFramebuffers(width,height); gpu.width = width; gpu.height = height; }
                    gpu.post.render(partial);
                    PIPELINE.finishEffect(instance.token,!mc.isGamePaused() && !instance.isPaused());
                    rendered = true;
                } catch (RuntimeException error) { PIPELINE.rollback(); fail(instance,error.toString()); }
                finally { currentPass = null; }
            }
            if (rendered) PIPELINE.present(main);
        } catch (RuntimeException error) { if (currentPost != null) fail(currentPost,error.toString()); }
        finally { currentPost = null; currentPass = null; }
    }
    public static boolean preserveMainDepth(Framebuffer framebuffer) { return currentPost != null && PIPELINE.isTarget(framebuffer); }
    private static void fail(ShaderInstance instance, String message) { instance.error = message == null ? "GPU resource loading failed" : message; instance.state = "ERROR"; if (!FAILURES.containsKey(instance.getId())) { FAILURES.put(instance.getId(),instance.error); CnpcPlus.LOGGER.error("[shader] {} {}",instance.getId(),instance.error); } }
    public static void preparePost(Shader pass) {
        if (currentPost == null) return;
        currentPass = pass; bindPost(pass.getShaderManager());
        ShaderProgramAccess access = (ShaderProgramAccess)pass.getShaderManager();
        for (String name : access.cnpcplus$samplerNames()) {
            Object value = access.cnpcplus$samplers().get(name);
            int texture = value instanceof Framebuffer ? ((Framebuffer)value).framebufferTexture : value instanceof Integer ? (Integer)value : value instanceof net.minecraft.client.renderer.texture.ITextureObject ? ((net.minecraft.client.renderer.texture.ITextureObject)value).getGlTextureId() : -1;
            if (texture == pass.framebufferOut.framebufferTexture) throw new IllegalArgumentException("Post sampler reads its own output: " + name);
        }
        PostRenderState.beforeDraw(); GlStateManager.depthMask(true); GL11.glDepthMask(true); ShaderProgramAccess.cnpcplus$program(-1);
    }
    public static void preparePostDraw() {
        if (currentPost == null || currentPass == null) return;
        PostRenderState.beforeDraw();
        // Allocation/clear and external render hooks may have changed these after preparePost.
        currentPass.framebufferOut.bindFramebuffer(false);
        GlStateManager.viewport(0,0,currentPass.framebufferOut.framebufferTextureWidth,currentPass.framebufferOut.framebufferTextureHeight);
        OpenGlHelper.glUseProgram(currentPass.getShaderManager().getProgram());
        ShaderProgramAccess access = (ShaderProgramAccess)currentPass.getShaderManager();
        int unit = 0;
        for (String name : access.cnpcplus$samplerNames()) {
            Object value = access.cnpcplus$samplers().get(name);
            int texture = value instanceof Framebuffer ? ((Framebuffer)value).framebufferTexture : value instanceof Integer ? (Integer)value : value instanceof net.minecraft.client.renderer.texture.ITextureObject ? ((net.minecraft.client.renderer.texture.ITextureObject)value).getGlTextureId() : -1;
            PostRenderState.activeTexture(OpenGlHelper.defaultTexUnit+unit++); if (texture >= 0) PostRenderState.bindTexture(texture);
        }
        PostRenderState.activeTexture(OpenGlHelper.defaultTexUnit);
        ShaderProgramAccess.cnpcplus$program(currentPass.getShaderManager().getProgram());
        ShaderProgramAccess.cnpcplus$manager(currentPass.getShaderManager());
        String fragment = currentPass.getShaderManager().getFragmentShaderLoader().getShaderFilename();
        if (fragment.equals("cnpcplus:script_blit")) { GlStateManager.disableBlend(); GL11.glDisable(GL11.GL_BLEND); }
    }
    public static void preparePostUpload() {
        if (currentPost == null || currentPass == null) return;
        PostRenderState.clientArrays(); preparePostDraw();
    }
    public static void bindPost(net.minecraft.client.shader.ShaderManager shader) {
        if (currentPost == null) return; bindUniforms(currentPost,shader,partial);
        shader.getShaderUniformOrDefault("WorldViewProjection").set(VIEW_PROJECTION); shader.getShaderUniformOrDefault("InverseWorldViewProjection").set(INVERSE);
        shader.getShaderUniformOrDefault("CameraPosition").set((float)camera.x,(float)camera.y,(float)camera.z);
        Entity follow = null; Minecraft mc = Minecraft.getMinecraft();
        if (currentPost.followEntity != null) { for (Entity entity : mc.world.loadedEntityList) if (entity.getUniqueID().equals(currentPost.followEntity)) { follow = entity; break; } }
        else if (currentPost.definition.json.has("followPlayer") && currentPost.definition.json.get("followPlayer").getAsBoolean()) follow = mc.player;
        Vec3d position = currentPost.worldPosition(partial);
        if (follow != null) position = new Vec3d(follow.lastTickPosX+(follow.posX-follow.lastTickPosX)*partial,follow.lastTickPosY+(follow.posY-follow.lastTickPosY)*partial+follow.height/2,follow.lastTickPosZ+(follow.posZ-follow.lastTickPosZ)*partial);
        if (position != null) {
            shader.getShaderUniformOrDefault("WorldPosition").set((float)position.x,(float)position.y,(float)position.z);
            shader.getShaderUniformOrDefault("CameraRelativeWorldPosition").set((float)(position.x-camera.x),(float)(position.y-camera.y),(float)(position.z-camera.z));
        }
        List<String> samplers = ((ShaderProgramAccess)shader).cnpcplus$samplerNames();
        currentPost.textures.forEach((name,id) -> { if (!name.equals("DiffuseSampler") && samplers.contains(name)) shader.addSamplerTexture(name,texture(id)); });
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
    private static int texture(String id) {
        Minecraft mc = Minecraft.getMinecraft(); if (id.equals("minecraft:main")) return currentPost == null ? mc.getFramebuffer().framebufferTexture : PIPELINE.sceneColor(); if (id.equals("minecraft:depth")) return PIPELINE.sceneDepth();
        if (id.equals("cnpcplus:history") || id.equals("cnpcplus:history_2") || id.equals("cnpcplus:history_3")) return PIPELINE.history(currentPost.token,id.equals("cnpcplus:history") ? 0 : id.equals("cnpcplus:history_2") ? 1 : 2);
        ResourceLocation key = TEXTURE_KEYS.get(id); if (key == null) { if (TEXTURE_KEYS.size() >= 256) TEXTURE_KEYS.remove(TEXTURE_KEYS.keySet().iterator().next()); key = new ResourceLocation(id); TEXTURE_KEYS.put(id,key); }
        if (mc.getTextureManager().getTexture(key) == null) mc.getTextureManager().bindTexture(key); return mc.getTextureManager().getTexture(key).getGlTextureId();
    }
    private static void bindUniforms(ShaderInstance instance, net.minecraft.client.shader.ShaderManager shader, float framePartial) {
        float now = instance.time+(instance.isPaused() ? 0 : framePartial*instance.speed);
        instance.uniforms.forEach((name,value) -> {
            ShaderUniform uniform = shader.getShaderUniform(name); if (uniform == null) return;
            int expected = uniformType(instance.getUniformType(name));
            if (((ShaderUniformAccess)uniform).cnpcplus$type() != expected) throw new IllegalArgumentException("GPU uniform type mismatch: " + name);
            float[] data = instance.animations.containsKey(name) ? instance.animations.get(name).value(now) : value;
            ShaderUniformAccess access = (ShaderUniformAccess)uniform; if (access.cnpcplus$count() != data.length) throw new IllegalArgumentException("GPU uniform length mismatch: " + name);
            if (name.equals("Time") || name.equals("Opacity")) {
                if (data.length != 1) throw new IllegalArgumentException("Scalar GPU uniform required: " + name);
                float scalar = name.equals("Time") ? data[0]+now/20 : data[0]*instance.opacity(framePartial);
                if (access.cnpcplus$type() <= 3) uniform.set((int)scalar,0,0,0); else uniform.set(scalar); return;
            }
            // Script matrix4 stays column-major, including animated values; native upload expects row-major.
            if (access.cnpcplus$type() <= 3) uniform.set((int)data[0],data.length>1 ? (int)data[1] : 0,data.length>2 ? (int)data[2] : 0,data.length>3 ? (int)data[3] : 0); else if (data.length == 16) uniform.set(data[0],data[4],data[8],data[12],data[1],data[5],data[9],data[13],data[2],data[6],data[10],data[14],data[3],data[7],data[11],data[15]); else uniform.set(data);
        });
    }
    private static int uniformType(String type) {
        switch (type) {
            case "int": case "bool": return 0;
            case "float": return 4;
            case "vec2": return 5;
            case "vec3": return 6;
            case "vec4": return 7;
            case "matrix4": return 10;
            default: throw new IllegalArgumentException("Unsupported script uniform type: " + type);
        }
    }
    public static void pushTarget(String target) { TARGETS.push(target); }
    public static void pushItem(ItemStack stack) { NBTTagCompound tag = stack.getTagCompound(); pushTarget(tag != null && tag.hasUniqueId("CNPCPlusShaderItem") ? "item:"+tag.getUniqueId("CNPCPlusShaderItem") : ""); }
    public static void popTarget() { TARGETS.pop(); }
    public static void beginDraw() {
        if (currentPost != null) { if (currentPass != null) preparePostDraw(); return; }
        if (TARGETS.isEmpty() || compilingList) return;
        ShaderInstance selected = null; String selectedTarget = "";
        for (String target : TARGETS) { ShaderController controller = OBJECTS.get(target); if (controller == null) continue; for (ShaderInstance instance : controller.instances.values()) if (instance.isActive() && (selected == null || instance.priority > selected.priority)) selected = instance; if (selected != null) { selectedTarget = target; break; } }
        if (selected == null) return;
        try {
            if (!load(selected.getId())) { fail(selected,"GPU resource loading failed"); return; }
            Gpu gpu = LOADED.get(selected.getId()); net.minecraft.client.shader.ShaderManager shader = selectedTarget.startsWith("block:") && gpu.block != null ? gpu.block : gpu.render;
            previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM); previousManager = ShaderProgramAccess.cnpcplus$manager(); previousTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); OpenGlHelper.glUseProgram(shader.getProgram()); ShaderProgramAccess.cnpcplus$program(shader.getProgram()); ShaderProgramAccess.cnpcplus$manager(shader); bindUniforms(selected,shader,Minecraft.getMinecraft().isGamePaused() ? 0 : Minecraft.getMinecraft().getRenderPartialTicks());
            shader.getShaderUniformOrDefault("Lighting").set(GL11.glIsEnabled(GL11.GL_LIGHTING) ? 1 : 0,0,0,0);
            shader.getShaderUniformOrDefault("FogMode").set(GL11.glIsEnabled(GL11.GL_FOG) ? GL11.glGetInteger(GL11.GL_FOG_MODE) : 0,0,0,0);
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit); shader.getShaderUniformOrDefault("Textured").set(GL11.glIsEnabled(GL11.GL_TEXTURE_2D) ? 1 : 0,0,0,0);
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit); shader.getShaderUniformOrDefault("Lightmap").set(GL11.glIsEnabled(GL11.GL_TEXTURE_2D) ? 1 : 0,0,0,0);
            ((ShaderProgramAccess)shader).cnpcplus$uniforms().forEach(ShaderUniform::upload);
            OpenGlHelper.glUniform1i(OpenGlHelper.glGetUniformLocation(shader.getProgram(),"Sampler0"),0);
            OpenGlHelper.glUniform1i(OpenGlHelper.glGetUniformLocation(shader.getProgram(),"Sampler2"),1);
            int unit = 2; for (Map.Entry<String,String> entry : selected.textures.entrySet()) {
                GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit+unit); RESTORE_TEXTURES.put(unit,GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)); GlStateManager.bindTexture(texture(entry.getValue()));
                OpenGlHelper.glUniform1i(OpenGlHelper.glGetUniformLocation(shader.getProgram(),entry.getKey()),unit++);
            } GlStateManager.setActiveTexture(previousTextureUnit);
        } catch (RuntimeException error) { fail(selected,error.toString()); endDraw(); }
    }
    public static void endDraw() {
        if (previousProgram < 0) return; OpenGlHelper.glUseProgram(previousProgram); ShaderProgramAccess.cnpcplus$program(previousProgram); ShaderProgramAccess.cnpcplus$manager(previousManager); previousManager = null; previousProgram = -1;
        RESTORE_TEXTURES.forEach((unit,texture) -> { GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit+unit); GlStateManager.bindTexture(texture); }); RESTORE_TEXTURES.clear(); GlStateManager.setActiveTexture(previousTextureUnit);
    }
}
