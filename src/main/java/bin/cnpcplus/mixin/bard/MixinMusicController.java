package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardSoundCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.controllers.MusicController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Bard music plays through the vanilla SoundCategory "bard", which we extend
 * onto the SoundCategory enum (BardSoundCategory). The vanilla sound options
 * screen renders a native slider for it and the volume chain
 * (getClampedVolume = sound volume x getVolume(category)) becomes fully
 * independent of MUSIC/RECORDS. We rewrite both MusicController play
 * methods via HEAD injection: @Redirect on the NEW constructor is
 * unreliable in this runtime (see findings 7d/9).
 */
@Mixin(value = MusicController.class, remap = false)
public class MixinMusicController {

    @Inject(method = "playStreaming", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$streamCategory(String music, Entity entity, CallbackInfo ci) {
        MusicController self = (MusicController) (Object) this;
        if (self.isPlaying(music)) {
            ci.cancel();
            return;
        }
        self.stopMusic();
        self.playingEntity = entity;
        self.playingResource = new ResourceLocation(music);
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        self.playing = new PositionedSoundRecord(self.playingResource, BardSoundCategory.BARD, 4.0F, 1.0F, false, 0, ISound.AttenuationType.LINEAR, (float) entity.posX, (float) entity.posY, (float) entity.posZ);
        handler.playSound(self.playing);
        ci.cancel();
    }

    @Inject(method = "playMusic", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$musicCategory(String music, Entity entity, CallbackInfo ci) {
        MusicController self = (MusicController) (Object) this;
        if (self.isPlaying(music)) {
            ci.cancel();
            return;
        }
        self.stopMusic();
        self.playingResource = new ResourceLocation(music);
        self.playingEntity = entity;
        SoundHandler handler = Minecraft.getMinecraft().getSoundHandler();
        self.playing = new PositionedSoundRecord(self.playingResource, BardSoundCategory.BARD, 1.0F, 1.0F, false, 0, ISound.AttenuationType.NONE, 0.0F, 0.0F, 0.0F);
        handler.playSound(self.playing);
        ci.cancel();
    }
}