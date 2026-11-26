package top.cnpcplus.mixin;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.quest.ItemProductionEvent;
import top.cnpcplus.quest.ItemProductionEvents;

@Mixin(ItemEntity.class)
public abstract class MixinItemEntityProduction {
    @Unique private ItemStack cnpcplus$pickupSnapshot = ItemStack.EMPTY;

    @Inject(method = "playerTouch", at = @At("HEAD"), require = 1)
    private void cnpcplus$beforePickup(Player player, CallbackInfo ci) {
        ItemEntity item = (ItemEntity)(Object)this;
        cnpcplus$pickupSnapshot = !player.level().isClientSide
                && item.getPersistentData().getBoolean("CNPCPlusCampfireOutput")
                && item.getPersistentData().hasUUID("CNPCPlusCampfireOwner")
                ? item.getItem().copy() : ItemStack.EMPTY;
    }

    @Inject(method = "playerTouch", at = @At("TAIL"), require = 1)
    private void cnpcplus$afterPickup(Player player, CallbackInfo ci) {
        ItemEntity item = (ItemEntity)(Object)this;
        if (cnpcplus$pickupSnapshot.isEmpty()) return;
        int remaining = item.isRemoved() ? 0 : item.getItem().getCount();
        int taken = cnpcplus$pickupSnapshot.getCount() - remaining;
        if (taken > 0) ItemProductionEvents.post(player, cnpcplus$pickupSnapshot,
                ItemProductionEvent.Source.CAMPFIRE, player.level(), item.blockPosition(), taken);
        cnpcplus$pickupSnapshot = ItemStack.EMPTY;
    }
}
