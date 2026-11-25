package top.cnpcplus;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import static org.lwjgl.opengl.GL33C.*;

/** Actual compiled handlers with small GUI/entity shells, plus native injection-site checks. No game launch. */
public final class NpcEditorRegression {
    private static final String PREFIX = "top/cnpcplus/mixin/";
    private static final String PROJECTILE = "noppes/npcs/entity/EntityProjectile";
    private static final String BUTTON = "noppes/npcs/shared/client/gui/components/GuiButtonNop";
    private static final String GUI = "noppes/npcs/shared/client/gui/listeners/IGuiInterface";

    public static void main(String[] args) throws Exception {
        int sites = 0;
        for (String name : List.of("MixinProjectileWallStick", "MixinMovingPathImmediateSync", "MixinNpcEditorSelections",
                "MixinSubGuiNpcMovementChoices", "MixinNpcLootModeSelect", "MixinGuiNPCInv", "MixinTextureSelectionDefaults", "MixinRenderCustomNpcLayers")) {
            ClassNode mixin = read(PREFIX + name);
            AnnotationNode spec = annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations).stream()
                    .filter(a -> a.desc.endsWith("/Mixin;")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") List<Type> targets = (List<Type>)value(spec, "value");
            for (Type type : targets) {
                ClassNode target = read(type.getInternalName());
                for (MethodNode handler : mixin.methods) for (AnnotationNode annotation : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                    if (!List.of("Inject", "Redirect").stream().anyMatch(n -> annotation.desc.endsWith("/" + n + ";"))) continue;
                    @SuppressWarnings("unchecked") List<String> methods = (List<String>)value(annotation, "method");
                    Object atValue = value(annotation, "at");
                    AnnotationNode at = atValue instanceof AnnotationNode node ? node : (AnnotationNode)((List<?>)atValue).get(0);
                    String kind = (String)value(at, "value"), reference = (String)value(at, "target");
                    int ordinal = value(at, "ordinal") instanceof Integer n ? n : -1;
                    for (String methodName : methods) {
                        List<MethodNode> matches = target.methods.stream().filter(m -> methodName.equals(m.name) || methodName.equals(m.name + m.desc)).toList();
                        check(!matches.isEmpty(), name + " missing target " + methodName);
                        for (MethodNode method : matches) {
                            if (reference != null) {
                                List<AbstractInsnNode> points = Arrays.stream(method.instructions.toArray()).filter(i ->
                                        kind.equals("NEW") && i instanceof TypeInsnNode allocation && allocation.getOpcode() == Opcodes.NEW
                                        && allocation.desc.equals(Type.getReturnType(reference).getInternalName()) ||
                                        i instanceof MethodInsnNode call && reference.equals("L" + call.owner + ";" + call.name + call.desc)
                                        || i instanceof FieldInsnNode field && reference.equals("L" + field.owner + ";" + field.name + ":" + field.desc)
                                        && (!(value(at, "opcode") instanceof Integer opcode) || opcode == i.getOpcode())).toList();
                                check(ordinal >= 0 ? ordinal < points.size() : points.size() == 1,
                                        name + "." + handler.name + " ambiguous/missing " + reference + " (" + points.size() + ")");
                            } else check(List.of("HEAD", "TAIL", "RETURN").contains(kind), "unchecked injection kind " + kind);
                            sites++;
                        }
                    }
                }
            }
        }
        MethodNode nativeHit = read(PROJECTILE).methods.stream().filter(m -> m.name.equals("m_6532_")).findFirst().orElseThrow();
        List<MethodInsnNode> arrowCalls = Arrays.stream(nativeHit.instructions.toArray()).filter(i -> i instanceof MethodInsnNode call
                && call.owner.equals(PROJECTILE) && call.name.equals("isArrow")).map(i -> (MethodInsnNode)i).toList();
        check(arrowCalls.size() == 5, "native entity/block impact layout changed");
        for (int ordinal : new int[]{3, 4}) {
            AbstractInsnNode next = arrowCalls.get(ordinal).getNext();
            while (next.getOpcode() < 0) next = next.getNext();
            check(next.getOpcode() == Opcodes.IFNE, "block stick/survival is no longer an OR decision");
        }
        projectileHandlers();
        pickerConfirmation();
        inventorySubGuiRenderGuard();
        visualControls();
        pathOverlay();
        System.out.println("NPC_EDITOR_REGRESSION=PASS (" + sites + " native injection sites; editor controls and route geometry/pixel verification; no Minecraft launch)");
    }

