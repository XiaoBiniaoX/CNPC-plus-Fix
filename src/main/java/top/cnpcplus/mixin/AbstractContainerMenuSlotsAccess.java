package top.cnpcplus.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露 AbstractContainerMenu 三个平行的槽位/同步列表。
 * 重建容器槽位时必须同时清空，否则服务端 remoteSlots 残留旧条目导致客户端 initializeContents 越界。
 */
@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuSlotsAccess {

    @Accessor("lastSlots")
    NonNullList<ItemStack> cnpcplus$getLastSlots();

    @Accessor("remoteSlots")
    NonNullList<ItemStack> cnpcplus$getRemoteSlots();
}