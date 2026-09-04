package top.cnpcplus.mixin;

import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.linked.LinkedSyncFlags;

/**
 * 同步标签的脚本同步：加载时套用共享脚本，保存时把脚本纳入共享快照。
 *
 * <p>{@code readNpcData} 是原版生成「当前 NPC 完整快照」的地方，{@code saveNpcData}
 * 会拿它的返回值与共享数据做深比较、不同才落盘。把脚本键补在这个返回值里，
 * 脚本就自动参与原版的比较与保存，不需要我们自己推进 {@code time} 或调 {@code save()}。
 *
 * <p>{@code loadNpcData} 的 RETURN 则负责把共享脚本套回 NPC，并重置
 * {@code lastInited} / {@code updateAI}，否则脚本引擎不会重新初始化，新脚本不生效。
 */
@Mixin(value = LinkedNpcController.class, remap = false)
public class MixinLinkedNpcController {

    @Inject(method = "loadNpcData", at = @At("RETURN"), remap = false)
    private void cnpcplus$loadScripts(EntityNPCInterface npc, CallbackInfo ci) {
        if (npc == null || npc.linkedData == null) return;
        if (!LinkedSyncFlags.isSyncScripts(npc.linkedData.name)) return;
        if (npc.linkedData.data.contains("Scripts", 9)) {
            npc.script.load(npc.linkedData.data);
            // 不重置这两个，脚本引擎不会重新 init，玩家会以为脚本没同步过来。
            npc.script.lastInited = -1L;
            npc.updateAI = true;
        }
    }

    @Inject(method = "readNpcData", at = @At("RETURN"), remap = false)
    private void cnpcplus$includeScripts(EntityNPCInterface npc,
                                         CallbackInfoReturnable<net.minecraft.nbt.CompoundTag> cir) {
        if (npc == null || npc.linkedData == null) return;
        if (!LinkedSyncFlags.isSyncScripts(npc.linkedData.name)) return;
        npc.script.save(cir.getReturnValue());
    }
}
