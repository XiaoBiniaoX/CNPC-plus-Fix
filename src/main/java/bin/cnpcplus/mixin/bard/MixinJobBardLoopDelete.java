package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardLoopStore;
import noppes.npcs.client.controllers.MusicController;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 收住 {@code JobBard.delete()} 的停歌行为。
 *
 * 原版实现：
 * <pre>
 * public void delete() {
 *     if (!world.isRemote) return;
 *     if (!hasOffRange) return;
 *     if (!MusicController.Instance.isPlaying(this.song)) return;
 *     MusicController.Instance.stopMusic();
 * }
 * </pre>
 * 客户端实体卸载 / NPC 死亡时会走到这里（{@code killed()} 也直接调它）。
 *
 * <h3>取消条件一：开了循环</h3>
 * 玩家走远到诗人离开客户端加载范围时就会触发，表现为「开了循环、走远之后
 * 还是断歌」。此时直接取消。
 *
 * <h3>取消条件二：这首歌不是我在放（3.4.1 新增）</h3>
 * {@code isPlaying(String)} 只比较曲目，**不比较 playingEntity**（字节码实证）。
 * 所以甲、乙两个诗人配同一首歌时，甲被卸载会把**乙正在放的**音乐停掉 ——
 * 这正是玩家反馈「进入一个新的、但同一歌曲的吟游诗人范围内会直接断开」的
 * 第二条路径（第一条是播放权转移缺失，见 MixinJobBardClient）。
 * 判据用 {@code playingEntity != this.npc}：不是我在放就无权停。
 *
 * 该方法只做「停音乐」一件事，取消掉不会漏掉任何清理逻辑。
 */
@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardLoopDelete {

    @Inject(method = "delete", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$keepLooping(CallbackInfo ci) {
        JobBard self = (JobBard) (Object) this;
        if (BardLoopStore.isLooping(self)) {
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
