package bin.cnpcplus.bard;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;

import java.lang.ref.WeakReference;

/**
 * 循环播放的「兜底续播器」。
 *
 * <h3>为什么必须有它</h3>
 * 循环续播原本写在 {@link bin.cnpcplus.mixin.bard.MixinJobBardClient} 的
 * {@code onLivingUpdate} 里，而那是**吟游诗人实体自己的 tick**。玩家反馈
 * 「疑似只要离得足够远，或者吟游诗人被区块卸载等因素后，bgm 就会断开不再重播」
 * 正是这个结构问题：
 * <ul>
 *   <li>客户端只 tick 加载范围内的实体。玩家走远到诗人被客户端卸载后，
 *       {@code onLivingUpdate} 根本不再被调用，续播分支永远等不到执行。</li>
 *   <li>{@link bin.cnpcplus.mixin.bard.MixinJobBardLoopDelete} 只能挡住
 *       {@code JobBard.delete()} 的**主动停歌**，挡不住「歌自然放完之后没人重播」。
 *       所以表现就是：走远那一刻还在响，这一首放完就彻底安静。</li>
 * </ul>
 * 结论：续播的驱动源不能依赖诗人实体是否还在 tick，必须挂在客户端全局 tick 上。
 *
 * <h3>与「范围内歌单切歌」如何不打架（硬约束）</h3>
 * 哈基彬明确要求循环续播「不能让激活距离内的歌单播放异常」，阶段 26 已经因为
 * 「续播分支排在 minRange 判断之前」把范围内切歌搞坏过一次。所以这里定的分工是：
 * <ul>
 *   <li><b>诗人还在 tick</b>（心跳在 1 秒内）→ 本类完全沉默，一切交给
 *       {@code MixinJobBardClient}，它已经正确区分了「范围内切歌 / 范围外续播」。</li>
 *   <li><b>诗人不再 tick</b>（卸载、移除、走出客户端加载范围）→ 才由本类接手重播。</li>
 * </ul>
 * 这样两者在时间上互斥，不存在「keeper 重播旧曲、诗人想切新曲」的竞争。
 *
 * <h3>让位规则</h3>
 * 若 MusicController 正在放**别的曲子**（另一个诗人已经接管），本类立刻清空状态退出，
 * 绝不与新的播放源抢占。
 *
 * <h3>已知限制（与原版一致，不额外补偿）</h3>
 * {@code isStreamer = true} 时原版用 RECORDS + LINEAR 衰减、坐标取诗人当时的位置，
 * 因此走远后本来就会被距离衰减到听不见 —— 这是原版音频模型决定的，不是本类的缺陷。
 * 「走远仍要听得见」实际只对 {@code isStreamer = false}（MUSIC + 无衰减）成立。
 * 若诗人实体已被回收，重播时退而使用玩家实体占位，仅用于满足 API 的 Entity 参数。
 */
@Mod.EventBusSubscriber(value = Side.CLIENT)
public final class BardLoopKeeper {

    /** 两次重播之间的最小间隔，与 MixinJobBardClient 的 500ms 防抖同源。 */
    private static final long REPLAY_COOLDOWN_MS = 500L;

    /** 超过这个时间没收到诗人心跳，就认为它不再 tick，由本类接手。 */
    private static final long HEARTBEAT_TIMEOUT_MS = 1000L;

    private static final Object LOCK = new Object();

    private static WeakReference<JobBard> job;
    private static WeakReference<Entity> npc;
    private static String song = "";
    private static boolean streamer;
    /** 记录时的循环开关快照。诗人被回收后 BardLoopStore 查不到了，只能靠它。 */
    private static boolean looping;
    private static long heartbeat;
    private static long lastReplay;

    private BardLoopKeeper() {
    }

    /**
     * 诗人每 tick 报一次到。只要还在报到，本类就不插手。
     */
    public static void heartbeat(JobBard self) {
        synchronized (LOCK) {
            if (job != null && job.get() == self) {
                heartbeat = System.currentTimeMillis();
                // 循环开关可能被 GUI 改过，顺手刷新快照。
                looping = BardLoopStore.isLooping(self);
            }
        }
    }

    /**
     * 记录「最后一次播放了什么」。由 {@code MixinJobBardClient.cnpcplus$play} 调用。
     */
    public static void record(JobBard self, String music) {
        if (self == null || music == null || music.isEmpty()) return;
        synchronized (LOCK) {
            job = new WeakReference<JobBard>(self);
            npc = new WeakReference<Entity>(self.npc);
            song = music;
            streamer = self.isStreamer;
            looping = BardLoopStore.isLooping(self);
            heartbeat = System.currentTimeMillis();
            lastReplay = System.currentTimeMillis();
        }
    }

    /** 玩家主动停歌 / 换人 / 关循环时清空，避免本类继续重播。 */
    public static void clear() {
        synchronized (LOCK) {
            job = null;
            npc = null;
            song = "";
            looping = false;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null) {
            clear();
            return;
        }
        MusicController c = MusicController.Instance;
        if (c == null) return;

        String music;
        boolean isStreamer;
        Entity anchor;
        synchronized (LOCK) {
            if (song.isEmpty()) return;

            JobBard bard = job == null ? null : job.get();
            // 诗人还活着就以它的实时开关为准；已被回收则只能用快照。
            boolean loopOn = bard != null ? BardLoopStore.isLooping(bard) : looping;
            if (!loopOn) {
                clear();
                return;
            }
            // 诗人还在 tick → 交给 MixinJobBardClient，本类不插手（见类注释的分工）。
            if (System.currentTimeMillis() - heartbeat < HEARTBEAT_TIMEOUT_MS) return;
            if (System.currentTimeMillis() - lastReplay < REPLAY_COOLDOWN_MS) return;

            // 已经有别的曲子在放 → 别人接管了，让位。
            if (c.playingResource != null
                    && !c.playingResource.toString().equals(song)
                    && c.playing != null
                    && mc.getSoundHandler().isSoundPlaying(c.playing)) {
                clear();
                return;
            }
            // 我的歌还在响，不用管。
            if (c.isPlaying(song)) return;

            // BardVolume 为 0 时 SoundManager.setVolume 会直接 stopSound，
            // 此时重播会变成每 500ms 疯狂重启，直接跳过。
            if (CnpcPlusConfig.getBardVolume() <= 0.0F) return;

            music = song;
            isStreamer = streamer;
            Entity kept = npc == null ? null : npc.get();
            anchor = kept != null ? kept : mc.player;
            lastReplay = System.currentTimeMillis();
        }

        // 必须经由 MusicController：MixinSoundManager 靠
        // sound == MusicController.Instance.playing 的引用相等来识别吟游诗人的声音，
        // 自建 ISound 会让独立音量滑块失效（阶段 24 的红线）。
        if (isStreamer) {
            c.playStreaming(music, anchor);
        } else {
            c.playMusic(music, anchor);
        }
    }
}
