package top.cnpcplus.script;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Construct the added enum value inside the enum's own initializer. */
public final class ScriptTypesPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) {}
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String target, String mixin) { return true; }
    @Override public void acceptTargets(Set<String> own, Set<String> others) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) {}
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) {
        if (!mixin.equals("top.cnpcplus.mixin.MixinScriptTypes")) return;
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
