package top.cnpcplus.mixin;

import noppes.npcs.controllers.IScriptExecutor;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.script.tools.ScriptScope;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptTaskScope {
    @Shadow private IScriptHandler handler;

    @Redirect(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At(value = "INVOKE", target =
            "Lnoppes/npcs/controllers/IScriptExecutor;run(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;"), require = 1)
    private String cnpcplus$scriptScope(IScriptExecutor executor, String type, Object event) {
        return ScriptScope.run((ScriptContainer) (Object) this, handler, event, () -> executor.run(type, event));
    }
}
