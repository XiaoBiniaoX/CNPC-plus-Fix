package bin.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "yesman.epicfight.world.capabilities.EpicFightCapabilities", remap = false)
public class EpicFightCapabilitiesMixin {

    @Inject(method = "getEntityPatch", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$skipModelEntityPatch(Entity entity, Class<?> type, CallbackInfoReturnable<@Nullable ?> cir) {
        if (bin.cnpcplus.util.FreezeHelper.isRenderingCNPCModelEntity(entity)) {
            cir.setReturnValue(null);
        }
    }
}