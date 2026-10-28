package bin.cnpcplus.mixin.advanced;

import bin.cnpcplus.common.IUntargetableAccess;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.ai.selector.NPCAttackSelector;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NPCAttackSelector.class, remap = false)
public abstract class MixinNpcAttackSelectorUntargetable {
    @Inject(method = "isEntityApplicable", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$excludeUntargetable(EntityLivingBase entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof EntityNPCInterface
                && ((IUntargetableAccess) ((EntityNPCInterface) entity).advanced).cnpcplus$isUntargetable()) cir.setReturnValue(false);
    }
}
