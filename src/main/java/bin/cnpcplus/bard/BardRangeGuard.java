package bin.cnpcplus.bard;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.JobBard;

/**
 * 吟游诗人「停止距离激活」（hasOffRange）与「循环播放」（isLooping）的客户端判定。
 *
 * <p>为什么不写在 JobBard.aiStep 里：
 * 玩家走远之后，诗人 NPC 会离开客户端的实体加载范围，它的 aiStep 不再被调用，
 * 挂在 aiStep 上的 stopMusic 和续播都永远没机会执行。所以判定改由
 * SoundEngine.tickNonPaused 每 tick 驱动，只要音乐还在播就一定会被检查到。
 *
 * <p>注意这个类刻意放在 mixin 包之外：mixin 包内的类不能被外部直接引用，
 * 否则会触发 IllegalClassLoadError。
 */
public final class BardRangeGuard {

    private BardRangeGuard() {
    }

    /** 取出当前正在播放的音乐所属的吟游诗人职业，非诗人来源返回 null。 */
    private static JobBard bardOf(MusicController c) {
        if (c == null || c.playing == null) return null;
        Entity source = c.playingEntity;
        if (!(source instanceof EntityNPCInterface npc)) return null;
        // 只处理吟游诗人职业；其他来源（对话音乐等）不受距离影响
        if (!(npc.job instanceof JobBard bard)) return null;
        return bard;
    }

    /** 玩家是否还在该诗人的停止距离内；与原版一致用 AABB，水平取 maxRange，垂直取 maxRange/2。 */
    private static boolean playerInRange(JobBard bard) {
        EntityNPCInterface npc = bard.npc;
        if (npc == null) return false;
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        return npc.level().getEntitiesOfClass(
                Player.class,
                npc.getBoundingBox().inflate(bard.maxRange, bard.maxRange / 2.0, bard.maxRange)
        ).contains(player);
    }

    /**
     * 判断当前正在播放的诗人音乐是否应该因为玩家走出 maxRange 而停止。
     *
     * @return true 表示该停了；未勾选 hasOffRange，或开了循环播放时永远返回 false
     */
    public static boolean shouldStop(MusicController c) {
        JobBard bard = bardOf(c);
        if (bard == null) return false;

        // 用户没开「停止距离激活」时，原版语义是走远也继续播，这里必须放过。
        if (!bard.hasOffRange) return false;

        // 循环播放要求走远也不断，交由 shouldRestart 续播，不在这里停。
        if (bard.isLooping) return false;

        return !playerInRange(bard);
    }

    /**
     * 开了循环播放时，歌自然放完后是否需要立刻重播当前这一首。
     *
     * <p>这是「走出激活距离后断歌」的真正缺口：此时诗人 NPC 已离开客户端实体加载范围，
     * aiStep 不再被调用，没有任何代码会续上下一首，音乐放完即静音。
     * 玩家仍在范围内时不由这里接管，让 aiStep 正常按歌单权重切歌。
     *
     * @return 需要重播的资源路径；不需要重播时返回 null
     */
    public static String shouldRestart(MusicController c, boolean active) {
        if (active) return null;

        JobBard bard = bardOf(c);
        if (bard == null) return null;
        if (!bard.isLooping) return null;

        // 玩家还在范围内时交给 aiStep 走正常的歌单切歌逻辑，避免和它抢。
        if (playerInRange(bard)) return null;

        String song = c.playingResource == null ? null : c.playingResource.toString();
        return song == null || song.isEmpty() ? null : song;
    }

    /** 当前播放来源是否为流式（唱片机）诗人音乐，决定重播走哪个入口。 */
    public static boolean isStreamer(MusicController c) {
        JobBard bard = bardOf(c);
        return bard != null && bard.isStreamer;
    }
}
