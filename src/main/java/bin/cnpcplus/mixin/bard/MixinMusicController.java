package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardSoundCategory;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import noppes.npcs.client.controllers.MusicController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Bard music plays through the vanilla SoundCategory "bard", which we extend
 * onto the SoundCategory enum (BardSoundCategory). The vanilla sound options
 * screen renders a native slider for it and the volume chain
 * (getClampedVolume = sound volume x getVolume(category)) becomes fully
 * independent of MUSIC/RECORDS. We only swap the category baked into the
 * two PositionedSoundRecord constructions of MusicController.
 */
@Mixin(value = MusicController.class, remap = false)
public class MixinMusicController {

    @Redirect(method = "playStreaming", at = @At(value = "NEW", target = "Lnet/minecraft/client/audio/PositionedSoundRecord;<init>(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/util/SoundCategory;FFZILnet/minecraft/client/audio/ISound$AttenuationType;FFF)V"), remap = false)
    private PositionedSoundRecord cnpcplus$streamCategory(ResourceLocation location, SoundCategory category, float volume, float pitch, boolean repeat, int repeatDelay, ISound.AttenuationType type, float x, float y, float z) {
        return new PositionedSoundRecord(location, BardSoundCategory.BARD, volume, pitch, repeat, repeatDelay, type, x, y, z);
    }

    @Redirect(method = "playMusic", at = @At(value = "NEW", target = "Lnet/minecraft/client/audio/PositionedSoundRecord;<init>(Lnet/minecraft/util/ResourceLocation;Lnet/minecraft/util/SoundCategory;FFZILnet/minecraft/client/audio/ISound$AttenuationType;FFF)V"), remap = false)
    private PositionedSoundRecord cnpcplus$musicCategory(ResourceLocation location, SoundCategory category, float volume, float pitch, boolean repeat, int repeatDelay, ISound.AttenuationType type, float x, float y, float z) {
        return new PositionedSoundRecord(location, BardSoundCategory.BARD, volume, pitch, repeat, repeatDelay, type, x, y, z);
    }
}