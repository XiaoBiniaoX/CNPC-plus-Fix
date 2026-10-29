package top.cnpcplus.interact;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import noppes.npcs.CustomItems;
import noppes.npcs.entity.EntityNPCInterface;
import bin.cnpcplus.config.CnpcPlusConfig;
import top.cnpcplus.config.ServerConfigAccess;

/** 对齐来源的物品使用优先判据：潜行、普通物品和CNPC编辑工具仍与NPC交互。 */
public final class InteractPriority {
    private InteractPriority() {}

    public static boolean itemWins(Player player, Entity target, InteractionHand hand) {
        if (player == null || hand == null || !(target instanceof EntityNPCInterface)) return false;
        if (!ServerConfigAccess.bool(CnpcPlusConfig.INTERACT_ITEM_PRIORITY, true) || player.isShiftKeyDown()) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || isCnpcTool(stack)) return false;
        return stack.getUseAnimation() != UseAnim.NONE;
    }

    private static boolean isCnpcTool(ItemStack stack) {
        var item = stack.getItem();
        return item == CustomItems.wand || item == CustomItems.cloner || item == CustomItems.mount
                || item == CustomItems.scripter || item == CustomItems.moving
                || item == CustomItems.soulstoneEmpty || item == CustomItems.nbt_book;
    }
}
