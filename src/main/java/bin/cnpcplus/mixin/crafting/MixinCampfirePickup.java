package bin.cnpcplus.mixin.crafting;

import bin.cnpcplus.quest.CraftStation;
import bin.cnpcplus.quest.ItemProductionEvents;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityItem.class)
public abstract class MixinCampfirePickup {
    @Unique private ItemStack cnpcplus$before = ItemStack.EMPTY;

    @Inject(method = "onCollideWithPlayer", at = @At("HEAD"))
    private void cnpcplus$beforePickup(EntityPlayer player, CallbackInfo ci) {
        EntityItem item = (EntityItem)(Object)this;
        NBTTagCompound record = item.getEntityData().getCompoundTag("CNPCPlusCampfireProduction");
        cnpcplus$before = !player.world.isRemote && record.hasUniqueId("Owner")
                && record.getUniqueId("Owner").equals(player.getUniqueID()) ? item.getItem().copy() : ItemStack.EMPTY;
    }

    @Inject(method = "onCollideWithPlayer", at = @At("RETURN"))
    private void cnpcplus$afterPickup(EntityPlayer player, CallbackInfo ci) {
        if (cnpcplus$before.isEmpty()) return;
        EntityItem item = (EntityItem)(Object)this;
        int taken = cnpcplus$before.getCount() - (item.isDead ? 0 : item.getItem().getCount());
        NBTTagCompound record = item.getEntityData().getCompoundTag("CNPCPlusCampfireProduction");
        ItemProductionEvents.post(player, cnpcplus$before, CraftStation.CAMPFIRE,
                BlockPos.fromLong(record.getLong("Position")), taken, record.getString("Recipe"));
        cnpcplus$before = ItemStack.EMPTY;
    }

    // Entity data is not considered by vanilla merging. Keep different owners/recipes separate.
    @Inject(method = "combineItems", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$mergeProduction(EntityItem other, CallbackInfoReturnable<Boolean> cir) {
        EntityItem self = (EntityItem)(Object)this;
        if (!self.getEntityData().getCompoundTag("CNPCPlusCampfireProduction")
                .equals(other.getEntityData().getCompoundTag("CNPCPlusCampfireProduction"))) cir.setReturnValue(false);
    }
}
