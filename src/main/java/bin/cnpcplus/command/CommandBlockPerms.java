package bin.cnpcplus.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Permission check for /noppes used from command blocks.
 * CommandBlockBaseLogic.canUseCommand only allows level &lt;= 2, so permission=4
 * subcommands (clone add/del, dialog reload, quest reload, schematics, npc)
 * fail when executed from command blocks. Players keep the original check.
 */
public class CommandBlockPerms {

    public static boolean canUse(ICommandSender sender, int level, String name) {
        if (sender.canUseCommand(level, name)) {
            return true;
        }
        if (sender instanceof EntityPlayer) {
            return false;
        }
        return sender.canUseCommand(2, name);
    }
}
