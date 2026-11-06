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
    private static final Map<UUID, Framebuffer[]> HISTORIES = new HashMap<>();
    private static final Deque<String> TARGETS = new ArrayDeque<>();
    private static final Matrix4f VIEW_PROJECTION = new Matrix4f(), INVERSE = new Matrix4f();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static Object world, player;
    private static boolean catalogPending;
    private static ShaderInstance currentPost;
    private static Vec3d camera = Vec3d.ZERO;
    private static int depth = -1, width, height, previousProgram = -1;
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
        renderThread(); if (isLoaded(id)) return true; ShaderDefinition definition = MANAGER.get(id); Minecraft mc = Minecraft.getMinecraft(); Gpu gpu = new Gpu();
        try {
            if (!OpenGlHelper.shadersSupported || !OpenGlHelper.isFramebufferEnabled()) throw new IllegalStateException("OpenGL shaders/framebuffers are disabled");
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
                gpu.post = new ShaderGroup(mc.getTextureManager(), mc.getResourceManager(), mc.getFramebuffer(), new ResourceLocation(definition.shaderResource));
                gpu.width = mc.getFramebuffer().framebufferWidth; gpu.height = mc.getFramebuffer().framebufferHeight; gpu.post.createBindFramebuffers(gpu.width,gpu.height);
                for (String name : definition.uniforms.keySet()) if (((ShaderGroupAccess)gpu.post).cnpcplus$passes().stream().noneMatch(p -> p.getShaderManager().getShaderUniform(name) != null)) throw new IllegalArgumentException(id + ": GPU uniform missing: " + name);
            } else {
                gpu.render = new net.minecraft.client.shader.ShaderManager(mc.getResourceManager(), definition.shaderResource);
                if (definition.json.has("blockShaderResource")) gpu.block = new net.minecraft.client.shader.ShaderManager(mc.getResourceManager(), ShaderDefinition.resource(definition.json.get("blockShaderResource").getAsString()));
                for (net.minecraft.client.shader.ShaderManager shader : gpu.block == null ? new net.minecraft.client.shader.ShaderManager[]{gpu.render} : new net.minecraft.client.shader.ShaderManager[]{gpu.render,gpu.block})
                    for (String name : definition.uniforms.keySet()) if (shader.getShaderUniform(name) == null) throw new IllegalArgumentException(id + ": GPU uniform missing: " + name);
            }
            LOADED.put(id,gpu); return true;
        } catch (IOException | RuntimeException error) { gpu.close(); CnpcPlus.LOGGER.error("[shader] load {}",id,error); return false; }
    }
    public static boolean unload(String id) { renderThread(); if (CONTROLLER.isActive(id) || OBJECTS.values().stream().anyMatch(c -> c.isActive(id))) return false; Gpu gpu = LOADED.remove(id); if (gpu == null) return false; gpu.close(); return true; }
    public static boolean reload(String id) { renderThread(); Gpu gpu = LOADED.remove(id); if (gpu != null) gpu.close(); return load(id); }
    public static void reloadAll() {
        renderThread(); clear(); MANAGER = new ShaderManager(); IResourceManager resources = Minecraft.getMinecraft().getResourceManager();
        Set<ResourceLocation> discovered = new LinkedHashSet<>();
        for (FallbackResourceManager domain : ((ResourceManagerAccess)resources).cnpcplus$domains().values()) for (IResourcePack pack : ((FallbackManagerAccess)domain).cnpcplus$packs()) {
            if (!(pack instanceof AbstractResourcePack)) continue; File file = ((ResourcePackAccess)pack).cnpcplus$file();
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
        renderThread(); CONTROLLER.instances.clear(); OBJECTS.clear(); LOADED.values().forEach(Gpu::close); LOADED.clear(); TARGETS.clear();
        HISTORIES.values().forEach(targets -> Arrays.stream(targets).forEach(Framebuffer::deleteFramebuffer)); HISTORIES.clear();
        if (depth >= 0) GlStateManager.deleteTexture(depth); depth = -1; currentPost = null; world = null; player = null;
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
    public static void renderPost(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft(); if (mc.world == null || CONTROLLER.instances.isEmpty()) return;
        partial = mc.isGamePaused() ? 0 : event.getPartialTicks(); Framebuffer main = mc.getFramebuffer();
        MATRIX.clear(); GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,MATRIX); Matrix4f view = new Matrix4f(); view.load(MATRIX);
        Matrix4f cameraView = Matrix4f.invert(view,null); Entity viewer = mc.getRenderViewEntity();
        camera = new Vec3d(viewer.lastTickPosX+(viewer.posX-viewer.lastTickPosX)*partial+cameraView.m30,viewer.lastTickPosY+(viewer.posY-viewer.lastTickPosY)*partial+cameraView.m31,viewer.lastTickPosZ+(viewer.posZ-viewer.lastTickPosZ)*partial+cameraView.m32);
        view.m30 = view.m31 = view.m32 = 0; MATRIX.clear(); GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX,MATRIX);
        Matrix4f projection = new Matrix4f(); projection.load(MATRIX); Matrix4f.mul(projection,view,VIEW_PROJECTION); Matrix4f.invert(VIEW_PROJECTION,INVERSE);
        // Native ShaderUniform uploads with transpose=true; adapt only our column-major matrices, not vanilla ProjMat.
        Matrix4f.transpose(VIEW_PROJECTION,VIEW_PROJECTION); Matrix4f.transpose(INVERSE,INVERSE);
        int oldUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE), oldProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        net.minecraft.client.shader.ShaderManager oldManager = ShaderProgramAccess.cnpcplus$manager();
        int[] bindings = new int[8]; boolean[] textures = new boolean[8];
        for (int i = 0; i < 8; i++) { GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit+i); bindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D); textures[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D); }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), alpha = GL11.glIsEnabled(GL11.GL_ALPHA_TEST), fog = GL11.glIsEnabled(GL11.GL_FOG), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), material = GL11.glIsEnabled(GL11.GL_COLOR_MATERIAL), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB), srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA), equation = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        java.nio.IntBuffer viewport = BufferUtils.createIntBuffer(16); GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
        java.nio.ByteBuffer colorMask = BufferUtils.createByteBuffer(16); GL11.glGetBoolean(GL11.GL_COLOR_WRITEMASK, colorMask);
        FloatBuffer clearColor = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE); int matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        FloatBuffer color = BufferUtils.createFloatBuffer(16); GL11.glGetFloat(GL11.GL_CURRENT_COLOR,color);
        if (depth < 0 || width != main.framebufferWidth || height != main.framebufferHeight) {
            if (depth >= 0) GlStateManager.deleteTexture(depth); width = main.framebufferWidth; height = main.framebufferHeight; depth = GlStateManager.generateTexture(); GlStateManager.bindTexture(depth);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL14.GL_DEPTH_COMPONENT24,width,height,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,(java.nio.ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST); GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE); GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }
        main.bindFramebuffer(false); GlStateManager.bindTexture(depth); GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,width,height); GlStateManager.bindTexture(bindings[0]);
        HISTORIES.entrySet().removeIf(entry -> { if (CONTROLLER.instances.containsKey(entry.getKey())) return false; Arrays.stream(entry.getValue()).forEach(Framebuffer::deleteFramebuffer); return true; });
        GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.pushMatrix(); GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.pushMatrix();
        try {
            GlStateManager.disableCull();
            ShaderInstance[] effects = CONTROLLER.getAllInstances(); Arrays.sort(effects,Comparator.comparingInt(ShaderInstance::getPriority));
            for (ShaderInstance instance : effects) {
                currentPost = instance; if (!load(instance.getId())) { fail(instance,"GPU resource loading failed"); continue; } Gpu gpu = LOADED.get(instance.getId());
                if (gpu.width != width || gpu.height != height) { gpu.post.createBindFramebuffers(width,height); gpu.width = width; gpu.height = height; }
                Framebuffer[] history = null;
                if (instance.textures.containsKey("HistorySampler")) {
                    history = HISTORIES.get(instance.token);
                    if (history == null || history[0].framebufferWidth != width || history[0].framebufferHeight != height) {
                        if (history != null) Arrays.stream(history).forEach(Framebuffer::deleteFramebuffer); history = new Framebuffer[3];
                        for (int i = 0; i < 3; i++) { history[i] = new Framebuffer(width,height,false); copyColor(main,history[i]); } HISTORIES.put(instance.token,history);
                    }
                }
                gpu.post.render(partial);
                if (history != null && !mc.isGamePaused() && !instance.isPaused()) { Framebuffer oldest = history[2]; history[2] = history[1]; history[1] = history[0]; history[0] = oldest; copyColor(main,oldest); }
            }
        } catch (RuntimeException error) { if (currentPost != null) fail(currentPost,error.toString()); }
        finally {
            currentPost = null; main.bindFramebuffer(true); GlStateManager.matrixMode(GL11.GL_MODELVIEW); GlStateManager.popMatrix(); GlStateManager.matrixMode(GL11.GL_PROJECTION); GlStateManager.popMatrix(); GlStateManager.matrixMode(GL11.GL_MODELVIEW);
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend(); GlStateManager.tryBlendFuncSeparate(src,dst,srcAlpha,dstAlpha); GlStateManager.glBlendEquation(equation);
            if (depthTest) GlStateManager.enableDepth(); else GlStateManager.disableDepth(); GlStateManager.depthMask(depthMask);
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha(); if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting(); if (material) GlStateManager.enableColorMaterial(); else GlStateManager.disableColorMaterial();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull(); GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
            GlStateManager.colorMask(colorMask.get(0) != 0,colorMask.get(1) != 0,colorMask.get(2) != 0,colorMask.get(3) != 0);
            GlStateManager.clearColor(clearColor.get(0),clearColor.get(1),clearColor.get(2),clearColor.get(3)); GlStateManager.clearDepth(clearDepth);
            GlStateManager.viewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3)); GlStateManager.matrixMode(matrixMode);
            for (int i = 0; i < 8; i++) { GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit+i); GlStateManager.bindTexture(bindings[i]); if (textures[i]) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D(); }
            GlStateManager.setActiveTexture(oldUnit); OpenGlHelper.glUseProgram(oldProgram); ShaderProgramAccess.cnpcplus$program(oldProgram); ShaderProgramAccess.cnpcplus$manager(oldManager); ShaderBlendAccess.cnpcplus$invalidate(null);
        }
    }
    private static void copyColor(Framebuffer source, Framebuffer target) { GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit); source.bindFramebuffer(false); GlStateManager.bindTexture(target.framebufferTexture); GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,source.framebufferWidth,source.framebufferHeight); }
    public static boolean preserveMainDepth(Framebuffer framebuffer) { return currentPost != null && framebuffer == Minecraft.getMinecraft().getFramebuffer(); }
    private static void fail(ShaderInstance instance, String message) { instance.error = message; instance.state = "ERROR"; CnpcPlus.LOGGER.error("[shader] {} {}",instance.getId(),message); }
    public static void bindPost(net.minecraft.client.shader.ShaderManager shader) {
        if (currentPost == null) return; bindUniforms(currentPost,shader,partial);
        shader.getShaderUniformOrDefault("WorldViewProjection").set(VIEW_PROJECTION); shader.getShaderUniformOrDefault("InverseWorldViewProjection").set(INVERSE);
        shader.getShaderUniformOrDefault("CameraPosition").set((float)camera.x,(float)camera.y,(float)camera.z);
        Entity follow = null; Minecraft mc = Minecraft.getMinecraft();
        if (currentPost.followEntity != null) { for (Entity entity : mc.world.loadedEntityList) if (entity.getUniqueID().equals(currentPost.followEntity)) { follow = entity; break; } }
        else if (currentPost.definition.json.has("followPlayer") && currentPost.definition.json.get("followPlayer").getAsBoolean()) follow = mc.player;
        if (follow != null) shader.getShaderUniformOrDefault("WorldPosition").set((float)(follow.lastTickPosX+(follow.posX-follow.lastTickPosX)*partial),(float)(follow.lastTickPosY+(follow.posY-follow.lastTickPosY)*partial)+follow.height/2,(float)(follow.lastTickPosZ+(follow.posZ-follow.lastTickPosZ)*partial));
        currentPost.textures.forEach((name,id) -> { if (!name.equals("DiffuseSampler")) shader.addSamplerTexture(name,texture(id)); });
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
    }
    private static int texture(String id) {
        Minecraft mc = Minecraft.getMinecraft(); if (id.equals("minecraft:main")) return mc.getFramebuffer().framebufferTexture; if (id.equals("minecraft:depth")) return depth;
        if (id.equals("cnpcplus:history") || id.equals("cnpcplus:history_2") || id.equals("cnpcplus:history_3")) return HISTORIES.get(currentPost.token)[id.equals("cnpcplus:history") ? 0 : id.equals("cnpcplus:history_2") ? 1 : 2].framebufferTexture;
        ResourceLocation key = new ResourceLocation(id); if (mc.getTextureManager().getTexture(key) == null) mc.getTextureManager().bindTexture(key); return mc.getTextureManager().getTexture(key).getGlTextureId();
    }
    private static void bindUniforms(ShaderInstance instance, net.minecraft.client.shader.ShaderManager shader, float framePartial) {
        float now = instance.time+(instance.isPaused() ? 0 : framePartial*instance.speed);
        instance.uniforms.forEach((name,value) -> {
            ShaderUniform uniform = shader.getShaderUniform(name); if (uniform == null) return;
            float[] data = instance.animations.containsKey(name) ? instance.animations.get(name).value(now) : value;
            if (name.equals("Time")) data = new float[]{data[0]+now/20}; if (name.equals("Opacity")) data = new float[]{data[0]*instance.opacity(framePartial)};
            ShaderUniformAccess access = (ShaderUniformAccess)uniform; if (access.cnpcplus$count() != data.length) throw new IllegalArgumentException("GPU uniform length mismatch: " + name);
            // Script matrix4 stays column-major, including animated values; native upload expects row-major.
            if (access.cnpcplus$type() <= 3) uniform.set((int)data[0],data.length>1 ? (int)data[1] : 0,data.length>2 ? (int)data[2] : 0,data.length>3 ? (int)data[3] : 0); else if (data.length == 16) uniform.set(data[0],data[4],data[8],data[12],data[1],data[5],data[9],data[13],data[2],data[6],data[10],data[14],data[3],data[7],data[11],data[15]); else uniform.set(data);
        });
    }
    public static void pushTarget(String target) { TARGETS.push(target); }
    public static void pushItem(ItemStack stack) { NBTTagCompound tag = stack.getTagCompound(); pushTarget(tag != null && tag.hasUniqueId("CNPCPlusShaderItem") ? "item:"+tag.getUniqueId("CNPCPlusShaderItem") : ""); }
    public static void popTarget() { TARGETS.pop(); }
    public static void beginDraw() {
        if (TARGETS.isEmpty() || currentPost != null || compilingList) return;
        ShaderInstance selected = null; String selectedTarget = "";
        for (String target : TARGETS) { ShaderController controller = OBJECTS.get(target); if (controller == null) continue; selected = Arrays.stream(controller.getAllInstances()).max(Comparator.comparingInt(ShaderInstance::getPriority)).orElse(null); if (selected != null) { selectedTarget = target; break; } }
        if (selected == null) return;
        try {
            if (!load(selected.getId())) { fail(selected,"GPU resource loading failed"); return; }
            Gpu gpu = LOADED.get(selected.getId()); net.minecraft.client.shader.ShaderManager shader = selectedTarget.startsWith("block:") && gpu.block != null ? gpu.block : gpu.render;
            previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM); previousTextureUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE); OpenGlHelper.glUseProgram(shader.getProgram()); bindUniforms(selected,shader,Minecraft.getMinecraft().isGamePaused() ? 0 : Minecraft.getMinecraft().getRenderPartialTicks());
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
        if (previousProgram < 0) return; OpenGlHelper.glUseProgram(previousProgram); previousProgram = -1;
        RESTORE_TEXTURES.forEach((unit,texture) -> { GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit+unit); GlStateManager.bindTexture(texture); }); RESTORE_TEXTURES.clear(); GlStateManager.setActiveTexture(previousTextureUnit);
    }
}
