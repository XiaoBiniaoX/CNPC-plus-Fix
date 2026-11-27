package bin.cnpcplus.quest;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.MinecraftForge;

public final class ItemProductionEvents {
    private ItemProductionEvents() {}

    public static void post(EntityPlayer player, ItemStack stack, CraftStation station,
                            BlockPos pos, int count, String recipeId) {
        if (player == null || player.world == null || player.world.isRemote || stack == null || stack.isEmpty()
                || station == null || pos == null || count <= 0) return;
        MinecraftForge.EVENT_BUS.post(new ItemProductionEvent(player, stack, station, player.world, pos, count, recipeId));
    }
}
