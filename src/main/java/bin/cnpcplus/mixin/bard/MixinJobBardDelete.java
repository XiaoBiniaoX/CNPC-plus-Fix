package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardRangeGuard;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 收住 {@code JobBard.delete()} 的停歌行为（按 1.12.2 已验证实现移植）。
 *
 * <p>原版实现（{@code JobBard:97-102}）：
 * <pre>
 * public void delete() {
 *     if (!npc.level().isClientSide) return;
 *     if (!hasOffRange) return;
 *     if (!MusicController.Instance.isPlaying(this.song)) return;
 *     MusicController.Instance.stopMusic();
 * }
 * </pre>
 * 客户端实体卸载、NPC 死亡都会走到这里（{@code killed():93-95} 直接调它，
 * {@code EntityNPCInterface.delete():1430} 也调 {@code job.delete()}）。
 *
 * <h3>取消条件一：开了循环</h3>
 * 玩家走远到诗人离开客户端加载范围时就会触发，表现为「开了循环、走远之后还是断歌」。
 * 虽然 {@link BardRangeGuard} 的快照续播能在断掉之后重新播上，但那会听到明显的一次中断，
 * 从源头取消更干净。
 *
 * <h3>取消条件二：这首歌不是我在放</h3>
 * {@code MusicController.isPlaying(String)}（{@code MusicController:73-78}）
 * <b>只比较曲目，不比较 playingEntity</b>。所以甲、乙两个诗人配同一首歌时，
 * 甲被卸载会把<b>乙正在放的</b>音乐停掉 —— 这正是玩家反馈「进入一个新的、
 * 但同一歌曲的吟游诗人范围内会直接断开」的第二条路径
 * （第一条是播放权转移缺失，已在 MixinJobBardClient 修）。
 * 非循环模式下这条会完整复现：歌被停了，而 aiStep 的接管分支要求 {@code active} 为真，
 * 于是谁也不播。判据用 {@code playingEntity != this.npc}：不是我在放就无权停。
 *
 * <p>该方法只做「停音乐」一件事，取消掉不会漏掉任何清理逻辑。
 */
@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardDelete {

    @Inject(method = "delete", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$keepPlaying(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (self.isLooping) {
            ci.cancel();
            return;
        }
        // 不是我在放的歌，就不许我停它。
        MusicController c = MusicController.Instance;
        if (c != null && c.playing != null && c.playingEntity != null
                && c.playingEntity != self.npc) {
            ci.cancel();
        }
    }
}
