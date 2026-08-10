package bin.cnpcplus.mixin.command;

import bin.cnpcplus.command.CommandBlockPerms;
import net.minecraft.command.ICommandSender;
import noppes.npcs.api.CommandNoppesBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Allow /noppes subcommand permission checks (SubCommand.permission)
 * to pass for non-player senders such as command blocks.
 */
@Mixin(value = CommandNoppesBase.class, remap = false)
public class MixinCommandNoppesBasePerm {

    @Redirect(method = "executeSub",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/command/ICommandSender;func_70003_b(ILjava/lang/String;)Z",
                    remap = false))
    private boolean cnpcplus$canUse(ICommandSender sender, int level, String name) {
        return CommandBlockPerms.canUse(sender, level, name);
    }
}
