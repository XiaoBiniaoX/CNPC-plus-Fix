package top.cnpcplus.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import javax.script.ScriptEngine;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.script.tools.ScriptEvents;
import top.cnpcplus.script.tools.ScriptScope;

@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptTaskScope {
    @Shadow private IScriptHandler handler;
    @Shadow private ScriptEngine engine;

    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)V", at = @At(value = "INVOKE",
            target = "Ljavax/script/ScriptEngine;eval(Ljava/lang/String;)Ljava/lang/Object;"), require = 1)
    private void cnpcplus$eventBinding(String type, Object event, CallbackInfo ci) {
        ScriptEvents.bind(engine);
    }

    @WrapMethod(method = "run(Ljava/lang/String;Ljava/lang/Object;)V")
    private void cnpcplus$scriptScope(String type, Object event, Operation<Void> original) {
        ScriptScope.run((ScriptContainer) (Object) this, handler, event, () -> {
            original.call(type, event);
            return null;
        });
    }
}
