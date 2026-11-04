package top.cnpcplus.mixin;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.station.StationOperator;

@Mixin(BaseContainerBlockEntity.class)
public abstract class MixinStationOperator implements StationOperator {
    @Unique private UUID cnpcplus$operator;
    @Unique public UUID cnpcplus$getOperator() { return cnpcplus$operator; }
    @Unique public void cnpcplus$setOperator(UUID id) { cnpcplus$operator = id; ((BlockEntity)(Object)this).setChanged(); }
    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void cnpcplus$load(CompoundTag tag, CallbackInfo ci) { cnpcplus$operator = tag.hasUUID("CNPCPlusOperator") ? tag.getUUID("CNPCPlusOperator") : null; }
    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 1)
    private void cnpcplus$save(CompoundTag tag, CallbackInfo ci) { if (cnpcplus$operator != null) tag.putUUID("CNPCPlusOperator", cnpcplus$operator); }
    @Inject(method = "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;", at = @At("RETURN"), require = 1)
    private void cnpcplus$opened(int id, Inventory inventory, Player player, CallbackInfoReturnable<AbstractContainerMenu> cir) {
        if (cir.getReturnValue() != null && !player.level().isClientSide && ((Object)this instanceof BrewingStandBlockEntity || (Object)this instanceof AbstractFurnaceBlockEntity))
            cnpcplus$setOperator(player.getUUID());
    }
}
