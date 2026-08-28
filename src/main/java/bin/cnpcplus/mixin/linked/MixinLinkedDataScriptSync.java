package bin.cnpcplus.mixin.linked;

import bin.cnpcplus.accessor.LinkedScriptSyncAccess;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.LinkedNpcController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = noppes.npcs.controllers.LinkedNpcController.LinkedData.class, remap = false)
public class MixinLinkedDataScriptSync implements LinkedScriptSyncAccess {

    @Unique
    private boolean cnpcplus$scriptSync = false;

    @Inject(method = "setNBT", at = @At("TAIL"))
    private void cnpcplus$readScriptSync(CompoundTag compound, CallbackInfo ci) {
        if (compound.contains("CNPCPlusScriptSync")) {
            this.cnpcplus$scriptSync = compound.getBoolean("CNPCPlusScriptSync");
        }
    }

    @Inject(method = "getNBT", at = @At("RETURN"))
    private void cnpcplus$writeScriptSync(CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag tag = cir.getReturnValue();
        tag.putBoolean("CNPCPlusScriptSync", this.cnpcplus$scriptSync);
    }

    @Override
    public boolean cnpcplus$isScriptSync() {
        return this.cnpcplus$scriptSync;
    }

    @Override
    public void cnpcplus$setScriptSync(boolean sync) {
        this.cnpcplus$scriptSync = sync;
    }
}