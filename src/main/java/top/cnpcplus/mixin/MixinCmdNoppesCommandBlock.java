package top.cnpcplus.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import noppes.npcs.command.CmdNoppes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * /noppes 各子命令用 hasPermission(NoppesCommandOpOnly ? 4 : 2)，命令方块只有 level 2，
 * 因此一部分子命令拒绝执行。注册后把整棵 /noppes 子树的 requirement 统一降到 level 2。
 */
@Mixin(value = CmdNoppes.class, remap = false)
public class MixinCmdNoppesCommandBlock {

    @Inject(method = "register", at = @At("RETURN"))
    private static void cnpcplus$allowCommandBlocks(CommandDispatcher<CommandSourceStack> dispatcher, CallbackInfo ci) {
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("noppes");
        if (root == null) return;
        Field requirement = cnpcplus$requirementField();
        if (requirement == null) return;
        cnpcplus$relax(root, requirement, new HashSet<>());
    }

    @Unique
    private static Field cnpcplus$requirementField() {
        try {
            Field f = CommandNode.class.getDeclaredField("requirement");
            f.setAccessible(true);
            return f;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus").error("获取命令节点 requirement 字段失败", e);
            return null;
        }
    }

    @Unique
    private static void cnpcplus$relax(CommandNode<CommandSourceStack> node, Field requirement,
                                       Set<CommandNode<CommandSourceStack>> seen) {
        if (!seen.add(node)) return;
        try {
            Predicate<CommandSourceStack> allow = source -> source.hasPermission(2);
            requirement.set(node, allow);
        } catch (Exception ignored) {
        }
        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            cnpcplus$relax(child, requirement, seen);
        }
        CommandNode<CommandSourceStack> redirect = node.getRedirect();
        if (redirect != null) cnpcplus$relax(redirect, requirement, seen);
    }
}
