package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptPageState;
import java.util.List;
import noppes.npcs.controllers.ScriptContainer;
import noppes.npcs.controllers.data.PlayerScriptData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player handlers have an additional failed-page cache outside ScriptContainer. */
@Mixin(value = PlayerScriptData.class, remap = false)
public abstract class MixinPlayerScriptRuntime {
    @Shadow private List<ScriptContainer> scripts;
    @Shadow private static List<Integer> errored;
    @Inject(method = "runScript", at = @At("HEAD"), require = 1)
    private void cnpcplus$refreshPages(CallbackInfo ci) {
        for (int i = 0; i < scripts.size(); i++) {
            var state = ScriptPageState.of(scripts.get(i)); state.refresh();
            if (state.takeReset()) errored.remove(Integer.valueOf(i));
        }
    }
}
