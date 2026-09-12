package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardRangeGuard;
import bin.cnpcplus.bard.SongListStore;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.mixin.MusicManagerMixin;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardClient {

    @Shadow(remap = false)
    public String song;

    @Unique
    private long cnpcplus$lastPlay = 0L;

    @Unique
    private String cnpcplus$lastSong = "";

    /** 本诗人的歌单里是否包含某首曲子（fallback 时即单曲 song）。 */
    @Unique
    private static boolean cnpcplus$plays(List<String[]> songs, String resource) {
        if (songs == null || resource == null || resource.isEmpty()) return false;
        for (String[] e : songs) {
            if (e != null && e.length > 0 && resource.equals(e[0])) return true;
        }
        return false;
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$bardAiStep(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (!self.npc.isClientSide()) return;
        List<String[]> songs = SongListStore.get(self);
        boolean fallback = false;
        if (songs == null || songs.isEmpty()) {
            if (self.song.isEmpty()) return;
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
        // 与原版一致用 AABB 判定：水平取 minRange，垂直取 minRange/2。
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

        if (mine) {
            if (active) {
                // 仍在播且未超看门狗时长：什么都不做，等它自然放完。
                // 看门狗只负责「卡住不结束」这一种异常，不能顺手把自然结束也一起吃掉，
                // 否则 active 变 false 后走不到下面的选曲逻辑，歌单就只播第一首不续上。
                if (System.currentTimeMillis() - this.cnpcplus$lastPlay
                        < CnpcPlusConfig.BARD_WATCHDOG_SECONDS.get() * 1000L) {
                    return;
                }
            }
            // 自然播放完成（active=false）或看门狗超时：释放旧实例，
            // 允许在起始范围内立即按权重选择下一首。
            c.stopMusic();
            BardRangeGuard.forget();
            this.cnpcplus$lastPlay = 0L;
        }

        // —— 同曲接管：修复「走进另一个播同一首曲子的诗人范围内音乐直接断掉」——
        //
        // 原版 aiStep 在「正在播的就是本 NPC 的曲子」时走的是接管分支：只把 playingEntity 改成
        // 更近的这个 NPC，不重新起播，所以音乐无缝延续，随后的停止距离判定也自然改按新诗人算。
        // 之前这里被写成直接 playStreaming/playMusic，而 MusicController.isPlaying 对同一资源
        // 是幂等的（同资源且仍在播就直接 return），于是 playingEntity 永远停留在旧诗人身上；
        // 玩家一旦走出旧诗人的 maxRange，BardRangeGuard.shouldStop 就按旧诗人判定并 stopMusic，
        // 表现正是「进入新诗人范围后音乐直接断开、且不再播放」。
        //
        // 这一段必须放在 minRange 判定之前：原版接管分支同样不要求进入 minRange，
        // 否则在「已出旧诗人 maxRange、尚未进新诗人 minRange」的那段路上音乐仍会被掐掉。
        if (!mine && active && c.playingResource != null) {
            String current = c.playingResource.toString();
            if (cnpcplus$plays(songs, current)) {
                Entity owner = c.playingEntity;
                boolean ownerIsNpc = owner instanceof EntityNPCInterface;
                // 音源是别的诗人时要求本 NPC 更近；音源不是诗人（循环续播期间挂在玩家身上）时直接接管。
                if (!ownerIsNpc || self.npc.closerThan(player, owner.distanceTo(player))) {
                    c.playingEntity = self.npc;
                    BardRangeGuard.remember(self, current);
                    this.cnpcplus$lastSong = current;
                    this.cnpcplus$lastPlay = System.currentTimeMillis();
                    try {
                        ((MusicManagerMixin) mc.getMusicManager()).nextSongDelay(12000);
                    } catch (Exception ignored) {
                    }
                    return;
                }
            }
        }

        // 尚未播放时必须进入 minRange 才能开始；离开 minRange 不会终止已在播放的音乐。
        if (!inStartRange) return;

        // 其他 NPC 正在播放不同的曲子：仅本 NPC 更近玩家时才换成自己的曲子
        if (c.playing != null && c.playingEntity != null && c.playingEntity != self.npc) {
            if (!self.npc.closerThan(player, c.playingEntity.distanceTo(player))) return;
        }

        String picked = fallback ? self.song : SongListStore.pick(self, this.cnpcplus$lastSong);
        if (picked == null || picked.isEmpty()) return;
        // 这里必须传 false：一旦交给音频引擎做实例级循环，声音永不结束，
        // isActive 恒为真，上面的自然结束分支再也走不到，激活范围内的切歌会彻底失效。
        // 「循环播放」只表示走出停止距离时不断歌，由 BardRangeGuard 负责，与实例循环无关。
        if (self.isStreamer) {
            c.playStreaming(picked, self.npc, false);
        } else {
            c.playMusic(picked, self.npc, false);
        }
        this.cnpcplus$lastSong = picked;
        this.cnpcplus$lastPlay = System.currentTimeMillis();
        // 记账给循环续播用：诗人实体被卸载后，续播只能依赖这份快照。
        BardRangeGuard.remember(self, picked);
        try {
            ((MusicManagerMixin) Minecraft.getInstance().getMusicManager()).nextSongDelay(12000);
        } catch (Exception ignored) {
        }
    }
}
