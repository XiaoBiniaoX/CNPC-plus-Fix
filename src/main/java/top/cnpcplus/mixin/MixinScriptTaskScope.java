package top.cnpcplus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import top.cnpcplus.script.tools.ScriptScope;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptTaskScope {
    @Shadow private IScriptHandler handler;

    @WrapMethod(method = "run(Ljava/lang/String;Ljava/lang/Object;)V")
    private void cnpcplus$scriptScope(String type, Object event, Operation<Void> original) {
        ScriptScope.run((ScriptContainer) (Object) this, handler, event, () -> {
            original.call(type, event);
            return null;
        });
    }
}
