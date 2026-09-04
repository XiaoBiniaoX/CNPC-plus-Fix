package top.cnpcplus.bard;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobBard;

/**
 * 吟游诗人「停止距离激活」（hasOffRange）的判定。
 *
 * <p>为什么不写在 JobBard.aiStep 里：
 * 玩家走远之后，诗人 NPC 会离开客户端的实体加载范围，它的 aiStep 不再被调用，
 * 挂在 aiStep 上的 stopMusic 就永远没机会执行，结果是「勾了停止距离激活也不断歌，
 * 歌一定会被放完」。所以判定改由 SoundEngine.tickNonPaused 每 tick 驱动，
 * 只要音乐还在播就一定会被检查到。
 *
 * <p>注意这个类刻意放在 mixin 包之外：mixin 包内的类不能被外部直接引用，
 * 否则会触发 IllegalClassLoadError。
 */
public final class BardRangeGuard {

    private BardRangeGuard() {
    }

    /**
     * 判断当前正在播放的诗人音乐是否应该因为玩家走出 maxRange 而停止。
     *
     * @return true 表示该停了；hasOffRange 没勾选时永远返回 false（保持原版语义：走远也不断歌）
     */
    public static boolean shouldStop(MusicController c) {
        if (c == null || c.playing == null) return false;

        Entity source = c.playingEntity;
        if (!(source instanceof EntityNPCInterface npc)) return false;

        // 只处理吟游诗人职业；其他来源（对话音乐等）不受距离影响
        if (!(npc.job instanceof JobBard bard)) return false;

        // 诗人已经不在世界里（死亡 / 被移除）就必须停，否则会留下一段无主 BGM。
        //
        // 这一条必须放在 isLooping 豁免之前：客户端实体卸载走的是
        // ClientLevel.EntityCallbacks.onTrackingEnd（javap 确认只调 unRide/onRemovedFromWorld），
        // **根本不会调用 JobBard.delete()**，而 delete() 本身还用 isPlaying(job.song) 判定，
        // 与我们播放的歌单曲目不一致，同样兜不住。所以这里是唯一可靠的兜底点。
        if (npc.isRemoved() || !npc.isAlive()) return true;

        // 循环播放优先于停止距离：用户明确要求「开了循环就不要断歌」，
        // 即使勾了 hasOffRange 也保持当前这首继续循环，避免走出范围时出现 BGM 空窗。
        // 切歌与夺权仍受 minRange 约束（在 MixinJobBardClient 里），所以走进另一个
        // 诗人的范围时依然会换成新诗人的音乐。
        if (bard.isLooping) return false;

        // 用户没开「停止距离激活」时，原版语义是走远也继续播，这里必须放过。
        if (!bard.hasOffRange) return false;

        Player player = Minecraft.getInstance().player;
        if (player == null) return false;

        // 与原版一致用 AABB 判定而非球形：水平取 maxRange，垂直取 maxRange/2。
        return !npc.level().getEntitiesOfClass(
                Player.class,
                npc.getBoundingBox().inflate(bard.maxRange, bard.maxRange / 2.0, bard.maxRange)
        ).contains(player);
    }
}
