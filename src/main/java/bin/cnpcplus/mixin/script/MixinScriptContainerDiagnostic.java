package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptDiagnostics;
import bin.cnpcplus.script.ScriptHooks;
import java.io.PrintWriter;
import java.util.HashMap;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptContainerDiagnostic {
    @Shadow @Final private static HashMap<String, Object> Data;
    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$observeHook(String type, Object event, CallbackInfo ci) { ScriptHooks.observe((ScriptContainer) (Object) this, type, event); }
    @Redirect(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At(value = "INVOKE", target = "Ljava/lang/Throwable;printStackTrace(Ljava/io/PrintWriter;)V"), require = 1)
    private void cnpcplus$captureError(Throwable error, PrintWriter writer) {
        ScriptDiagnostics.capture((ScriptContainer) (Object) this, error);
        error.printStackTrace(writer);
    }
    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("RETURN"), require = 1)
    private void cnpcplus$clearSuccessful(String type, Object event, CallbackInfo ci) {
        ScriptContainer self = (ScriptContainer) (Object) this;
        if (self.isValid()) ScriptDiagnostics.clear(self);
    }
    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readError(NBTTagCompound nbt, CallbackInfo ci) {
        ScriptDiagnostics.read((ScriptContainer) (Object) this, nbt); ScriptHooks.read((ScriptContainer) (Object) this, nbt);
    }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeError(NBTTagCompound nbt, CallbackInfoReturnable<NBTTagCompound> cir) {
        ScriptDiagnostics.write((ScriptContainer) (Object) this, cir.getReturnValue());
        ScriptHooks.write((ScriptContainer) (Object) this, cir.getReturnValue(), Data.keySet());
    }
}
