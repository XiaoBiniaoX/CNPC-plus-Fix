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

    /**
     * 「循环续播」的独立记账。
     *
     * <p>不能依赖 MusicController.playingEntity 来做循环续播：诗人实体一旦被客户端卸载
     * （走太远、区块卸载），playingEntity 指向的实体对象会 isRemoved，level() 也不再持有它，
     * 于是 bardOf() 返回的 JobBard 拿不到有效的 npc，playerInRange 必然 false 也无从判断，
     * 更关键的是 MusicController.stopMusic() 会把 playingEntity 与 playingResource 一起清空，
     * 重播时再也找不到「该播哪首、以谁为音源」。这就是「循环开着走远了却彻底断掉、不再重播」的根因。
     *
     * <p>所以在起播时把「曲目 + 是否流式 + 循环开关 + 停止距离 + 诗人坐标」快照下来，
     * 之后的续播完全依赖这份快照，不再依赖实体是否还活着。
     */
    private static volatile String loopSong = "";
    private static volatile boolean loopStreamer = true;
    private static volatile boolean loopEnabled = false;
    private static volatile int loopMaxRange = 64;
    private static volatile double loopX = 0.0;
    private static volatile double loopY = 0.0;
    private static volatile double loopZ = 0.0;

    /** 起播时记账。由 MixinJobBardClient 在实际调用 playStreaming/playMusic 后调用。 */
    public static void remember(JobBard bard, String song) {
        if (bard == null || song == null || song.isEmpty()) {
            forget();
            return;
        }
        EntityNPCInterface npc = bard.npc;
        loopSong = song;
        loopStreamer = bard.isStreamer;
        loopEnabled = bard.isLooping;
        loopMaxRange = bard.maxRange;
        if (npc != null) {
            loopX = npc.getX();
            loopY = npc.getY();
            loopZ = npc.getZ();
        }
    }

    /** 清账。音乐被正常停止（切歌、走出停止距离、诗人死亡）时调用。 */
    public static void forget() {
        loopSong = "";
        loopEnabled = false;
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

    /** 玩家是否还在快照记录的诗人位置的停止距离内。实体已卸载时也能判定。 */
    private static boolean playerInRememberedRange() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return false;
        double dx = Math.abs(player.getX() - loopX);
        double dy = Math.abs(player.getY() - loopY);
        double dz = Math.abs(player.getZ() - loopZ);
        return dx <= loopMaxRange && dz <= loopMaxRange && dy <= loopMaxRange / 2.0;
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
     * <p>判定完全基于起播时的快照，不依赖诗人实体是否还在客户端存活 ——
     * 这正是走太远 / 区块卸载后仍能续播的关键。
     *
     * <p>玩家仍在快照范围内时不由这里接管，让诗人的 aiStep 按歌单权重正常切歌，两者不抢。
     *
     * @return 需要重播的资源路径；不需要重播时返回 null
     */
    public static String shouldRestart(boolean active) {
        if (active) return null;
        if (!loopEnabled) return null;
        String song = loopSong;
        if (song.isEmpty()) return null;
        // 玩家还在范围内时交给 aiStep 走正常的歌单切歌逻辑，避免和它抢。
        if (playerInRememberedRange()) return null;
        return song;
    }

    /** 续播时该走流式（唱片机）入口还是背景音乐入口，取自起播快照。 */
    public static boolean isRestartStreamer() {
        return loopStreamer;
    }
}
