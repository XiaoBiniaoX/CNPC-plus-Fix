package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardLoopStore;
import bin.cnpcplus.bard.SongListStore;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Rewrite of the bard client-side playback (1.12.2 onLivingUpdate = 1.20.1 aiStep):
 * weighted playlist picking, watchdog (force-switch a song that plays too long),
 * distance rules (min/maxRange, nearest bard wins) and fallback to the vanilla
 * single song when the playlist is empty.
 *
 * 循环播放（哈基彬的需求，3.4.0 新增）：
 *  - 开了循环后，无论有没有设置离开触发距离，BGM 都不断掉，而是继续循环
 *    当前这一首（自然放完就重播同一首），不切歌。
 *  - 切歌（按权重换下一首）只在触发距离（minRange）内生效。
 *  - 玩家走进另一个吟游诗人的范围时，停旧播新，避免 BGM 空窗。
 *
 * 三处原有断歌源与对应处理：
 *  1. hasOffRange + 超出 maxRange → 原本无条件 stopMusic；循环时跳过。
 *  2. 超出 minRange 且本诗人的歌刚放完 → 原本 stop 并不再续播；
 *     循环时改为重播当前曲。
 *  3. JobBard.delete()（实体卸载/死亡时按 hasOffRange 停歌）
 *     由 MixinJobBardLoopDelete 单独处理。
 *
 * 音量独立性红线（务必保持）：
 *  - 播放一律走 MusicController.playStreaming / playMusic，
 *    绝不自己 new ISound、也不直接调 SoundHandler.playSound。
 *    因为 MixinSoundManager 是靠 {@code sound == MusicController.Instance.playing}
 *    的引用相等来认出「这是吟游诗人的声音」的，换了实例音量就会退化成
 *    受 MUSIC/RECORDS 滑块控制，且 stopMusic 也掐不掉。
 *  - 不使用 PositionedSoundRecord 的 repeat=true 来做循环：那会让声音永不
 *    结束、isSoundPlaying 恒 true，除看门狗外无法切歌，与「切歌在距离内
 *    有效」直接冲突（1.20.1/1.21.1 也都明确否决过这条路）。
 *  - BardVolume 为 0 时 SoundManager.setVolume 会直接 stopSound，
 *    所以循环重播必须跳过 0 音量，否则会每 500ms 疯狂重播。
 */
@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardClient {

    @Unique
    private long cnpcplus$lastPlay = 0L;

    @Unique
    private String cnpcplus$lastPicked = "";

    @Unique
    private String cnpcplus$lastSong = "";

    @Inject(method = "onLivingUpdate", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$bardTick(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (!self.npc.isRemote()) return;
        List<String[]> songs = SongListStore.get(self);
        boolean fallback = false;
        if (songs == null || songs.isEmpty()) {
            if (self.song.isEmpty()) return;
            fallback = true;
            songs = new ArrayList<String[]>();
            songs.add(new String[]{self.song, "1"});
        }
        ci.cancel();

        MusicController c = MusicController.Instance;
        SoundHandler sm = Minecraft.getMinecraft().getSoundHandler();
        EntityPlayer player = CustomNpcs.proxy.getPlayer();
        if (player == null) return;

        boolean looping = BardLoopStore.isLooping(self);
        String current = c.playingResource == null ? "" : c.playingResource.toString();
        boolean active = c.playing != null && sm.isSoundPlaying(c.playing);
        boolean mine = c.playingEntity == self.npc && c.playing != null;
        double distSq = self.npc.getDistanceSq(player);
        boolean inMinRange = distSq <= (double) (self.minRange * self.minRange);

        // 断歌源 1：离开距离。开了循环就不因距离停歌，保持当前这首继续放。
        // 注意这里只在「正在放我的歌」时早退，不影响下面更近的诗人抢占 ——
        // 抢占走的是 mine == false 的分支，两者互不干扰。
        if (mine && active && self.hasOffRange && !looping
                && distSq > (double) (self.maxRange * self.maxRange)) {
            c.stopMusic();
            return;
        }

        if (!current.isEmpty() && current.equals(this.cnpcplus$lastPicked)) {
            boolean expired = System.currentTimeMillis() - this.cnpcplus$lastPlay
                    >= CnpcPlusConfig.getBardWatchdogSeconds() * 1000L;
            if (active && !expired) return;
            if (!active && System.currentTimeMillis() - this.cnpcplus$lastPlay < 500L) return;
            // 看门狗强制换曲属于「切歌」，按需求只在触发距离内生效。
            // 循环模式下距离外维持当前曲，不强制切。
            if (active && expired && (inMinRange || !looping)) {
                c.stopMusic();
                this.cnpcplus$lastPlay = 0L;
                this.cnpcplus$lastPicked = "";
            } else if (active) {
                return;
            }
        }
        if (mine && active) return;

        // 更近的吟游诗人优先。放在循环续播之前，保证「走进别人的范围就换人」，
        // 否则旧诗人会因为循环而永久霸占播放，新诗人永远抢不到。
        if (c.playing != null && c.playingEntity != null && c.playingEntity != self.npc && active) {
            if (distSq > player.getDistanceSq(c.playingEntity)) return;
        }

        // 断歌源 2：超出触发距离。循环时不掐掉当前曲，只是不再开新曲。
        //
        // 顺序很关键：这一段必须排在「循环续播」之前。
        // 哈基彬实测反馈：开循环后在可切歌范围内切歌失效、只重复最后一首。
        // 原因就是我上一版把循环续播放在了 minRange 判断**之前** ——
        // 于是范围内也命中续播分支，永远重播 lastPicked，走不到下面的加权选曲。
        // 现在的语义：范围内 → 正常切歌；范围外 → 才进入循环续播。
        if (!inMinRange) {
            if (looping && mine && !active && !this.cnpcplus$lastPicked.isEmpty()) {
                // 循环续播：离开触发距离后，我的歌自然放完就重播同一首。
                // 不调 stopMusic —— playStreaming/playMusic 内部已有
                // isPlaying 短路与 stopMusic，交给它们处理，避免出现空窗。
                // BardVolume 为 0 时 SoundManager.setVolume 会直接 stopSound，
                // 若此时重播会变成每 500ms 疯狂重启，所以直接跳过。
                if (CnpcPlusConfig.getBardVolume() <= 0.0F) return;
                cnpcplus$play(self, c, this.cnpcplus$lastPicked);
                return;
            }
            if (mine && !looping) c.stopMusic();
            return;
        }

        String picked = fallback ? self.song : SongListStore.pick(self, this.cnpcplus$lastSong);
        if (picked == null || picked.isEmpty()) return;
        cnpcplus$play(self, c, picked);
    }

    /**
     * 统一的播放出口。
     *
     * 必须经由 MusicController，理由见类注释的音量独立性红线。
     */
    @Unique
    private void cnpcplus$play(JobBard self, MusicController c, String song) {
        if (self.isStreamer) {
            c.playStreaming(song, self.npc);
        } else {
            c.playMusic(song, self.npc);
        }
        this.cnpcplus$lastPicked = song;
        this.cnpcplus$lastSong = song;
        this.cnpcplus$lastPlay = System.currentTimeMillis();
        try {
            Field f = MusicTicker.class.getDeclaredField("field_147676_d");
            f.setAccessible(true);
            f.setInt(Minecraft.getMinecraft().getMusicTicker(), 12000);
        } catch (Exception ignored) {
        }
    }
}
