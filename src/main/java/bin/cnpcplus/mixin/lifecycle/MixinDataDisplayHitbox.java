package bin.cnpcplus.mixin.lifecycle;

import bin.cnpcplus.data.NpcHitboxStore;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataDisplay.class, remap = false)
public abstract class MixinDataDisplayHitbox {
    @Inject(method = "readToNBT", at = @At("HEAD"), require = 1)
    private void cnpcplus$read(NBTTagCompound tag, CallbackInfo ci) {
        NpcHitboxStore.put((DataDisplay) (Object) this, NpcHitboxStore.Settings.read(tag));
        float shadow = tag.hasKey("CNPCPlusShadowScale", 99) ? tag.getFloat("CNPCPlusShadowScale") : 1;
        NpcHitboxStore.shadow((DataDisplay)(Object)this, Float.isFinite(shadow) ? Math.max(0, Math.min(1, shadow)) : 1);
        NpcHitboxStore.solid((DataDisplay) (Object) this, tag.getByte("IsStatue") == 2);
    }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$write(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        NpcHitboxStore.get((DataDisplay) (Object) this).write(cir.getReturnValue());
        cir.getReturnValue().setFloat("CNPCPlusShadowScale", NpcHitboxStore.shadow((DataDisplay)(Object)this));
        cir.getReturnValue().setByte("IsStatue", (byte) NpcHitboxStore.mode((DataDisplay) (Object) this));
    }
    @Inject(method = "getHasHitbox", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$solidHitbox(CallbackInfoReturnable<Boolean> cir) {
        if (NpcHitboxStore.isSolid((DataDisplay) (Object) this)) cir.setReturnValue(true);
    }
    @Inject(method = "setHasHitbox", at = @At("HEAD"), require = 1)
    private void cnpcplus$nativeToggle(boolean enabled, CallbackInfo ci) {
        NpcHitboxStore.solid((DataDisplay) (Object) this, false);
    }
}
