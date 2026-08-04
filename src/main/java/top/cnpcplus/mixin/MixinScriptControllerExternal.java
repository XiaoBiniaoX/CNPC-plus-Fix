package top.cnpcplus.mixin;

import noppes.npcs.controllers.ScriptController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.persist.ExternalScriptStore;

import java.util.Map;

@Mixin(value = ScriptController.class, remap = false)
public class MixinScriptControllerExternal {

    @Shadow public Map<String, String> scripts;

    @Inject(method = "loadCategories", at = @At("RETURN"))
    private void cnpcplus$loadExternalScripts(CallbackInfo ci) {
        Map<String, String> external = ExternalScriptStore.loadAll();
        if (external.isEmpty()) return;
        this.scripts.putAll(external);
    }
}
