package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardLoopKeeper;
import bin.cnpcplus.bard.BardLoopStore;
import bin.cnpcplus.bard.SongListStore;
import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.MusicTicker;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import noppes.npcs.CustomNpcs;
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
 * <h3>播放权转移（3.4.1 修复，别再删）</h3>
 * 本类用 {@code ci.cancel()} 接管了整个 onLivingUpdate，因此必须自己复刻
 * 原版 {@code JobBard.onLivingUpdate:75-79} 的「更近者直接改写 playingEntity」。
 * 漏掉它会导致「走进另一个放同一首歌的诗人范围时直接断歌」——
 * 因为 {@code MusicController.isPlaying} 只比曲目不比实体，乙的 play* 会被短路，
 * playingEntity 永远留在甲身上，甲一走远就把乙的歌停了。详见方法内注释。
 *
 * <h3>循环续播的驱动源</h3>
 * 循环续播原本只写在本方法里，而本方法是实体 tick 的一部分：诗人被区块卸载后
 * 就不再执行，于是「走得足够远之后 bgm 不再重播」。真正的兜底在
 * {@link bin.cnpcplus.bard.BardLoopKeeper}（挂客户端全局 tick）。
 * 两者以「心跳」互斥：诗人还在 tick 时 keeper 完全沉默，避免它重播旧曲
 * 而破坏范围内的歌单切歌。
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
@SideOnly(Side.CLIENT)
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

        try {
            Class<?> mcClass = Class.forName("noppes.npcs.client.controllers.MusicController");
            Field instanceField = mcClass.getField("Instance");
            Object c = instanceField.get(null);
            
            SoundHandler sm = Minecraft.getMinecraft().getSoundHandler();
            EntityPlayer player = CustomNpcs.proxy.getPlayer();
            if (player == null) return;

            boolean looping = BardLoopStore.isLooping(self);
            
            Field playingResourceField = mcClass.getField("playingResource");
            Object playingResourceObj = playingResourceField.get(c);
            String current = playingResourceObj == null ? "" : playingResourceObj.toString();
            
            Field playingField = mcClass.getField("playing");
            Object playing = playingField.get(c);
            boolean active = playing != null && sm.isSoundPlaying((net.minecraft.client.audio.ISound) playing);
            
            double distSq = self.npc.getDistanceSq(player);
            boolean inMinRange = distSq <= (double) (self.minRange * self.minRange);

            Field playingEntityField = mcClass.getField("playingEntity");
            Object playingEntity = playingEntityField.get(c);
            
            // 播放权转移（哈基彬反馈 A-1 的真正根因，务必保留）。
            if (active && playingEntity != null && playingEntity != self.npc
                    && !current.isEmpty() && cnpcplus$ownsSong(self, songs, fallback, current)
                    && distSq < player.getDistanceSq((net.minecraft.entity.Entity) playingEntity)) {
                playingEntityField.set(c, self.npc);
                BardLoopKeeper.record(self, current);
            }

            boolean mine = playingEntity == self.npc && playing != null;
            // 只要这只诗人还在 tick，兜底续播器就不插手（分工见 BardLoopKeeper 类注释）。
            if (mine) BardLoopKeeper.heartbeat(self);

            // 断歌源 1：离开距离。开了循环就不因距离停歌，保持当前这首继续放。
            if (mine && active && self.hasOffRange && !looping
                    && distSq > (double) (self.maxRange * self.maxRange)) {
                mcClass.getMethod("stopMusic").invoke(c);
                BardLoopKeeper.clear();
                return;
            }

            if (!current.isEmpty() && current.equals(this.cnpcplus$lastPicked)) {
                boolean expired = System.currentTimeMillis() - this.cnpcplus$lastPlay
                        >= CnpcPlusConfig.getBardWatchdogSeconds() * 1000L;
                if (active && !expired) return;
                if (!active && System.currentTimeMillis() - this.cnpcplus$lastPlay < 500L) return;
                // 看门狗强制换曲属于「切歌」，按需求只在触发距离内生效。
                if (active && expired && (inMinRange || !looping)) {
                    mcClass.getMethod("stopMusic").invoke(c);
                    BardLoopKeeper.clear();
                    this.cnpcplus$lastPlay = 0L;
                    this.cnpcplus$lastPicked = "";
                } else if (active) {
                    return;
                }
            }
            if (mine && active) return;

            // 更近的吟游诗人优先。放在循环续播之前，保证「走进别人的范围就换人」。
            // 3.4.2: 加入距离差阈值，避免交界处因微小距离变化而频繁切歌（哈基彬反馈）。
            if (playing != null && playingEntity != null && playingEntity != self.npc && active) {
                double otherDistSq = player.getDistanceSq((net.minecraft.entity.Entity) playingEntity);
                // 必须显著更近（距离平方差 > 4.0，约 2 格）才抢占，否则维持当前播放。
                if (distSq > otherDistSq - 4.0) return;
            }

            // 断歌源 2：超出触发距离。循环时不掐掉当前曲，只是不再开新曲。
            if (!inMinRange) {
                if (looping && mine && !active && !this.cnpcplus$lastPicked.isEmpty()) {
                    if (CnpcPlusConfig.getBardVolume() <= 0.0F) return;
                    cnpcplus$play(self, c, mcClass, playingEntityField, this.cnpcplus$lastPicked);
                    return;
                }
                if (mine && !looping) {
                    mcClass.getMethod("stopMusic").invoke(c);
                    BardLoopKeeper.clear();
                }
                return;
            }

            String picked = fallback ? self.song : SongListStore.pick(self, this.cnpcplus$lastSong);
            if (picked == null || picked.isEmpty()) return;
            cnpcplus$play(self, c, mcClass, playingEntityField, picked);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 这只诗人的曲库里是否包含正在播放的这首曲子。
     */
    @Unique
    private boolean cnpcplus$ownsSong(JobBard self, List<String[]> songs, boolean fallback, String current) {
        if (fallback) {
            return !self.song.isEmpty() && self.song.equals(current);
        }
        for (String[] entry : songs) {
            if (entry != null && entry.length > 0 && current.equals(entry[0])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一的播放出口。
     */
    @Unique
    private void cnpcplus$play(JobBard self, Object c, Class<?> mcClass, Field playingEntityField, String song) {
        try {
            if (self.isStreamer) {
                mcClass.getMethod("playStreaming", String.class, net.minecraft.entity.Entity.class)
                       .invoke(c, song, self.npc);
            } else {
                mcClass.getMethod("playMusic", String.class, net.minecraft.entity.Entity.class)
                       .invoke(c, song, self.npc);
            }
            playingEntityField.set(c, self.npc);
            BardLoopKeeper.record(self, song);
            this.cnpcplus$lastPicked = song;
            this.cnpcplus$lastSong = song;
            this.cnpcplus$lastPlay = System.currentTimeMillis();
            Field f = MusicTicker.class.getDeclaredField("field_147676_d");
            f.setAccessible(true);
            f.setInt(Minecraft.getMinecraft().getMusicTicker(), 12000);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
