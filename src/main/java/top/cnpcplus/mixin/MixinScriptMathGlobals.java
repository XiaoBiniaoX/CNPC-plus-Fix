package top.cnpcplus.mixin;

import java.util.Locale;
import javax.script.ScriptEngine;
import javax.script.ScriptException;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.script.tools.ScriptApiRegistry;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptMathGlobals {
    @Shadow private ScriptEngine engine;

    @Inject(method = "setEngine", at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.PUTFIELD,
            target = "Lnoppes/npcs/controllers/ScriptContainer;currentScriptLanguage:Ljava/lang/String;",
            shift = At.Shift.AFTER), require = 1)
    private void cnpcplus$extendMath(String language, CallbackInfo ci) {
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
