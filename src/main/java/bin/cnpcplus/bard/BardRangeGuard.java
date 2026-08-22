package bin.cnpcplus.bard;

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
