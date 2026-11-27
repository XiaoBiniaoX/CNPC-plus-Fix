package bin.cnpcplus.mixin.script;

import bin.cnpcplus.api.script.LanguageDescriptor;
import bin.cnpcplus.api.script.ScriptHostApi;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import noppes.npcs.controllers.ScriptController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds registered addon language IDs to CNPC's existing editor language/extension payload. */
@Mixin(value = ScriptController.class, remap = false)
public abstract class MixinScriptLanguageList {
    @Inject(method = "nbtLanguages", at = @At("RETURN"), require = 1)
    private void cnpcplus$appendAddonLanguages(CallbackInfoReturnable<NBTTagList> cir) {
        NBTTagList list = cir.getReturnValue();
        ScriptController controller = (ScriptController)(Object)this;
        for (LanguageDescriptor language : ScriptHostApi.languages()) {
            if (!ScriptHostApi.runtimeLanguageId(language.id()).isPresent()) continue;
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Language", language.id());
            NBTTagList scripts = new NBTTagList();
            for (String path : controller.scripts.keySet())
                for (String extension : language.extensions())
                    if (path.toLowerCase(java.util.Locale.ROOT).endsWith(extension)) { scripts.appendTag(new net.minecraft.nbt.NBTTagString(path)); break; }
            entry.setTag("Scripts", scripts);
            list.appendTag(entry);
        }
    }
}
