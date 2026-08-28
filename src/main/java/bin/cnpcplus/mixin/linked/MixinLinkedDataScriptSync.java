package bin.cnpcplus.mixin.linked;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.LinkedNpcController;
import bin.cnpcplus.common.ILinkedDataScriptSyncAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = LinkedNpcController.LinkedData.class, remap = false)
public abstract class MixinLinkedDataScriptSync implements ILinkedDataScriptSyncAccess {

    @Unique
    private boolean cnpcplus$scriptSync = false;

    @Inject(method = "setNBT", at = @At("TAIL"))
    private void cnpcplus$onSetNBT(NBTTagCompound compound, CallbackInfo ci) {
        cnpcplus$scriptSync = compound != null && compound.hasKey("CNPCPlusScriptSync", 1)
                && compound.getBoolean("CNPCPlusScriptSync");
    }

    @Inject(method = "getNBT", at = @At("RETURN"))
    private void cnpcplus$onGetNBT(CallbackInfoReturnable<NBTTagCompound> cir) {
        NBTTagCompound compound = cir.getReturnValue();
        if (compound != null) {
            compound.setBoolean("CNPCPlusScriptSync", cnpcplus$scriptSync);
        }
    }

    @Override
    public boolean cnpcplus$getScriptSync() {
        return cnpcplus$scriptSync;
    }

    @Override
    public void cnpcplus$setScriptSync(boolean sync) {
        cnpcplus$scriptSync = sync;
    }
}
