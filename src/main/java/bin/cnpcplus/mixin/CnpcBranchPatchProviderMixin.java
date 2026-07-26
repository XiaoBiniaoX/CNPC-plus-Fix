package bin.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "top.bincnpcef.common.CnpcBranchPatchProvider", remap = false)
public class CnpcBranchPatchProviderMixin {

    @Inject(method = "get", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$nullCheckDisplay(Entity entity, CallbackInfoReturnable<Object> cir) {
        if (entity instanceof EntityNPCInterface npc && npc.display == null) {
            cir.setReturnValue(null);
        }
    }
}