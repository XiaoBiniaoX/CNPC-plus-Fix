package top.cnpcplus.mixin;

import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** CNPC copies Data into each executor's engine bindings before evaluating scripts. */
@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptEngineGlobals {
    @Inject(method = "<clinit>", at = @At("TAIL"), require = 1)
    private static void cnpcplus$globals(CallbackInfo ci) {
        ScriptContainer.Data.put("CNPCPlusLog", org.slf4j.LoggerFactory.getLogger("cnpcplus.script"));
    }
}
