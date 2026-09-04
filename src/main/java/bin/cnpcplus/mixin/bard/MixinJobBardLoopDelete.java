package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.BardLoopStore;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 循环播放时，不让 {@code JobBard.delete()} 把音乐掐掉。
 *
 * 这是第三个断歌源，此前完全没被接管：
 * <pre>
 * public void delete() {
 *     if (!world.isRemote) return;
 *     if (!hasOffRange) return;
 *     if (!MusicController.Instance.isPlaying(this.song)) return;
 *     MusicController.Instance.stopMusic();
 * }
 * </pre>
 * 客户端实体卸载 / NPC 死亡时会走到这里（{@code killed()} 也直接调它）。
 * 玩家走远到诗人离开客户端加载范围时就会触发，表现为「开了循环、走远之后
 * 还是断歌」的偶发现象。
 *
 * 开启循环时直接取消该方法。注意它只做「停音乐」一件事，
 * 取消掉不会漏掉任何清理逻辑。
 */
@Mixin(value = JobBard.class, remap = false)
public class MixinJobBardLoopDelete {

    @Inject(method = "delete", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$keepLooping(CallbackInfo ci) {
        if (BardLoopStore.isLooping((JobBard) (Object) this)) {
            ci.cancel();
        }
    }
}
