package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.trader.MerchantExternalStore;

@Mixin(value = RoleTrader.class, remap = false)
public abstract class MixinRoleTraderExternalStore {
    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private void cnpcplus$prepareProductMigration(CompoundTag nbt, CallbackInfo ci) {
        RoleTrader role = (RoleTrader) (Object) this;
        if (role.npc != null && role.npc.role == role) MerchantExternalStore.prepareLoad(role, nbt);
    }

    @Inject(method = "save", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveProductsOutsideEntityNbt(CompoundTag nbt,
            CallbackInfoReturnable<CompoundTag> cir) {
        RoleTrader role = (RoleTrader) (Object) this;
        if (role.npc != null && role.npc.role == role) MerchantExternalStore.saveNpc(role.npc, cir.getReturnValue());
    }
}
