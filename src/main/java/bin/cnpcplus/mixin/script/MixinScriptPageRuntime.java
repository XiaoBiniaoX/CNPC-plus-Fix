package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.*;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.HashSet;
import javax.script.ScriptEngine;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import top.cnpcplus.api.script.ScriptLanguageApi;
import top.cnpcplus.api.script.ScriptPageApi;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptPageRuntime implements ScriptPageAccess {
    @Shadow private IScriptHandler handler;
    @Shadow private ScriptEngine engine;
    @Shadow private String currentScriptLanguage;
    @Shadow private boolean init;
    @Shadow private HashSet<String> unknownFunctions;
    @Shadow public String fullscript;
    @Shadow public boolean errored;
    @Shadow public long lastCreated;
    @Unique private ScriptPageState cnpcplus$page;

    @Override public ScriptPageState cnpcplus$pageState() { return cnpcplus$page; }
    @Override public IScriptHandler cnpcplus$handler() { return handler; }
    @Override public ScriptEngine cnpcplus$engine() { return engine; }
    @Override public void cnpcplus$resetExecutor() {
        engine = null; currentScriptLanguage = null; init = false; unknownFunctions.clear();
        fullscript = ""; lastCreated = 0; errored = false;
    }
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void cnpcplus$createState(IScriptHandler handler, CallbackInfo ci) {
        cnpcplus$page = new ScriptPageState((ScriptContainer)(Object)this, handler);
    }
    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("HEAD"), require = 1)
    private void cnpcplus$beforeLoad(CompoundTag tag, CallbackInfo ci) {
        cnpcplus$page.invalidate(ScriptPageApi.ResetReason.RELOAD);
    }
    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), require = 1)
    private void cnpcplus$loadPage(CompoundTag tag, CallbackInfo ci) { cnpcplus$page.load(tag); }
    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("HEAD"), require = 1)
    private void cnpcplus$beforeSave(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) { cnpcplus$page.refresh(); }
    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), require = 1)
    private void cnpcplus$savePage(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) { cnpcplus$page.save(cir.getReturnValue()); }
    @Redirect(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/controllers/IScriptHandler;getLanguage()Ljava/lang/String;"), require = 1)
    private String cnpcplus$effectiveLanguage(IScriptHandler handler) { return cnpcplus$page.effectiveLanguage(); }
    @WrapMethod(method = "setEngine(Ljava/lang/String;)V")
    private void cnpcplus$selectEngine(String language, Operation<Void> original) {
        try { original.call(ScriptLanguageApi.engineName(language)); }
        catch (RuntimeException | LinkageError failure) {
            cnpcplus$page.invalidate(ScriptPageApi.ResetReason.EXPLICIT);
            errored = true; cnpcplus$page.nativeError(failure);
            org.slf4j.LoggerFactory.getLogger("cnpcplus.script").error("Cannot create script engine {}", language, failure);
        }
        if (engine == null && errored && cnpcplus$page.diagnostic() == null)
            cnpcplus$page.nativeError(new IllegalStateException("Script language unavailable: " + language));
    }
}
