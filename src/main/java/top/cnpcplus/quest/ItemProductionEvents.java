package top.cnpcplus.quest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;

/** Shared posting boundary for successful, server-side production. */
public final class ItemProductionEvents {
    private ItemProductionEvents() {}

    public static void post(Player player, ItemStack stack, ItemProductionEvent.Source source,
                            Level level, BlockPos pos, int count) {
        if (player.level().isClientSide || level == null || level.isClientSide || stack.isEmpty() || count <= 0) return;
        MinecraftForge.EVENT_BUS.post(new ItemProductionEvent(player, stack, source, level, pos, count));
    }

    public static void post(Player player, ItemStack stack, ItemProductionEvent.Source source,
                            ContainerLevelAccess access) {
        if (player.level().isClientSide || stack.isEmpty()) return;
        access.execute((level, pos) -> post(player, stack, source, level, pos, stack.getCount()));
    }
}
