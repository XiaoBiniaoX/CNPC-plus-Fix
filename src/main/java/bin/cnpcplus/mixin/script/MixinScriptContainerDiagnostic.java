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
    @Shadow private noppes.npcs.controllers.IScriptHandler handler;
    @Shadow private javax.script.ScriptEngine engine;
    @org.spongepowered.asm.mixin.Unique private javax.script.ScriptEngine cnpcplus$boundEngine;
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void cnpcplus$logBinding(CallbackInfo ci) {
        Data.put("CNPCPlusLog", bin.cnpcplus.CnpcPlus.LOGGER);
        bin.cnpcplus.script.tools.ScriptApiRegistry.bind(Data);
    }
    @Inject(method = "setEngine", at = @At("RETURN"), require = 1)
    private void cnpcplus$math(String language, CallbackInfo ci) {
        if (engine == null || engine == cnpcplus$boundEngine) return;
        bin.cnpcplus.script.tools.ScriptEvents.release((ScriptContainer)(Object)this);
        String name = engine.getFactory().getLanguageName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("ecmascript") || name.contains("javascript")) {
            try { bin.cnpcplus.script.tools.ScriptApiRegistry.extendJavaScriptMath(engine); }
            catch (javax.script.ScriptException error) { throw new IllegalStateException("Unable to register CNPCPlus Math", error); }
        }
        cnpcplus$boundEngine = engine;
    }
    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$observeHook(String type, Object event, CallbackInfo ci) {
        ScriptContainer page = (ScriptContainer)(Object)this;
        ScriptHooks.observe(page, type, event);
        bin.cnpcplus.script.tools.ScriptScope.observe(page, handler, event);
        if (bin.cnpcplus.script.ScriptRuntimeHost.run(page, type, event, new HashMap<>(Data))) {
            bin.cnpcplus.script.tools.ScriptScope.leave();
            ci.cancel();
        }
    }
    @Redirect(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At(value = "INVOKE", target = "Ljava/lang/Throwable;printStackTrace(Ljava/io/PrintWriter;)V"), require = 1)
    private void cnpcplus$captureError(Throwable error, PrintWriter writer) {
        ScriptDiagnostics.capture((ScriptContainer) (Object) this, error);
        bin.cnpcplus.CnpcPlus.LOGGER.error("[script] execution failed", error);
        error.printStackTrace(writer);
    }
    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At("RETURN"), require = 1)
    private void cnpcplus$clearSuccessful(String type, Object event, CallbackInfo ci) {
        ScriptContainer self = (ScriptContainer) (Object) this;
        if (self.isValid()) ScriptDiagnostics.clear(self);
        bin.cnpcplus.script.tools.ScriptScope.leave();
    }
    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readError(NBTTagCompound nbt, CallbackInfo ci) {
        ScriptContainer page = (ScriptContainer) (Object) this;
        ScriptHooks.read(page, nbt);
    }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeError(NBTTagCompound nbt, CallbackInfoReturnable<NBTTagCompound> cir) {
        ScriptDiagnostics.write((ScriptContainer) (Object) this, cir.getReturnValue());
        ScriptHooks.write((ScriptContainer) (Object) this, cir.getReturnValue(), Data.keySet());
        bin.cnpcplus.script.ScriptRuntimeHost.writeDiagnostics((ScriptContainer) (Object) this, cir.getReturnValue());
    }
}
