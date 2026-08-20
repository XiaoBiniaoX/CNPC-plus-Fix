package bin.cnpcplus.smelting;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import noppes.npcs.CustomNpcsPermissions;

/**
 * One place to decide who may edit smelting recipes.
 *
 * Reuses CNPC's own global recipe permission so server owners configure this the
 * same way they configure every other global editor, and falls back to an op
 * level check when the permission system is disabled.
 */
public final class SmeltingPermissions {
    private SmeltingPermissions() {}

    public static boolean canEdit(EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        try {
            if (CustomNpcsPermissions.hasPermission(player, CustomNpcsPermissions.GLOBAL_RECIPE)) {
                return true;
            }
        } catch (Throwable ignored) {
            // Permission backend missing or misconfigured: fall through to op check.
        }
        return player.canUseCommand(2, "");
    }
}