    public static class ProjectileShell {
        public boolean arrow, wall, threeD;
        public Vec3 pos = new Vec3(100, 64, 100), motion = new Vec3(2, 0, 0);
        public boolean isArrow() { return arrow; }
        public boolean sticksToWalls() { return (arrow || threeD) && wall; }
        public DataShell getEntityData() { return new DataShell(wall); }
        public Vec3 position() { return pos; }
        public void setPos(Vec3 value) { pos = value; }
        public void setDeltaMovement(Vec3 value) { motion = value; }
    }
    public record DataShell(boolean wall) {
        @SuppressWarnings("unchecked") public <T> T get(EntityDataAccessor<T> key) { return (T)Boolean.valueOf(wall); }
    }

    private static void projectileHandlers() throws Exception {
        ClassNode node = read(PREFIX + "MixinProjectileWallStick");
        String name = "top/cnpcplus/NpcWallProbe";
        Map<String, String> map = Map.of(node.name, name, PROJECTILE, Type.getInternalName(ProjectileShell.class),
                "net/minecraft/network/syncher/SynchedEntityData", Type.getInternalName(DataShell.class));
        ClassNode probe = new ClassNode(); node.accept(new ClassRemapper(probe, new SimpleRemapper(map)));
        probe.access &= ~Opcodes.ACC_ABSTRACT;
        probe.superName = Type.getInternalName(ProjectileShell.class);
        for (MethodNode method : probe.methods) if (method.name.equals("<init>")) for (var i : method.instructions)
            if (i instanceof MethodInsnNode call && call.name.equals("<init>")) call.owner = probe.superName;
        Class<?> type = define(probe);
        ProjectileShell self = (ProjectileShell)type.getConstructor().newInstance();
        Method setting = type.getDeclaredMethod("cnpcplus$arrowWallSetting", CallbackInfoReturnable.class); setting.setAccessible(true);
        Method ground = type.getDeclaredMethod("cnpcplus$groundUsesSetting", ProjectileShell.class); ground.setAccessible(true);
        Method block = type.getDeclaredMethod("cnpcplus$blockImpactUsesSetting", ProjectileShell.class, HitResult.class); block.setAccessible(true);
        Method survive = type.getDeclaredMethod("cnpcplus$discardUnstuckArrow", ProjectileShell.class, HitResult.class); survive.setAccessible(true);
        Method stop = type.getDeclaredMethod("cnpcplus$stopAtSurface", HitResult.class, CallbackInfo.class); stop.setAccessible(true);
        for (boolean arrow : new boolean[]{false, true}) for (boolean wall : new boolean[]{false, true}) for (boolean threeD : new boolean[]{false, true}) {
            self.arrow = arrow; self.wall = wall; self.threeD = threeD;
            var callback = new CallbackInfoReturnable<Boolean>("sticksToWalls", true);
            setting.invoke(self, callback);
            check(callback.isCancelled() == arrow && (!arrow || callback.getReturnValue() == wall), "arrow raw wall option depends on Render3D");
            check(!(boolean)ground.invoke(self, self), "tick still forces arrow stick");
            for (HitResult.Type kind : new HitResult.Type[]{HitResult.Type.BLOCK, HitResult.Type.ENTITY}) {
                HitResult hit = hit(kind);
                boolean expected = kind == HitResult.Type.BLOCK ? arrow ? wall : threeD && wall : arrow || threeD && wall;
                check((boolean)block.invoke(self, self, hit) == expected && (boolean)survive.invoke(self, self, hit) == expected,
                        "wall setting leaked into entity-hit behavior or block decision");
            }
            self.pos = new Vec3(100, 64, 100); self.motion = new Vec3(2, 0, 0);
            // Native inGround branch is the precondition for this injected handler.
            if (self.sticksToWalls()) {
                stop.invoke(self, hit(HitResult.Type.BLOCK), new CallbackInfo("onHit", false));
                check(arrow ? self.pos.distanceTo(new Vec3(101.95, 64, 100)) < 1e-9 && self.motion.equals(Vec3.ZERO)
                        : self.pos.equals(new Vec3(100, 64, 100)) && self.motion.equals(new Vec3(2, 0, 0)), "landing moved other items or left arrow flying");
            }
        }
    }

