package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.parts.MpmPartData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.model.PartTransform;

@Mixin(value = MpmPartData.class, remap = false)
public abstract class MixinMpmPartTransform implements PartTransform.Access {
    @Unique private final PartTransform cnpcplus$transform = new PartTransform();

    @Override public PartTransform cnpcplus$partTransform() { return cnpcplus$transform; }

    @Inject(method = "setNbt", at = @At("TAIL"), require = 1)
    private void cnpcplus$readTransform(CompoundTag tag, CallbackInfo ci) {
        cnpcplus$transform.read(tag);
    }

    @Inject(method = "getNbt", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeTransform(CallbackInfoReturnable<CompoundTag> ci) {
        cnpcplus$transform.write(ci.getReturnValue());
    }
}
