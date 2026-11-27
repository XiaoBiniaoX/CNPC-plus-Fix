package bin.cnpcplus.mixin.script;

import bin.cnpcplus.script.tools.ScriptEvents;
import java.util.List;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.NBTTags;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = NBTTags.class, remap = false)
public abstract class MixinScriptPageEvents {
    @Inject(method = "GetScript", at = @At("HEAD"), require = 1)
    private static void cnpcplus$releasePages(NBTTagList data, IScriptHandler handler, CallbackInfoReturnable<List<ScriptContainer>> cir) {
        ScriptEvents.release(handler);
        bin.cnpcplus.script.ScriptRuntimeHost.release(handler);
    }
}
