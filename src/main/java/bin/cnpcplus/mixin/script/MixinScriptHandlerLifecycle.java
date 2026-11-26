package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.ScriptPageState;
import noppes.npcs.controllers.IScriptHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = {"noppes.npcs.entity.data.DataScript", "noppes.npcs.controllers.data.PlayerScriptData",
        "noppes.npcs.controllers.data.ForgeScriptData", "noppes.npcs.blocks.tiles.TileScripted",
        "noppes.npcs.blocks.tiles.TileScriptedDoor", "noppes.npcs.api.wrapper.ItemScriptedWrapper"}, remap = false)
public abstract class MixinScriptHandlerLifecycle {
    @Inject(method = {"setLanguage(Ljava/lang/String;)V", "setEnabled(Z)V"}, at = @At("TAIL"), require = 1)
    private void cnpcplus$handlerChanged(CallbackInfo ci) {
        for (var page : ((IScriptHandler)(Object)this).getScripts()) ScriptPageState.of(page).refresh();
    }
}
