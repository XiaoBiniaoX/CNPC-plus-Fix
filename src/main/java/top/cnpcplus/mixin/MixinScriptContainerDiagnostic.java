package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.script.ScriptDiagnostics;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptContainerDiagnostic {
    @Inject(method = "load", at = @At("RETURN"), require = 1)
    private void cnpcplus$readDiagnostic(CompoundTag nbt, CallbackInfo ci) {
        ScriptDiagnostics.read((ScriptContainer) (Object) this, nbt);
    }

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeDiagnostic(CompoundTag nbt, CallbackInfoReturnable<CompoundTag> cir) {
        ScriptContainer owner = (ScriptContainer) (Object) this;
        ScriptDiagnostics.write(owner, cir.getReturnValue());
    }
}
