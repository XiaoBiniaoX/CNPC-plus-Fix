package top.cnpcplus.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;
import noppes.npcs.roles.JobInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.bard.SongListStore;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardClient {

    @Shadow(remap = false)
    public String song;

    @Unique
    private long cnpcplus$lastPlay = 0L;

    @Unique
    private String cnpcplus$lastPicked = "";

    @Unique
    private String cnpcplus$lastSong = "";

    @Unique
    private boolean cnpcplus$lastActive = false;

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$bardAiStep(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (!((JobInterface) self).npc.isClientSide()) return;
        List<String[]> songs = SongListStore.get(self);
        boolean fallback = false;
        if (songs == null || songs.isEmpty()) {
            if (self.song.isEmpty()) {
                return;
            }
            fallback = true;
            songs = new ArrayList<>();
            songs.add(new String[]{self.song, "1"});
        }
        ci.cancel();

        Minecraft mc = Minecraft.getInstance();
        MusicController c = MusicController.Instance;
        SoundManager sm = mc.getSoundManager();
        Player player = CustomNpcs.proxy.getPlayer();
        if (player == null) return;

        boolean active = c.playing != null && sm.isActive(c.playing);
        boolean mine = c.playing != null && c.playingEntity == self.npc;
        boolean inStartRange = self.npc.level().getEntitiesOfClass(
                Player.class,
                self.npc.getBoundingBox().inflate(self.minRange, self.minRange / 2.0, self.minRange)
        ).contains(player);

        // 原版语义：minRange 只决定是否开始播放，不负责停止已经开始的音乐。
        // 只有开启 hasOffRange 后，玩家离开 maxRange 才停止。
        //
        // 停止判定不放在这里：玩家走远后本 NPC 会离开客户端实体加载范围，aiStep 不再被调用，
        // 写在这里的 stopMusic 永远等不到执行时机（表现就是「勾了停止距离激活也不断歌」）。
        // 判定已移到 BardRangeGuard，由 SoundEngine.tickNonPaused 每 tick 驱动。

        // 开了循环播放时，本首播完要立刻重播同一首，而且不受 minRange 限制 ——
        // 否则玩家走出起始范围后本 tick 就没人续播，仍会出现 BGM 空窗。
        boolean loop = self.isLooping;

        if (mine) {
            if (active) {
                // 歌单里已经没有当前曲（玩家删了正在播的那首）时立刻停，让下面重新选。
                if (!fallback && c.playingResource != null) {
                    String current = c.playingResource.toString();
                    boolean stillListed = false;
                    for (String[] e : songs) {
                        if (e[0].equals(current)) {
                            stillListed = true;
                            break;
                        }
                    }
                    if (!stillListed) {
                        c.stopMusic();
                        this.cnpcplus$lastSong = "";
                    } else {
                        return;
                    }
                } else {
                    return;
                }
            } else if (loop) {
                // 循环续播只交给 BardLoopKeeper：它按歌单 pick 下一首，且不依赖 NPC 仍在 tick。
                // 这里绝不能 stopMusic，否则 playingResource 被清空，keeper 无法得知刚播完的曲。
                // 也绝不能在这里重播 lastSong，那会把歌单锁死成最后一首。
                return;
            } else {
                c.stopMusic();
                this.cnpcplus$lastPlay = 0L;
                this.cnpcplus$lastPicked = "";
            }
        }

        // 尚未播放时必须进入 minRange 才能开始；离开 minRange 不会终止已在播放的音乐。
        // 切歌同样受这条约束（用户要求「切歌相关在触发距离内有效」）。
        if (!inStartRange) return;

        // 其他 NPC 正在播放：通常仍由更近者接管，避免两个诗人范围重叠时每 tick 互相抢歌。
        //
        // 3.4.0 的例外：如果旧诗人开了循环、音乐因而没有在走远后停止，但玩家已经不在
        // 旧诗人的 minRange（触发距离）内，此时走进新诗人的 minRange 就应允许新诗人接管，
        // 即使新诗人比旧诗人略远。否则旧循环曲会永远霸占播放权，违背「进入其他诗人范围
        // 应播放其他诗人音乐」的需求。
        //
        // 这个例外只看「旧诗人已不在触发范围」，所以两个触发范围真正重叠时仍保留更近者
        // 优先，不会发生 A/B 每 tick 互相 stop+play 的抖动。
        if (c.playing != null && c.playingEntity != null && c.playingEntity != self.npc) {
            boolean oldOutsideStartRange = false;
            if (c.playingEntity instanceof noppes.npcs.entity.EntityNPCInterface oldNpc
                    && oldNpc.job instanceof JobBard oldBard) {
                oldOutsideStartRange = !oldNpc.level().getEntitiesOfClass(
                        Player.class,
                        oldNpc.getBoundingBox().inflate(oldBard.minRange,
                                oldBard.minRange / 2.0, oldBard.minRange)
                ).contains(player);
            }
            if (!oldOutsideStartRange
                    && !self.npc.closerThan(player, c.playingEntity.distanceTo(player))) return;
        }

        if (active != this.cnpcplus$lastActive) {
            this.cnpcplus$lastActive = active;
        }

        String picked = fallback ? self.song : SongListStore.pick(self, this.cnpcplus$lastSong);
        if (picked == null || picked.isEmpty()) return;
        cnpcplus$play(c, self, picked);
    }

    /** 实际起播 + 记录状态。抽出来是因为循环重播与正常选曲两条路径都要用。 */
    @Unique
    private void cnpcplus$play(MusicController c, JobBard self, String song) {
        if (self.isStreamer) {
            c.playStreaming(song, self.npc, false);
        } else {
            c.playMusic(song, self.npc, false);
        }
        this.cnpcplus$lastPicked = song;
        this.cnpcplus$lastSong = song;
        this.cnpcplus$lastPlay = System.currentTimeMillis();
        try {
            ((noppes.npcs.mixin.MusicManagerMixin) Minecraft.getInstance().getMusicManager()).nextSongDelay(12000);
        } catch (Exception ignored) {
        }
    }
}
