package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.villagertrade.RoleVillagerTrader;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedVillagerTrader {
    @Shadow private EntityNPCInterface npc;
    @Shadow public int role;
    @Inject(method = "setRole(I)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$roleEight(int id, CallbackInfo ci) {
        if (id != 8) return;
        this.role = 8;
        if (!(npc.roleInterface instanceof RoleVillagerTrader)) npc.roleInterface = new RoleVillagerTrader(npc);
        ci.cancel();
    }
}
