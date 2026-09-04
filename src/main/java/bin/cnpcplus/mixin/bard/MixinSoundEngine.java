package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardRangeGuard;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
        // 原版 tickNonPaused 会把源音量为 0 的通道 stop + remove。
        // 吟游诗人用 MUSIC/RECORDS 通道，玩家把这两个通道调 0 时通道每 tick 被掐死，
        // 导致 isActive 恒为 false，引发重播叠加与距离检查失效。
        // 这里只保活诗人正在播放的通道，真实音量仍由 cnpcplus$applyBardVolume 决定。
        if ((source == SoundSource.MUSIC || source == SoundSource.RECORDS)
                && MusicController.Instance != null
                && MusicController.Instance.playing != null) {
            return 1.0F;
        }
        return options.getSoundSourceVolume(source);
    }

    @Inject(method = "tickNonPaused", at = @At("TAIL"))
    private void cnpcplus$applyVolumeTick(CallbackInfo ci) {
        MusicController c = MusicController.Instance;
        if (c == null || c.playing == null) return;

        // 「停止距离激活」必须在这里判定，不能只放在 JobBard.aiStep 里。
        // 原因：玩家走远后诗人 NPC 会离开客户端实体加载范围，它的 aiStep 直接不再被调用，
        // 写在 aiStep 里的 stopMusic 就永远没机会执行，于是歌一定被放完。
        // tickNonPaused 由声音引擎每 tick 驱动，与诗人是否还在 tick 无关，判定才可靠。
        if (BardRangeGuard.shouldStop(c)) {
            c.stopMusic();
            return;
        }

        // 开了循环播放且玩家已走出激活距离时，诗人 aiStep 早已不再被调用，
        // 歌自然放完后没人续播，表现就是「循环开着却直接断歌」。这里补上重播。
        boolean active = Minecraft.getInstance().getSoundManager().isActive(c.playing);
        String restart = BardRangeGuard.shouldRestart(c, active);
        if (restart != null) {
            Entity source = c.playingEntity;
            boolean streamer = BardRangeGuard.isStreamer(c);
            // playStreaming/playMusic 内部会先 stopMusic 再新建实例，
            // 必须先清掉 playingResource，否则其 isPlaying 幂等判断会直接 return。
            c.stopMusic();
            if (source != null) {
                if (streamer) {
                    c.playStreaming(restart, source, false);
                } else {
                    c.playMusic(restart, source, false);
                }
            }
            return;
        }

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
                && inst == MusicController.Instance.playing;
        if (bard) {
            return (float) (volume * CnpcPlusConfig.BARD_VOLUME.get().doubleValue());
        }
        return volume * Minecraft.getInstance().options.getSoundSourceVolume(source);
    }
}
