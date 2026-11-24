package bin.cnpcplus.mixin.model;

import bin.cnpcplus.model.PartTransform;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.ModelPartData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ModelPartData.class, remap = false)
public abstract class MixinModelPartTransform implements PartTransform.Access {
    @Unique private final PartTransform cnpcplus$transform = new PartTransform();
    public PartTransform cnpcplus$partTransform() { return cnpcplus$transform; }
    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$read(NBTTagCompound tag, CallbackInfo ci) { cnpcplus$transform.read(tag); }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$write(CallbackInfoReturnable<NBTTagCompound> cir) { cnpcplus$transform.write(cir.getReturnValue()); }
}
