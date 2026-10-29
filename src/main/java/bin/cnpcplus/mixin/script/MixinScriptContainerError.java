package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptError;
import bin.cnpcplus.script.ScriptErrorAccess;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 复用原生Console/ScriptGet/Save同步链，诊断数据不接受或执行任何代码。 */
@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptContainerError implements ScriptErrorAccess {
    @Shadow(remap = false) public String script;
    @Unique private ScriptError cnpcplus$error;

    @Override public ScriptError cnpcplus$error() { return cnpcplus$error; }

    @Redirect(method = "run(Ljava/lang/String;Ljava/lang/Object;)V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Throwable;printStackTrace(Ljava/io/PrintWriter;)V"),
            require = 1)
    private void cnpcplus$captureError(Throwable error, java.io.PrintWriter writer) {
        cnpcplus$error = ScriptError.from(script, error);
        error.printStackTrace(writer); // 保留原Console，脚本自行print的行号不会进入诊断解析。
    }

    @Inject(method = "save(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"), require = 1)
    private void cnpcplus$saveError(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (cnpcplus$error != null && cnpcplus$error.matches(script)) {
            cir.getReturnValue().put(ScriptError.KEY, cnpcplus$error.save());
        } else cir.getReturnValue().remove(ScriptError.KEY);
    }

    @Inject(method = "load(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("TAIL"), require = 1)
    private void cnpcplus$loadError(CompoundTag tag, CallbackInfo ci) {
        cnpcplus$error = ScriptError.load(tag.getCompound(ScriptError.KEY));
    }
}
