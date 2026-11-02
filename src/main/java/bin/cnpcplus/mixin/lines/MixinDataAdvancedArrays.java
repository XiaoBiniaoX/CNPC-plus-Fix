package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.ArrayLine;
import bin.cnpcplus.lines.LinesData;
import bin.cnpcplus.lines.NpcSoundArrays;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedArrays {
    @Inject(method = "setLine", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$unlimited(int type, int slot, String text, String sound, CallbackInfo ci) {
        Lines lines = LinesData.category((DataAdvanced) (Object) this, type);
        if (lines == null) return;
        slot = Math.max(0, slot);
        if (text == null || text.isEmpty()) lines.lines.remove(slot);
        else {
            ArrayLine line = lines.lines.containsKey(slot) ? ArrayLine.of(lines.lines.get(slot)) : new ArrayLine();
            line.setText(text); line.setSound(sound); lines.lines.put(slot, line);
        }
        ci.cancel();
    }
    @Inject(method = "readToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$readSounds(NBTTagCompound tag, CallbackInfo ci) { NpcSoundArrays.read((DataAdvanced) (Object) this, tag); }
    @Inject(method = "writeToNBT", at = @At("RETURN"), require = 1)
    private void cnpcplus$saveSounds(NBTTagCompound tag, CallbackInfoReturnable<NBTTagCompound> cir) {
        NpcSoundArrays value = NpcSoundArrays.get((DataAdvanced) (Object) this);
        NBTTagCompound out = cir.getReturnValue();
        value.save(out);
        String[] keys = {"NpcIdleSound", "NpcAngrySound", "NpcHurtSound", "NpcDeathSound", "NpcStepSound"};
        for (int i = 0; i < keys.length; i++) out.setString(keys[i], value.slots[i].first());
    }
    @Inject(method = "getSound", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$stableSound(int type, CallbackInfoReturnable<String> cir) {
        NpcSoundArrays value = NpcSoundArrays.peek((DataAdvanced) (Object) this);
        if (value != null && type >= 0 && type < 5) {
            String sound = value.slots[type].sounds.stream().filter(s -> s != null && !s.trim().isEmpty()).findFirst().orElse("");
            cir.setReturnValue(sound.isEmpty() ? null : sound);
        }
    }
    @Inject(method = "setSound", at = @At("RETURN"), require = 1)
    private void cnpcplus$single(int type, String sound, CallbackInfo ci) {
        NpcSoundArrays value = NpcSoundArrays.peek((DataAdvanced) (Object) this);
        if (value != null && type >= 0 && type < 5) value.slots[type].single(sound);
    }
    @Redirect(method = "playSound", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/data/DataAdvanced;getSound(I)Ljava/lang/String;"), require = 1)
    private String cnpcplus$choose(DataAdvanced advanced, int type) {
        if (type < 0 || type >= 5) return null;
        String sound = NpcSoundArrays.get(advanced).slots[type].choose();
        return sound.isEmpty() ? null : sound;
    }
    @ModifyVariable(method = "playSound", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 1)
    private float cnpcplus$pitch(float pitch, int type, float volume, float originalPitch) {
        return type >= 0 && type < 5 ? NpcSoundArrays.get((DataAdvanced) (Object) this).slots[type].pitch() : pitch;
    }
}