    private static HitResult hit(HitResult.Type kind) {
        return new HitResult(new Vec3(102, 64, 100)) { @Override public Type getType() { return kind; } };
    }
    public static final class ButtonShell {
        public int id, value;
        public int getValue() { return value; }
        public void setDisplay(int selected) { value = selected; }
    }
    public interface ParentShell { void buttonEvent(ButtonShell button); }

    private static void pickerConfirmation() throws Exception {
        ClassNode node = read("top/cnpcplus/client/gui/SubGuiNpcOptionSelect");
        node.methods.removeIf(m -> !m.name.equals("buttonEvent"));
        node.fields.removeIf(f -> !List.of("parent", "original", "pending", "visible").contains(f.name));
        for (FieldNode field : node.fields) field.access = Opcodes.ACC_PUBLIC;
        String name = "top/cnpcplus/NpcPickerProbe";
        Map<String, String> map = new HashMap<>(); map.put(node.name, name);
        map.put(BUTTON, Type.getInternalName(ButtonShell.class)); map.put(GUI, Type.getInternalName(ParentShell.class));
        ClassNode probe = new ClassNode(); node.accept(new ClassRemapper(probe, new SimpleRemapper(map)));
        probe.superName = "java/lang/Object"; probe.interfaces.clear();
        probe.fields.add(new FieldNode(Opcodes.ACC_PUBLIC, "closed", "Z", null, null));
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN)); probe.methods.add(init);
        MethodNode close = new MethodNode(Opcodes.ACC_PUBLIC, "close", "()V", null, null);
        close.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0)); close.instructions.add(new InsnNode(Opcodes.ICONST_1));
        close.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, name, "closed", "Z")); close.instructions.add(new InsnNode(Opcodes.RETURN));
        probe.methods.add(close);
        Class<?> type = define(probe); int[] calls = {0};
        for (int action : new int[]{1, 0, 0}) {
            Object picker = type.getConstructor().newInstance(); ButtonShell original = new ButtonShell(); original.value = 1;
            type.getField("parent").set(picker, (ParentShell)button -> { check(button == original && original.value == 4, "apply order/type changed"); calls[0]++; });
            type.getField("original").set(picker, original); type.getField("pending").setInt(picker, 4);
            type.getField("visible").set(picker, new ArrayList<>(calls[0] == 0 && action == 0 ? List.of(0, 1, 4) : List.of(0, 1)));
            ButtonShell button = new ButtonShell(); button.id = action;
            int before = calls[0]; type.getMethod("buttonEvent", ButtonShell.class).invoke(picker, button);
            boolean apply = action == 0 && before == 0;
            check(original.value == (apply ? 4 : 1) && calls[0] == before + (apply ? 1 : 0), "cancel/filter changed parent data");
            check(type.getField("closed").getBoolean(picker), "picker did not close");
        }
        check(calls[0] == 1, "confirmation not applied exactly once");
    }

    private static void inventorySubGuiRenderGuard() throws Exception {
        ClassNode mixin = read(PREFIX + "MixinGuiNPCInv");
        Map<String, String> guards = Map.of("cnpcplus$skipSlotHighlightsWithSubGui", "m_7286_",
                "cnpcplus$skipNpcPreviewWithSubGui", "m_88315_");
        for (var guard : guards.entrySet()) {
            MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals(guard.getKey())).findFirst().orElseThrow();
            AnnotationNode inject = annotations(handler.visibleAnnotations, handler.invisibleAnnotations).stream()
                    .filter(a -> a.desc.endsWith("/Inject;")).findFirst().orElseThrow();
            @SuppressWarnings("unchecked") List<String> method = (List<String>) value(inject, "method");
            Object atValue = value(inject, "at");
            AnnotationNode at = atValue instanceof AnnotationNode node ? node : (AnnotationNode) ((List<?>) atValue).get(0);
            String[] shift = (String[]) value(at, "shift");
            String descriptor = guard.getValue().equals("m_7286_") ? "FII" : "IIF";
            check(method.contains(guard.getValue()) && "INVOKE".equals(value(at, "value"))
                            && ("Lnoppes/npcs/client/gui/util/GuiContainerNPCInterface2;" + guard.getValue()
                                + "(Lnet/minecraft/client/gui/GuiGraphics;" + descriptor + ")V").equals(value(at, "target"))
                            && shift[1].equals("AFTER") && Boolean.TRUE.equals(value(inject, "cancellable")),
                    "inventory guard must preserve the parent/child rendering before skipping " + guard.getKey());
        }

        MethodNode nativeRender = read("noppes/npcs/client/gui/mainmenu/GuiNPCInv").methods.stream()
                .filter(m -> m.name.equals("m_88315_")).findFirst().orElseThrow();
        List<String> nativeCalls = Arrays.stream(nativeRender.instructions.toArray()).filter(i -> i instanceof MethodInsnNode)
                .map(i -> ((MethodInsnNode)i).name).toList();
        check(nativeCalls.equals(List.of("m_88315_", "getShowName", "setShowName", "drawNpc", "setShowName")),
                "native preview suffix changed; cancellation may skip non-preview work or name restoration");

        mixin.methods.removeIf(m -> !guards.containsKey(m.name));
        mixin.fields.clear();
        ClassNode probe = new ClassNode();
        mixin.accept(new ClassRemapper(probe, new SimpleRemapper(Map.of(mixin.name, "top/cnpcplus/NpcInventoryRenderProbe",
                GUI, Type.getInternalName(InventoryRenderShell.class), "net/minecraft/client/gui/GuiGraphics", "java/lang/Object"))));
        probe.superName = Type.getInternalName(InventoryRenderShell.class);
        MethodNode init = new MethodNode(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, probe.superName, "<init>", "()V", false));
        init.instructions.add(new InsnNode(Opcodes.RETURN)); probe.methods.add(init);
        for (MethodNode handler : probe.methods) for (var instruction : handler.instructions)
            if (instruction instanceof MethodInsnNode call && call.name.equals("hasSubGui")) {
                call.setOpcode(Opcodes.INVOKEVIRTUAL); call.itf = false;
            }
        Class<?> type = define(probe);
        InventoryRenderShell self = (InventoryRenderShell)type.getConstructor().newInstance();
        for (String name : guards.keySet()) {
            boolean slots = guards.get(name).equals("m_7286_");
            Method handler = type.getDeclaredMethod(name, Object.class, slots ? float.class : int.class,
                    int.class, slots ? int.class : float.class, CallbackInfo.class);
            handler.setAccessible(true);
            for (boolean open : new boolean[]{false, true, true, false}) {
                self.subGui = open;
                CallbackInfo callback = new CallbackInfo(guards.get(name), true);
                if (slots) handler.invoke(self, null, 0f, 0, 0, callback);
                else handler.invoke(self, null, 0, 0, 0f, callback);
                check(callback.isCancelled() == open, "inventory decorations/preview did not follow child screen visibility: " + name);
            }
        }
        System.out.println("INVENTORY_SUBGUI_REGRESSION=PASS (slot/preview guards; open/reopen/close; native render order)");
    }

    public static class InventoryRenderShell {
        public boolean subGui;
        public boolean hasSubGui() { return subGui; }
    }

    private static void visualControls() throws Exception {
        ClassNode choices = read("top/cnpcplus/client/gui/GuiSelectionButton");
        MethodNode render = choices.methods.stream().filter(m -> m.name.equals("renderWidget")).findFirst().orElseThrow();
        List<Object> colors = Arrays.stream(render.instructions.toArray()).filter(i -> i instanceof LdcInsnNode)
                .map(i -> ((LdcInsnNode)i).cst).toList();
        check(colors.contains(0xff373737) && Arrays.stream(render.instructions.toArray()).anyMatch(i -> i.getOpcode() == Opcodes.ICONST_M1),
                "selected choice lost the Minecraft recessed bevel");

        ClassNode picker = read("top/cnpcplus/client/gui/SubGuiNpcOptionSelect");
        check(picker.interfaces.contains("noppes/npcs/shared/client/gui/listeners/ISliderListener"), "animation picker is not wired to native slider");
        MethodNode drag = picker.methods.stream().filter(m -> m.name.equals("mouseDragged")).findFirst().orElseThrow();
        check(Arrays.stream(drag.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("sliderValue") && f.getOpcode() == Opcodes.GETFIELD)
                && Arrays.stream(drag.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("previewRotation") && f.getOpcode() == Opcodes.PUTFIELD),
                "slider drag no longer updates preview rotation");
        MethodNode draw = picker.methods.stream().filter(m -> m.name.equals("m_88315_")).findFirst().orElseThrow();
        check(Arrays.stream(draw.instructions.toArray()).anyMatch(i -> i instanceof FieldInsnNode f && f.name.equals("previewRotation") && f.getOpcode() == Opcodes.GETFIELD),
                "preview renderer no longer reads selected rotation");

        ClassNode advanced = read(PREFIX + "MixinGuiNpcAdvancedCustomRoleJob");
        MethodNode layout = advanced.methods.stream().filter(m -> m.name.equals("cnpcplus$quickSelect")).findFirst().orElseThrow();
        check(Arrays.stream(layout.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("setX"))
                && Arrays.stream(layout.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("setCentered")),
                "advanced controls no longer use a centered row/current-value slot");
    }

    private static void pathOverlay() throws Exception {
        ClassNode node = read("top/cnpcplus/wand/client/WandPathOverlay");
        MethodNode render = node.methods.stream().filter(m -> m.name.equals("render")).findFirst().orElseThrow();
        List<Object> sizes = Arrays.stream(render.instructions.toArray()).filter(i -> i instanceof LdcInsnNode)
                .map(i -> ((LdcInsnNode)i).cst).toList();
        check(sizes.contains(8.0) && sizes.contains(.28) && sizes.contains(.13)
                && Arrays.stream(render.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call && call.name.equals("isTurn"))
                && Arrays.stream(render.instructions.toArray()).anyMatch(i -> i instanceof MethodInsnNode call && call.owner.equals("net/minecraft/client/renderer/RenderType") && call.name.equals("debugQuads")),
                "route arrows or turning-point markers lost their sparse layout");
        node.methods.removeIf(m -> !List.of("route", "routeStrip", "waypoint", "diamond", "quad", "vertex", "isTurn").contains(m.name));
        ClassNode probe = new ClassNode();
        node.accept(new ClassRemapper(probe, new SimpleRemapper(node.name, "top/cnpcplus/NpcRouteProbe")));
        Class<?> type = define(probe);
        List<Vec3> vertices = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();
        var consumer = (com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(
                NpcEditorRegression.class.getClassLoader(), new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class}, (self, method, args) -> {
                    if (args != null && args.length == 4) {
                        if (args[0] instanceof org.joml.Matrix4f) vertices.add(new Vec3(((Number)args[1]).doubleValue(), ((Number)args[2]).doubleValue(), ((Number)args[3]).doubleValue()));
                        else if (args[0] instanceof org.joml.Matrix3f) {
                            for (int i = 1; i < 4; i++) check(Float.isFinite(((Number)args[i]).floatValue()), "non-finite route normal");
                        } else if (args[0] instanceof Integer r && args[1] instanceof Integer g && args[2] instanceof Integer b && args[3] instanceof Integer a) {
                            colors.add(a << 24 | r << 16 | g << 8 | b);
                        }
                    }
                    return method.getReturnType() == com.mojang.blaze3d.vertex.VertexConsumer.class ? self : null;
                });
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        Method route = type.getDeclaredMethod("route", pose.getClass(), com.mojang.blaze3d.vertex.VertexConsumer.class, Vec3.class, Vec3.class);
        route.setAccessible(true);
        for (Vec3 end : List.of(new Vec3(2, 0, 0), new Vec3(2, 1, 2), new Vec3(0, 2, 0))) {
            vertices.clear(); colors.clear();
            route.invoke(null, pose, consumer, Vec3.ZERO, end);
            check(vertices.size() == 8 && colors.equals(List.of(0xc029241d, 0xc029241d, 0xc029241d, 0xc029241d,
                    0xffffd34d, 0xffffd34d, 0xffffd34d, 0xffffd34d)), "route lost its dark border or bright gold core");
            check(Math.abs(vertices.get(0).distanceTo(vertices.get(3)) - .09) < 1e-6
                    && Math.abs(vertices.get(4).distanceTo(vertices.get(7)) - .064) < 1e-6
                    && Math.abs(vertices.get(4).y - vertices.get(0).y - .004) < 1e-6,
                    "route width or core depth separation changed");
        }
        vertices.clear(); colors.clear();
        Method waypoint = type.getDeclaredMethod("waypoint", pose.getClass(), com.mojang.blaze3d.vertex.VertexConsumer.class, Vec3.class);
        waypoint.setAccessible(true); waypoint.invoke(null, pose, consumer, Vec3.ZERO);
        check(vertices.size() == 8 && colors.subList(0, 4).stream().allMatch(c -> c == 0xc029241d)
                && colors.subList(4, 8).stream().allMatch(c -> c == 0xffffd34d), "waypoint lost its small outlined diamond");
        Method isTurn = type.getDeclaredMethod("isTurn", List.class, int.class, boolean.class); isTurn.setAccessible(true);
        var straight = List.of(new int[]{0, 0, 0}, new int[]{1, 0, 0}, new int[]{2, 0, 0});
        var bend = List.of(new int[]{0, 0, 0}, new int[]{1, 0, 0}, new int[]{1, 0, 1});
        check((boolean)isTurn.invoke(null, straight, 0, false) && !(boolean)isTurn.invoke(null, straight, 1, false)
                && (boolean)isTurn.invoke(null, straight, 2, false) && (boolean)isTurn.invoke(null, bend, 1, false),
                "waypoint markers no longer favor endpoints and turns");

        vertices.clear(); colors.clear();
        Vec3[] corners = {new Vec3(.5, 1, .5), new Vec3(3.5, 1, .5), new Vec3(3.5, 1, 3.5), new Vec3(.5, 1, 3.5)};
        for (int i = 0; i < corners.length; i++) {
            route.invoke(null, pose, consumer, corners[i], corners[(i + 1) % corners.length]);
            waypoint.invoke(null, pose, consumer, corners[i]);
        }
        Vec3 arrowTip = new Vec3(2, 1, .5);
        route.invoke(null, pose, consumer, arrowTip, new Vec3(1.78, 1, .41));
        route.invoke(null, pose, consumer, arrowTip, new Vec3(1.78, 1, .59));
        check(vertices.size() == colors.size() && vertices.size() % 4 == 0, "debugQuads stream contains a partial primitive");
        renderRoutePixels(vertices, colors);
    }

    private static void renderRoutePixels(List<Vec3> vertices, List<Integer> colors) throws Exception {
        final int size = 128;
        check(GLFW.glfwInit(), "GLFW init for path pixel regression");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(size, size, "NPC route pixel regression", 0, 0);
        check(window != 0, "OpenGL context for path pixel regression");
        int program = 0, vao = 0, vbo = 0;
        try {
            GLFW.glfwMakeContextCurrent(window);
            GL.createCapabilities();
            int vertexShader = compileShader(GL_VERTEX_SHADER, """
                    #version 150
                    in vec3 Position;
                    in vec4 Color;
                    out vec4 vertexColor;
                    void main() {
                        gl_Position = vec4(Position.x * 0.5 - 0.9, Position.z * 0.5 - 0.9, 0.0, 1.0);
                        vertexColor = Color;
                    }
                    """);
            int fragmentShader = compileShader(GL_FRAGMENT_SHADER, """
                    #version 150
                    in vec4 vertexColor;
                    out vec4 fragColor;
                    void main() { fragColor = vertexColor; }
                    """);
            program = glCreateProgram(); glAttachShader(program, vertexShader); glAttachShader(program, fragmentShader);
            glBindAttribLocation(program, 0, "Position"); glBindAttribLocation(program, 1, "Color"); glLinkProgram(program);
            check(glGetProgrami(program, GL_LINK_STATUS) != GL_FALSE, glGetProgramInfoLog(program));
            glDeleteShader(vertexShader); glDeleteShader(fragmentShader);

            vao = glGenVertexArrays(); glBindVertexArray(vao);
            vbo = glGenBuffers(); glBindBuffer(GL_ARRAY_BUFFER, vbo);
            float[] data = quadTriangles(vertices, colors);
            FloatBuffer buffer = BufferUtils.createFloatBuffer(data.length); buffer.put(data).flip();
            glBufferData(GL_ARRAY_BUFFER, buffer, GL_STATIC_DRAW);
            glVertexAttribPointer(0, 3, GL_FLOAT, false, 7 * Float.BYTES, 0L); glEnableVertexAttribArray(0);
            glVertexAttribPointer(1, 4, GL_FLOAT, false, 7 * Float.BYTES, 3L * Float.BYTES); glEnableVertexAttribArray(1);

            glViewport(0, 0, size, size); glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE);
            glEnable(GL_BLEND); glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
            glClearColor(.38f, .43f, .50f, 1); glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(program); glDrawArrays(GL_TRIANGLES, 0, data.length / 7); glFinish();
            ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
            glReadPixels(0, 0, size, size, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
            int gold = 0, dark = 0;
            for (int i = 0; i < size * size; i++) {
                int r = Byte.toUnsignedInt(pixels.get(i * 4)), g = Byte.toUnsignedInt(pixels.get(i * 4 + 1)), b = Byte.toUnsignedInt(pixels.get(i * 4 + 2));
                if (r > 220 && g > 150 && b < 120) gold++;
                if (r < 80 && g < 80 && b < 80) dark++;
            }
            int center = (size / 2 * size + size / 2) * 4;
            int centerR = Byte.toUnsignedInt(pixels.get(center)), centerG = Byte.toUnsignedInt(pixels.get(center + 1)), centerB = Byte.toUnsignedInt(pixels.get(center + 2));
            check(gold > 100 && dark > 20 && centerR > 80 && centerG > 90 && centerB > 100,
                    "path pixel probe shows missing colors or filled-in route interior: gold=" + gold + ", dark=" + dark
                            + ", center=" + centerR + "/" + centerG + "/" + centerB);
            check(glGetError() == GL_NO_ERROR, "OpenGL route pixel probe error");
            BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
                int i = ((size - 1 - y) * size + x) * 4;
                image.setRGB(x, y, Byte.toUnsignedInt(pixels.get(i)) << 16 | Byte.toUnsignedInt(pixels.get(i + 1)) << 8 | Byte.toUnsignedInt(pixels.get(i + 2)));
            }
            ImageIO.write(image, "png", Path.of("Temp/path-overlay-regression.png").toFile());
            System.out.println("PATH_PIXEL_REGRESSION=PASS (" + gold + " gold, " + dark + " outline pixels; " + glGetString(GL_RENDERER)
                    + "; Temp/path-overlay-regression.png)");
        } finally {
            if (vbo != 0) glDeleteBuffers(vbo);
            if (vao != 0) glDeleteVertexArrays(vao);
            if (program != 0) glDeleteProgram(program);
            GLFW.glfwDestroyWindow(window); GLFW.glfwTerminate();
        }
    }

    private static float[] quadTriangles(List<Vec3> vertices, List<Integer> colors) {
        float[] data = new float[vertices.size() / 4 * 6 * 7];
        int[] order = {0, 1, 2, 0, 2, 3};
        int out = 0;
        for (int quad = 0; quad < vertices.size(); quad += 4) for (int index : order) {
            Vec3 point = vertices.get(quad + index); int color = colors.get(quad + index);
            data[out++] = (float)point.x; data[out++] = (float)point.y; data[out++] = (float)point.z;
            data[out++] = (color >> 16 & 255) / 255f; data[out++] = (color >> 8 & 255) / 255f;
            data[out++] = (color & 255) / 255f; data[out++] = (color >>> 24) / 255f;
        }
        return data;
    }

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type); glShaderSource(shader, source); glCompileShader(shader);
        check(glGetShaderi(shader, GL_COMPILE_STATUS) != GL_FALSE, glGetShaderInfoLog(shader));
        return shader;
    }

    private static Class<?> define(ClassNode node) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray();
        return new ClassLoader(NpcEditorRegression.class.getClassLoader()) {
            Class<?> create() { return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.length); }
        }.create();
    }
    private static ClassNode read(String name) throws Exception {
        try (var stream = NpcEditorRegression.class.getClassLoader().getResourceAsStream(name + ".class")) {
            check(stream != null, "missing class " + name); ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> list = new ArrayList<>(); if (visible != null) list.addAll(visible); if (invisible != null) list.addAll(invisible); return list;
    }
    private static Object value(AnnotationNode annotation, String key) {
        int index = annotation.values.indexOf(key); return index < 0 ? null : annotation.values.get(index + 1);
    }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
}
