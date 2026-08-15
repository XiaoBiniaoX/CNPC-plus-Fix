package bin.cnpcplus.mixin.bard;

import net.minecraft.util.SoundCategory;
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
 * Bard playback keeps the original MUSIC/RECORDS categories. MixinSoundManager
 * replaces only this exact sound's category calculation with BardVolume while
 * the SoundSystem keeps its native master-volume handling.
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
        self.playing = new PositionedSoundRecord(self.playingResource, SoundCategory.RECORDS, 4.0F, 1.0F, false, 0, ISound.AttenuationType.LINEAR, (float) entity.posX, (float) entity.posY, (float) entity.posZ);
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
        self.playing = new PositionedSoundRecord(self.playingResource, SoundCategory.MUSIC, 1.0F, 1.0F, false, 0, ISound.AttenuationType.NONE, 0.0F, 0.0F, 0.0F);
        handler.playSound(self.playing);
        ci.cancel();
    }
}