package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptLanguageRuntime;
import javax.script.ScriptEngine;
import noppes.npcs.controllers.ScriptController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.cnpcplus.api.script.ScriptLanguageApi;

@Mixin(value = ScriptController.class, remap = false)
public abstract class MixinScriptLanguageController {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void cnpcplus$installLanguages(CallbackInfo ci) { ScriptLanguageRuntime.attach((ScriptController)(Object)this); }
    @Inject(method = "getEngineByName(Ljava/lang/String;)Ljavax/script/ScriptEngine;", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$registeredEngine(String name, CallbackInfoReturnable<ScriptEngine> cir) {
        if (ScriptLanguageApi.find(name).isPresent()) cir.setReturnValue(ScriptLanguageRuntime.create(name));
    }
    @Inject(method = "loadCategories()V", at = @At("HEAD"), require = 1)
    private void cnpcplus$reloadPages(CallbackInfo ci) { ScriptLanguageRuntime.reload(); }
}
