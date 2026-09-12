package top.cnpcplus.bard;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobBard;

/**
 * 吟游诗人「停止距离激活」（hasOffRange）与「诗人消失」的停歌判定。
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
     * 「诗人实体已从客户端世界消失」时，判定它是被真正删除还是仅仅区块卸载的距离阈值（格）。
     *
     * <p>取 32：服务端停止 tracking 的距离由 entityDistanceScaling × 视距决定，实际远大于
     * 这个值（最低视距 2 也有 32 格，常见 8~12 视距是 128 格以上）；而玩家亲手用魔杖删掉
     * 一个 NPC 时必然站在它跟前（十格以内）。两种情形在距离上不会重叠。
     */
    private static final double DELETED_NEAR_SQR = 32.0 * 32.0;

    /**
     * 判断当前正在播放的诗人音乐是否应该停止。
     *
     * @return true 表示该停了；hasOffRange 没勾选时永远返回 false（保持原版语义：走远也不断歌）
     */
    public static boolean shouldStop(MusicController c) {
        if (c == null || c.playing == null) return false;

        Entity source = c.playingEntity;
        if (!(source instanceof EntityNPCInterface npc)) return false;

        // 只处理吟游诗人职业；其他来源（对话音乐等）不受距离影响
        if (!(npc.job instanceof JobBard bard)) return false;

        // 诗人实体已经不在客户端世界里。这里必须区分两种成因，它们的期望行为完全相反：
        //
        //   - 玩家走远，服务端停止 tracking → 只是「卸载」，开了循环就该继续播；
        //   - 玩家（或脚本）把这个 NPC 删掉 → 必须停，否则留下一段无主 BGM。
        //
        // 客户端**无法靠 removalReason 区分**：两者最终都走
        // ClientboundRemoveEntitiesPacket → ClientLevel.removeEntity(id, DISCARDED)，
        // 拿到的 reason 一模一样。能区分的只有距离 —— 见 DELETED_NEAR_SQR 的说明。
        //
        // 注意 playingEntity 是强引用，所以实体被移除后 npc / npc.job / 歌单
        // （SongListStore 的 WeakHashMap 值）全都仍然可达，续播不需要额外缓存快照。
        if (npc.isRemoved()) {
            Player p = Minecraft.getInstance().player;
            if (p == null) return true;
            return p.distanceToSqr(npc.getX(), npc.getY(), npc.getZ()) <= DELETED_NEAR_SQR;
        }

        // 诗人死了就停。死亡不同于被移除：CNPC 的 NPC 死后通常还会按 spawnCycle 复活，
        // 实体仍在世界里，但这段时间不该继续放它的 BGM。
        if (npc.isKilled()) return true;

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
