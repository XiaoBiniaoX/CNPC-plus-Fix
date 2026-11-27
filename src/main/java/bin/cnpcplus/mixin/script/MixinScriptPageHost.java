package bin.cnpcplus.mixin.script;

import bin.cnpcplus.api.script.LanguageDescriptor;
import bin.cnpcplus.api.script.ScriptHostApi;
import bin.cnpcplus.api.script.SourceBundle;
import bin.cnpcplus.script.ScriptRuntimeHost;
import java.util.List;
import noppes.npcs.controllers.IScriptHandler;
import noppes.npcs.controllers.ScriptContainer;
import net.minecraft.nbt.NBTTagCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the stable addon page handle and its persisted per-page language override. */
@Mixin(value = ScriptContainer.class, remap = false)
public abstract class MixinScriptPageHost implements ScriptRuntimeHost.PageAccess {
    @Shadow private IScriptHandler handler;
    @Shadow public String script;
    @Shadow public List<String> scripts;

    @Unique private String cnpcplus$language = "";
    @Unique private volatile long cnpcplus$revision;
    @Unique private volatile long cnpcplus$generation;
    @Unique private String cnpcplus$fingerprint = "";
    @Unique private String cnpcplus$sourceFingerprint = "";
    @Unique private String cnpcplus$readFingerprint;

    @Override public String cnpcplus$language() { return cnpcplus$language; }
    @Override public void cnpcplus$language(String value) { cnpcplus$language = value == null ? "" : value; }
    @Override public long cnpcplus$revision() { return cnpcplus$revision; }
    @Override public void cnpcplus$revision(long value) { cnpcplus$revision = value; }
    @Override public long cnpcplus$generation() { return cnpcplus$generation; }
    @Override public void cnpcplus$generation(long value) { cnpcplus$generation = value; }
    @Override public String cnpcplus$fingerprint() { return cnpcplus$fingerprint; }
    @Override public void cnpcplus$fingerprint(String value) { cnpcplus$fingerprint = value == null ? "" : value; }
    @Override public String cnpcplus$sourceFingerprint() { return cnpcplus$sourceFingerprint; }
    @Override public void cnpcplus$sourceFingerprint(String value) { cnpcplus$sourceFingerprint = value == null ? "" : value; }
    @Override public IScriptHandler cnpcplus$handler() { return handler; }
    @Override public String cnpcplus$effectiveRawLanguage() {
        String value = cnpcplus$language.isEmpty() ? handler == null ? "" : handler.getLanguage() : cnpcplus$language;
        if (ScriptHostApi.isExplicitLanguageId(value)) {
            LanguageDescriptor descriptor = ScriptHostApi.language(value).orElse(null);
            if (descriptor != null && !ScriptHostApi.runtimeLanguageId(descriptor.id()).isPresent()) return descriptor.displayName();
        }
        return value;
    }
    @Override public String cnpcplus$effectiveLanguageId() {
        String value = cnpcplus$language.isEmpty() ? handler == null ? "" : handler.getLanguage() : cnpcplus$language;
        return ScriptHostApi.language(value).map(LanguageDescriptor::id).orElse(value);
    }

    @Override public String contextKind() { return ScriptRuntimeHost.contextKind((ScriptContainer)(Object)this); }
    @Override public String languageOverride() { return cnpcplus$language; }
    @Override public String effectiveLanguageId() { return cnpcplus$effectiveLanguageId(); }
    @Override public SourceBundle source() { return ScriptRuntimeHost.sourceSnapshot((ScriptContainer)(Object)this); }
    @Override public long generation() { return cnpcplus$generation(); }
    @Override public boolean isPresent() { return ScriptRuntimeHost.isPresent((ScriptContainer)(Object)this); }
    @Override public void setLanguageOverride(String languageIdOrEmpty) { ScriptRuntimeHost.setLanguageOverride((ScriptContainer)(Object)this, languageIdOrEmpty); }
    @Override public void reload() { ScriptRuntimeHost.reload((ScriptContainer)(Object)this); }
    @Override public void cnpcplus$release() { ScriptRuntimeHost.release((ScriptContainer)(Object)this); }

    @Inject(method = "readFromNBT", at = @At("HEAD"), require = 1)
    private void cnpcplus$releaseBeforeRead(NBTTagCompound nbt, CallbackInfo ci) {
        ScriptContainer page = (ScriptContainer)(Object)this;
        cnpcplus$readFingerprint = ScriptRuntimeHost.currentFingerprint(page);
        ScriptRuntimeHost.release(page);
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readLanguage(NBTTagCompound nbt, CallbackInfo ci) {
        cnpcplus$language = ScriptRuntimeHost.normalizeStoredOverride(nbt.getString("CNPCPlusLanguage"));
        ScriptContainer page = (ScriptContainer)(Object)this;
        bin.cnpcplus.script.ScriptDiagnostics.read(page, nbt);
        ScriptRuntimeHost.initializeSnapshot(page, cnpcplus$readFingerprint);
        ScriptRuntimeHost.readDiagnostics(page, nbt);
        cnpcplus$readFingerprint = null;
    }

    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$writeLanguage(NBTTagCompound nbt, CallbackInfoReturnable<NBTTagCompound> cir) {
        if (cnpcplus$language.isEmpty()) nbt.removeTag("CNPCPlusLanguage");
        else nbt.setString("CNPCPlusLanguage", cnpcplus$language);
    }
}
