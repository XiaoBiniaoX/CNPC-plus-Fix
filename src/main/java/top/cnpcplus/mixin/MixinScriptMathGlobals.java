package top.cnpcplus.mixin;

import java.util.Locale;
import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import noppes.npcs.controllers.Jsr223Executor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.script.tools.ScriptApiRegistry;

@Mixin(value = Jsr223Executor.class, remap = false)
public abstract class MixinScriptMathGlobals {
    @Shadow private ScriptEngine engine;

    @Inject(method = "initialize", at = @At("TAIL"), require = 1)
    private void cnpcplus$extendMath(String language, Map<String, Object> globals, CallbackInfo ci) {
        if (engine == null) return;
        String name = engine.getFactory().getLanguageName().toLowerCase(Locale.ROOT);
        if (!name.contains("javascript") && !name.contains("ecmascript")) return;
        try {
            ScriptApiRegistry.extendJavaScriptMath(engine);
        } catch (ScriptException error) {
            org.slf4j.LoggerFactory.getLogger("cnpcplus.script").warn("Could not extend JavaScript Math. CNPCPlusMath remains available.", error);
        }
    }
}
