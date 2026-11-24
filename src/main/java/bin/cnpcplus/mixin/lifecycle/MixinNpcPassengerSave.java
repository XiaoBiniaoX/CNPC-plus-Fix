package bin.cnpcplus.mixin.lifecycle;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityCustomNpc.class, remap = false)
public abstract class MixinNpcPassengerSave {
    // CNPC calls writeToNBTAtomically here, bypassing vanilla's root-only check.
    // Passenger NBT is already saved by its vehicle through writeToNBTAtomically.
    @Inject(method = {"writeToNBTOptional(Lnet/minecraft/nbt/NBTTagCompound;)Z", "func_70039_c(Lnet/minecraft/nbt/NBTTagCompound;)Z"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$rootOnly(NBTTagCompound tag, CallbackInfoReturnable<Boolean> cir) {
        if (((EntityCustomNpc)(Object)this).isRiding()) cir.setReturnValue(false);
    }
}
