package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.trader.MerchantExternalStore;

@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCMerchantExternal {
    @Inject(method = "m_7378_", at = @At("RETURN"), remap = false)
    private void cnpcplus$restoreTraderProducts(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.role instanceof RoleTrader role) {
            MerchantExternalStore.prepareLoad(role, tag);
            if (MerchantExternalStore.isExternal(role)) role.load(tag);
        }
    }

    @Inject(method = "m_7380_", at = @At("RETURN"), remap = false)
    private void cnpcplus$externalizeTraderProducts(CompoundTag tag, CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.role instanceof RoleTrader) MerchantExternalStore.saveNpc(npc, tag);
    }
}
