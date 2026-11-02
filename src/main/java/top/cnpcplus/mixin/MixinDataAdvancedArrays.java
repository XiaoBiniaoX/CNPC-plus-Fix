package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.lines.ArrayLine;
import top.cnpcplus.lines.LinesData;
import top.cnpcplus.lines.NpcSoundArrays;

@Mixin(value = DataAdvanced.class, remap = false)
public abstract class MixinDataAdvancedArrays {
    @Inject(method = "setLine", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$unlimited(int type, int slot, String text, String sound, CallbackInfo ci) {
        Lines lines = LinesData.category((DataAdvanced) (Object) this, type);
        if (lines != null) {
            slot = Math.max(0, slot);
            if (text == null || text.isEmpty()) lines.lines.remove(slot);
            else {
                ArrayLine line = lines.lines.containsKey(slot) ? ArrayLine.of(lines.lines.get(slot)) : new ArrayLine();
                line.setText(text);
                line.setSound(sound);
                lines.lines.put(slot, line);
            }
        }
        ci.cancel();
    }
    @Inject(method = "readToNBT", at = @At("RETURN"))
    private void cnpcplus$readSounds(CompoundTag tag, CallbackInfo ci) { NpcSoundArrays.read((DataAdvanced) (Object) this, tag); }
    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveSounds(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        NpcSoundArrays value = NpcSoundArrays.get((DataAdvanced) (Object) this);
        CompoundTag out = cir.getReturnValue();
        value.save(out);
        String[] legacy = {"NpcIdleSound", "NpcAngrySound", "NpcHurtSound", "NpcDeathSound", "NpcStepSound"};
        for (int i = 0; i < 5; i++) out.putString(legacy[i], value.slots[i].first());
    }
    @Inject(method = "getSound", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$stableSound(int type, CallbackInfoReturnable<String> cir) {
        NpcSoundArrays value = NpcSoundArrays.peek((DataAdvanced) (Object) this);
        if (value != null && type >= 0 && type < 5) {
            String first = value.slots[type].first();
            if (first.isEmpty()) first = value.slots[type].sounds.stream().filter(s -> s != null && !s.isBlank()).findFirst().orElse("");
            cir.setReturnValue(first.isEmpty() ? null : first);
        }
    }
    @Inject(method = "setSound", at = @At("RETURN"))
    private void cnpcplus$singleSound(int type, String sound, CallbackInfo ci) {
        NpcSoundArrays value = NpcSoundArrays.peek((DataAdvanced) (Object) this);
        if (value != null && type >= 0 && type < 5) value.slots[type].single(sound);
    }
    @Redirect(method = "playSound", at = @At(value = "INVOKE", target = "Lnoppes/npcs/entity/data/DataAdvanced;getSound(I)Ljava/lang/String;"))
    private String cnpcplus$chooseSound(DataAdvanced advanced, int type) {
        if (type < 0 || type >= 5) return null;
        String sound = NpcSoundArrays.get(advanced).slots[type].choose();
        return sound.isEmpty() ? null : sound;
    }
    @ModifyVariable(method = "playSound", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private float cnpcplus$independentPitch(float pitch, int type, float volume, float originalPitch) {
        return type >= 0 && type < 5 ? NpcSoundArrays.get((DataAdvanced) (Object) this).slots[type].pitch() : pitch;
    }
}
