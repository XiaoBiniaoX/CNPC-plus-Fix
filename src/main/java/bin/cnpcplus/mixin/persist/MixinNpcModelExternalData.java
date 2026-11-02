package bin.cnpcplus.mixin.persist;

import bin.cnpcplus.data.NpcExternalData;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityCustomNpc.class, remap = false)
public abstract class MixinNpcModelExternalData {
    @Inject(method = "func_70037_a", at = @At("HEAD"), require = 1)
    private void cnpcplus$restoreModel(NBTTagCompound tag, CallbackInfo ci) {
        NpcExternalData.loadModel((EntityCustomNpc) (Object) this, tag);
    }

    @Inject(method = "func_70014_b", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveModel(NBTTagCompound tag, CallbackInfo ci) {
        NpcExternalData.saveModel((EntityCustomNpc) (Object) this, tag);
    }
}
