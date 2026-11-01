package top.cnpcplus.mixin;

import java.io.PrintWriter;
import noppes.npcs.controllers.Jsr223Executor;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.script.ScriptDiagnostics;

@Mixin(value = Jsr223Executor.class, remap = false)
public abstract class MixinJsr223ExecutorDiagnostic {
    @Shadow private boolean errored;

    @Redirect(
            method = "run(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;",
            at = @At(value = "INVOKE", target = "Ljava/lang/Throwable;printStackTrace(Ljava/io/PrintWriter;)V"),
            require = 1
    )
    private void cnpcplus$captureRuntimeError(Throwable error, PrintWriter writer) {
        ScriptDiagnostics.capture(ScriptContainer.Current, error);
        error.printStackTrace(writer);
    }

    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;", at = @At("RETURN"), require = 1)
    private void cnpcplus$clearAfterSuccessfulRun(String function, Object event,
                                                   CallbackInfoReturnable<String> cir) {
        if (!this.errored) ScriptDiagnostics.clearRuntime(ScriptContainer.Current);
    }
}
