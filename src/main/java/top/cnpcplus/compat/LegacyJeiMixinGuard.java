package top.cnpcplus.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinErrorHandler;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException;

/** JEI changed its draw callback descriptors. The old CNPC overlay hook is optional. */
public final class LegacyJeiMixinGuard implements IMixinConfigPlugin, IMixinErrorHandler {
    @Override public void onLoad(String mixinPackage) { Mixins.registerErrorHandlerClass(getClass().getName()); }

    @Override public ErrorAction onApplyError(String target, Throwable error, IMixinInfo mixin, ErrorAction action) {
        if ("mezz.jei.gui.events.GuiEventHandler".equals(target)
                && "noppes.npcs.mixin.MixinJEIEventHandler".equals(mixin.getClassName())
                && "customnpcs.mixins.json".equals(mixin.getConfig().getName())
                && error instanceof InvalidInjectionException) return ErrorAction.NONE;
        return action;
    }

    @Override public ErrorAction onPrepareError(IMixinConfig config, Throwable error, IMixinInfo mixin, ErrorAction action) { return action; }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.equals("top.cnpcplus.mixin.MixinScriptTypes")) return;
        // Only the enum's own initializer can construct an instance and assign its final values array.
        String array = "[L" + node.name + ";";
        var init = node.methods.stream().filter(method -> method.name.equals("<clinit>")).findFirst().orElseThrow();
        var end = init.instructions.getLast();
        while (end.getOpcode() != Opcodes.RETURN) end = end.getPrevious();
        InsnList code = new InsnList();
        code.add(new TypeInsnNode(Opcodes.NEW, node.name));
        code.add(new InsnNode(Opcodes.DUP));
        code.add(new LdcInsnNode("TICKFIX"));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, "$VALUES", array));
        code.add(new InsnNode(Opcodes.ARRAYLENGTH));
        code.add(new LdcInsnNode("tickfix"));
        code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, node.name, "<init>", "(Ljava/lang/String;ILjava/lang/String;)V", false));
        code.add(new FieldInsnNode(Opcodes.GETSTATIC, node.name, "$VALUES", array));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "top/cnpcplus/script/ExtraScriptHook", "register",
                "(L" + node.name + ";" + array + ")" + array, false));
        code.add(new FieldInsnNode(Opcodes.PUTSTATIC, node.name, "$VALUES", array));
        init.instructions.insertBefore(end, code);
        init.maxStack = Math.max(init.maxStack, 5);
    }
}
