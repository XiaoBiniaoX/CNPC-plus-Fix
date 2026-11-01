package top.cnpcplus;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Execute the real page's nonce branches; only GUI input/rendering and network transport are test shells. */
public final class WandLifecycleRegression {
    private static final String PAGE = "top.cnpcplus.wand.client.GuiWandNpcList";
    private static final String HOOK = "top/cnpcplus/WandLifecycleRegression";
    private static final sun.misc.Unsafe UNSAFE;
    private static final List<Long> sent = new ArrayList<>();
    private static List<Integer> selected = List.of(41);
    private static int opened, cancelled;
    static {
        try { Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); UNSAFE = (sun.misc.Unsafe)f.get(null); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    public static List<Integer> selection() { return selected; }
    public static void send(int id, long request, boolean copying) { if (copying) sent.add(request); }
    public static void clearFocus(Object page, Object focus) { }
    public static void clearDragging(Object page, boolean dragging) { }
    public static void unfocus() { }
    public static void cancel(Object list) { cancelled++; }
    public static Object button(Object page, int id) { return null; }
    public static void nativeSend(Object packet) { }
    public static void open(Object page, Object screen) { opened++; }

    public static void main(String[] args) throws Exception {
        ClassLoader loader = new ClassLoader(WandLifecycleRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(PAGE)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (stream == null) throw new ClassNotFoundException(name);
                        ClassNode node = new ClassNode(); new ClassReader(stream.readAllBytes()).accept(node, 0);
                        for (MethodNode method : node.methods) {
                            if (method.name.equals("selectedIds")) {
                                method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "selection", "()Ljava/util/List;", false));
                                method.instructions.add(new InsnNode(Opcodes.ARETURN));
                            }
                            for (var instruction : method.instructions.toArray()) {
                                if (instruction instanceof TypeInsnNode create && create.getOpcode() == Opcodes.NEW
                                        && create.desc.equals("noppes/npcs/client/gui/GuiNpcMobSpawnerAdd")) {
                                    method.instructions.remove(create.getNext()); method.instructions.remove(create);
                                } else if (instruction instanceof MethodInsnNode call) {
                                    String hook = null, descriptor = null;
                                    if (call.owner.equals("top/cnpcplus/wand/network/WandNpcPacket") && call.name.equals("send")) { hook = "send"; descriptor = call.desc; }
                                    else if (call.name.equals("getButton") && call.owner.equals(PAGE.replace('.', '/'))) {
                                        var next = call.getNext();
                                        method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "button", "(Ljava/lang/Object;I)Ljava/lang/Object;", false));
                                        method.instructions.insertBefore(next, new TypeInsnNode(Opcodes.CHECKCAST, "noppes/npcs/shared/client/gui/components/GuiButtonNop"));
                                        continue;
                                    }
                                    else if (call.name.equals("setFocused") && call.owner.equals(PAGE.replace('.', '/'))) { hook = "clearFocus"; descriptor = "(Ljava/lang/Object;Ljava/lang/Object;)V"; }
                                    else if (call.name.equals("setDragging") && call.owner.equals(PAGE.replace('.', '/'))) { hook = "clearDragging"; descriptor = "(Ljava/lang/Object;Z)V"; }
                                    else if (call.name.equals("setScreen") && call.owner.equals(PAGE.replace('.', '/'))) { hook = "open"; descriptor = "(Ljava/lang/Object;Ljava/lang/Object;)V"; }
                                    else if (call.name.equals("unfocus")) { hook = "unfocus"; descriptor = "()V"; }
                                    else if (call.name.equals("cancelDrag")) { hook = "cancel"; descriptor = "(Ljava/lang/Object;)V"; }
                                    else if (call.owner.equals("noppes/npcs/packets/Packets") && call.name.equals("sendServer")) { hook = "nativeSend"; descriptor = "(Ljava/lang/Object;)V"; }
                                    else if (call.owner.equals("noppes/npcs/client/gui/GuiNpcMobSpawnerAdd") && call.name.equals("<init>")) {
                                        var next = call.getNext();
                                        method.instructions.set(call, new InsnNode(Opcodes.POP));
                                        method.instructions.insertBefore(next, new InsnNode(Opcodes.ACONST_NULL));
                                        continue;
                                    }
                                    if (hook != null) method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, hook, descriptor, false));
                                }
                            }
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer);
                        byte[] bytes = writer.toByteArray(); type = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(type); return type;
                }
            }
        };
        Class<?> page = loader.loadClass(PAGE);
        Object first = UNSAFE.allocateInstance(page); field(page, "copying").setBoolean(first, true);
        Class<?> button = loader.loadClass("noppes.npcs.shared.client.gui.components.GuiButtonNop");
        Object copy = UNSAFE.allocateInstance(button); button.getField("id").setInt(copy, 0);
        var click = page.getMethod("buttonEvent", button);
        var accept = page.getMethod("acceptCopy", long.class, CompoundTag.class);
        click.invoke(first, copy); long request = field(page, "copyRequest").getLong(first);
        check(request != 0 && sent.size() == 1, "first copy not requested");
        click.invoke(first, copy); check(sent.size() == 1, "rapid click sent duplicate copy");
        accept.invoke(first, request + 1, new CompoundTag()); check(field(page, "copyRequest").getLong(first) == request && opened == 0, "wrong nonce opened page");
        accept.invoke(first, request, null); check(field(page, "copyRequest").getLong(first) == 0 && opened == 0, "failure did not restore request state");
        click.invoke(first, copy); request = field(page, "copyRequest").getLong(first);
        Object refresh = UNSAFE.allocateInstance(button); button.getField("id").setInt(refresh, 6);
        Class<?> scroll = loader.loadClass("top.cnpcplus.wand.client.WandNpcScroll");
        field(page, "list").set(first, UNSAFE.allocateInstance(scroll));
        click.invoke(first, refresh);
        check(field(page, "copyRequest").getLong(first) == 0 && cancelled == 1, "refresh did not cancel copy and drag");
        accept.invoke(first, request, new CompoundTag()); check(opened == 0, "late refreshed copy opened editor");
        click.invoke(first, copy); request = field(page, "copyRequest").getLong(first);
        page.getMethod("removed").invoke(first);
        check(field(page, "copyRequest").getLong(first) == 0 && field(page, "previewRequest").getLong(first) == 0 && cancelled == 2, "close retained nonce or drag");
        accept.invoke(first, request, new CompoundTag()); check(opened == 0, "late closed copy opened editor");
        Object second = UNSAFE.allocateInstance(page); field(page, "copying").setBoolean(second, true);
        accept.invoke(second, request, new CompoundTag()); check(opened == 0, "old page copy affected reopened page");
        // Returning from confirmation reuses the old instance after removed; selection requests a fresh preview.
        var changed = page.getDeclaredMethod("selectionChanged"); changed.setAccessible(true); changed.invoke(first);
        check(field(page, "previewId").getInt(first) == 41 && field(page, "previewRequest").getLong(first) != 0, "return did not request fresh preview");
        click.invoke(second, copy); request = field(page, "copyRequest").getLong(second);
        accept.invoke(second, request, new CompoundTag()); accept.invoke(second, request, new CompoundTag());
        check(opened == 1 && field(page, "copyRequest").getLong(second) == 0, "success opened twice or kept request busy");
        selected = List.of(41, 42); click.invoke(second, copy); check(field(page, "copyRequest").getLong(second) == 0, "multi-selection copied");
        System.out.println("WAND_LIFECYCLE_REGRESSION=PASS (real nonce branches; GUI input/render/transport shells, not runtime Mixin)");
    }
    private static Field field(Class<?> type, String name) throws Exception { Field field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
