package top.cnpcplus.bard;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobBard;

/**
 * 「循环播放」的续播驱动。
 *
 * <p>为什么不能只靠 {@code JobBard.aiStep}：诗人的 aiStep 由
 * {@code EntityNPCInterface} 的**客户端**分支驱动，玩家一走远，NPC 就离开客户端实体
 * 加载范围，aiStep 直接不再被调用。写在那里的续播永远等不到执行时机，
 * 结果就是「开了循环，走远之后还是断歌」—— 与当年 hasOffRange 失效是同一个结构性坑，
 * 所以解法也一样：交给 {@code SoundEngine.tickNonPaused} 每 tick 驱动。
 *
 * <p>与 {@link BardRangeGuard} 的分工：那个决定「该不该停」，这个决定「停了之后要不要
 * 立刻续上同一首」。两者都在同一处每 tick 调用，顺序是先判停、再判续播。
 *
 * <p>刻意不走 OpenAL 硬件循环（给 SimpleSoundInstance 传 isLooping=true）：那样
 * {@code SoundEngine.play} 会命中 {@code shouldLoopAutomatically} → {@code Channel.setLooping(true)}，
 * 声音永不结束，{@code isActive} 恒真，诗人的夺权与切歌逻辑全部失效。第十一轮已实证过。
 *
 * <p>放在 mixin 包外：普通 mixin 类不能被外部引用（{@code IllegalClassLoadError}）。
 */
public final class BardLoopKeeper {

    private BardLoopKeeper() {
    }

    /**
     * 若当前诗人开了循环播放且本首已自然结束，则按歌单权重选下一首再播。
     *
     * @return true 表示本 tick 已经重新起播（调用方应跳过后续的音量刷新，
     *         因为 playing 实例已经被换掉了）
     */
    public static boolean tickLoop(MusicController c) {
        if (c == null || c.playing == null) return false;

        Entity source = c.playingEntity;
        if (!(source instanceof EntityNPCInterface npc)) return false;
        if (!(npc.job instanceof JobBard bard)) return false;

        // 只处理开了循环播放的诗人。
        if (!bard.isLooping) return false;

        // 诗人已经不在世界里就不要续播了，交给 BardRangeGuard 去停。
        if (npc.isRemoved() || !npc.isAlive()) return false;

        // 还在响就什么都不做。
        if (Minecraft.getInstance().getSoundManager().isActive(c.playing)) return false;

        // 取当前曲目。必须在 stopMusic 之前取，它会把 playingResource 置空。
        if (c.playingResource == null) return false;
        String oldSong = c.playingResource.toString();
        if (oldSong.isEmpty()) return false;

        // 先停再播：playStreaming/playMusic 的第一行是 `if (isPlaying(music)) return;`，
        // 不先清掉 playingResource，下一首若碰巧同名会被静默吞掉。
        c.stopMusic();
        String next = SongListStore.pick(bard, oldSong);
        if (next == null || next.isEmpty()) {
            next = bard.song;
        }
        if (next == null || next.isEmpty()) return false;
        if (bard.isStreamer) {
            c.playStreaming(next, npc, false);
        } else {
            c.playMusic(next, npc, false);
        }
        return true;
    }
}
