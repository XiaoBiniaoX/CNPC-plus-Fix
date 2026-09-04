package top.cnpcplus.mixin;

import com.mojang.blaze3d.audio.Channel;
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
import top.cnpcplus.bard.BardLoopKeeper;
import top.cnpcplus.bard.BardRangeGuard;
import top.cnpcplus.config.CnpcPlusConfigData;

import java.util.Map;

@Mixin(SoundEngine.class)
public abstract class MixinSoundEngine {

    // MixinGradle 的 refmap 只收录 @Inject/@Redirect/@Accessor 的目标，不收录 @Shadow。
    // 开发环境是 official(mojmap)，生产环境是 SRG，所以 @Shadow 必须靠 aliases 补生产名，
    // 否则整个 mixin 在生产会因「was not located in the target class」被整体丢弃。
    @Shadow(aliases = "f_120226_")
    private Map<SoundInstance, ChannelAccess.ChannelHandle> instanceToChannel;

    @Shadow(aliases = "m_120327_")
    private float calculateVolume(SoundInstance inst) { return 0.0F; }

    @Shadow(aliases = "m_120324_")
    private float calculatePitch(SoundInstance inst) { return 1.0F; }

    @Unique
    private SoundInstance cnpcplus$playing;

    @Unique
    private SoundInstance cnpcplus$calcInst;

    @Inject(method = "play", at = @At("HEAD"))
    private void cnpcplus$markPlaying(SoundInstance inst, CallbackInfo ci) {
        this.cnpcplus$playing = inst;
    }

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("HEAD"))
    private void cnpcplus$markCalc(SoundInstance inst, CallbackInfoReturnable<Float> cir) {
        this.cnpcplus$calcInst = inst;
    }

    @Redirect(method = "play", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float cnpcplus$playVolume(SoundEngine self, float volume, SoundSource source) {
        return cnpcplus$applyBardVolume(this.cnpcplus$playing, volume, source);
    }

    @Redirect(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/sounds/SoundEngine;calculateVolume(FLnet/minecraft/sounds/SoundSource;)F"))
    private float cnpcplus$calcVolume(SoundEngine self, float volume, SoundSource source) {
        return cnpcplus$applyBardVolume(this.cnpcplus$calcInst, volume, source);
    }

    @Redirect(method = "tickNonPaused", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getSoundSourceVolume(Lnet/minecraft/sounds/SoundSource;)F"))
    private float cnpcplus$tickSourceVolume(Options options, SoundSource source) {
        // 原版 tickNonPaused 对源音量为 0 的通道执行 stop + remove。
        // 吟游诗人使用 MUSIC/RECORDS 通道，玩家把这两个通道调 0 时通道会被每 tick 掐死，
        // 导致 isActive 恒为 false，引发 500ms 重播叠加、距离检查失效、滑块失去实时控制。
        // 这里仅在诗人正在播放时保活其所属通道，真实音量由 applyBardVolume 决定。
        if ((source == SoundSource.MUSIC || source == SoundSource.RECORDS)
                && MusicController.Instance != null && MusicController.Instance.playing != null) {
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

        // 循环播放的续播同样必须由声音引擎驱动，不能只放在 aiStep：
        // 玩家走远后诗人不再 tick，写在 aiStep 里的续播等不到执行，仍会断歌。
        // 返回 true 表示本 tick 已换了新实例，跳过下面的音量刷新（下一 tick 会正常刷）。
        if (BardLoopKeeper.tickLoop(c)) return;

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
        if (inst != null && MusicController.Instance != null && inst == MusicController.Instance.playing) {
            // 诗人音乐只受「吟游诗人」滑块与主音量控制，不受被保活的 MUSIC/RECORDS 通道影响
            float master = Minecraft.getInstance().options.getSoundSourceVolume(SoundSource.MASTER);
            return (float) (volume * master * CnpcPlusConfigData.BardVolume.get().doubleValue());
        }
        return volume * Minecraft.getInstance().options.getSoundSourceVolume(source);
    }
}
