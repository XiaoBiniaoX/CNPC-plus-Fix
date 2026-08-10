package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import noppes.npcs.client.controllers.MusicController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = SoundEngine.class, remap = false)
public abstract class MixinSoundEngine {

    @Shadow
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow
    private float calculateVolume(SoundInstance inst) { return 0.0F; }

    @Shadow
    private float calculatePitch(SoundInstance inst) { return 1.0F; }

    @Unique
    private SoundInstance cnpcplus$playing;

    @Unique
    private SoundInstance cnpcplus$calcInst;

    @Inject(method = "play", at = @At("HEAD"))
    private void cnpcplus$markPlaying(SoundInstance inst, CallbackInfo ci) {
        this.cnpcplus$playing = inst;
    }

    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float cnpcplus$playVolume(SoundEngine self, float volume, SoundSource source) {
        return cnpcplus$applyBardVolume(this.cnpcplus$playing, volume, source);
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("HEAD"))
    private void cnpcplus$markCalc(SoundInstance inst, CallbackInfoReturnable<Float> cir) {
        this.cnpcplus$calcInst = inst;
    }

    @Redirect(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float cnpcplus$calcVolume(SoundEngine self, float volume, SoundSource source) {
        return cnpcplus$applyBardVolume(this.cnpcplus$calcInst, volume, source);
    }

    @Redirect(method = "tickNonPaused", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getSoundSourceVolume(Lnet/minecraft/sounds/SoundSource;)F"))
    private float cnpcplus$tickSourceVolume(Options options, SoundSource source) {
        if ((source == SoundSource.MUSIC || source == SoundSource.RECORDS)
                && MusicController.Instance != null
                && MusicController.Instance.playingResource != null) {
            return 1.0F;
        }
        return options.getSoundSourceVolume(source);
    }

    @Inject(method = "tickNonPaused", at = @At("TAIL"))
    private void cnpcplus$applyVolumeTick(CallbackInfo ci) {
        MusicController c = MusicController.Instance;
        if (c == null || c.playing == null) return;
        ChannelAccess.ChannelHandle h = this.instanceToChannel.get(c.playing);
        if (h != null) {
            float v = this.calculateVolume(c.playing);
            float p = this.calculatePitch(c.playing);
            h.execute(ch -> {
                ch.setVolume(v);
                ch.setPitch(p);
            });
        }
    }

    @Unique
    private float cnpcplus$applyBardVolume(SoundInstance inst, float volume, SoundSource source) {
        boolean bard = inst != null && MusicController.Instance != null
                && MusicController.Instance.playingResource != null
                && inst.getLocation().equals(MusicController.Instance.playingResource);
        if (bard) {
            return (float) (volume * CnpcPlusConfig.BARD_VOLUME.get().doubleValue());
        }
        return volume * Minecraft.getInstance().options.getSoundSourceVolume(source);
    }
}
