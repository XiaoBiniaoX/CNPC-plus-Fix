package top.cnpcplus;

import java.lang.reflect.Field;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.shared.client.gui.components.GuiBasic;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.components.GuiWrapper;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Production removed handler on real mapped wrapper/active-field objects; GPU/page construction is not executed. */
public final class GuiRemovalRegression {
    private static final String PAGE = "top/cnpcplus/GuiRemovalRegression$Page";
    private static Field active;
    public static void clearActive(GuiTextFieldNop field) { try { active.set(null, field); } catch (Exception e) { throw new AssertionError(e); } }
    public static void main(String[] args) throws Exception {
        ClassLoader loader = new ClassLoader(GuiRemovalRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(PAGE.replace('/', '.'))) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(PAGE + ".class")) {
                        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                        ClassNode mixin = new ClassNode();
                        try (var source = getParent().getResourceAsStream("top/cnpcplus/mixin/MixinNpcScreenRemoved.class")) { new ClassReader(source).accept(mixin, 0); }
                        MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("cnpcplus$releaseDetachedPage")).findFirst().orElseThrow();
                        for (var instruction : handler.instructions.toArray()) if (instruction instanceof MethodInsnNode call && call.owner.equals("top/cnpcplus/mixin/GuiTextFieldActiveAccess")) {
                            call.owner = "top/cnpcplus/GuiRemovalRegression"; call.name = "clearActive"; call.itf = false;
                        }
                        node.methods.add(handler);
                        MethodNode removed = node.methods.stream().filter(m -> m.name.equals("removed")).findFirst().orElseThrow();
                        InsnList hook = new InsnList(); hook.add(new VarInsnNode(Opcodes.ALOAD, 0)); hook.add(new InsnNode(Opcodes.ACONST_NULL));
                        hook.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, PAGE, handler.name, handler.desc, false)); removed.instructions.insert(hook);
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true); var unsafe = (sun.misc.Unsafe)field.get(null);
        Class<?> pageType = loader.loadClass(PAGE.replace('/', '.'));
        GuiBasic parent = (GuiBasic)unsafe.allocateInstance(pageType), child = (GuiBasic)unsafe.allocateInstance(pageType), leaf = (GuiBasic)unsafe.allocateInstance(pageType);
        parent.wrapper = new GuiWrapper(parent); child.wrapper = new GuiWrapper(child); leaf.wrapper = new GuiWrapper(leaf);
        parent.wrapper.subgui = child; child.wrapper.parent = parent; child.wrapper.subgui = leaf; leaf.wrapper.parent = child;
        Text text = (Text)unsafe.allocateInstance(Text.class); text.setCanLoseFocus(true); text.setFocused(true); leaf.wrapper.textfields.put(0, text);
        active = GuiTextFieldNop.class.getDeclaredField("activeTextfield"); active.setAccessible(true); active.set(null, text);
        parent.removed();
        check(GuiTextFieldNop.getActive() == null && !text.isFocused() && text.callbacks == 0, "owned active field retained or removal saved draft");
        for (GuiBasic page : new GuiBasic[]{parent, child, leaf}) check(pageType.getField("removals").getInt(page) == 1 && pageType.getField("saves").getInt(page) == 0, "child removal not propagated or saved");
        check(parent.wrapper.subgui == child && child.wrapper.parent == parent && child.wrapper.subgui == leaf && leaf.wrapper.parent == child, "confirmation return lost parent/draft links");
        Text unrelated = (Text)unsafe.allocateInstance(Text.class); unrelated.setCanLoseFocus(true); unrelated.setFocused(true); active.set(null, unrelated);
        text.setFocused(true);
        parent.removed(); check(GuiTextFieldNop.getActive() == unrelated && unrelated.isFocused(), "removal cleared another page's active field");
        check(!text.isFocused() && text.callbacks == 0, "non-active owned field retained focus or saved draft");
        text.setFocused(true); active.set(null, null); parent.removed();
        check(!text.isFocused() && text.callbacks == 0, "owned focus retained without static active field");
        active.set(null, null);
        System.out.println("GUI_REMOVAL_REGRESSION=PASS (production handler; mapped wrapper/active fields, nested disposal/no-save/unrelated focus/draft links; not game/GPU/Mixin)");
    }
    public static final class Page extends GuiBasic {
        public int removals, saves;
        @Override public void removed() { removals++; }
        @Override public void save() { saves++; }
    }
    public static final class Text extends GuiTextFieldNop {
        int callbacks;
        private Text() { super(0, null, 0, 0, 100, 20, ""); }
        @Override public void unFocused() { callbacks++; }
    }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
}
