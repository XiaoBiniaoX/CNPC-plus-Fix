package top.cnpcplus.mixin;

import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketScriptSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.linked.LinkedSyncFlags;

/**
 * 脚本魔杖保存后，把脚本同步到同标签的其他 NPC。
 *
 * <p>为什么需要：脚本魔杖走 {@code SPacketScriptSave.handle()}，NPC 类型（type==0）
 * 只执行 {@code npc.script.load(data)}，**不会调用 {@code LinkedNpcController.saveNpcData}**
 * （源码实证）。所以普通编辑界面那条同步链覆盖不到脚本魔杖，脚本内容与启用状态从未进入共享标签。
 *
 * <p>3.4.0 修法变更（重要）：旧实现是
 * <pre>
 * npc.script.save(npc.linkedData.data);
 * npc.linkedData.time = System.currentTimeMillis();
 * LinkedNpcController.Instance.save();
 * </pre>
 * 这样做绕过了原版 {@code saveNpcData} 的深比较，直接推进 {@code time} 并落盘。后果是
 * **所有同标签 NPC 下一 tick 都会被 {@code linkedData.data} 全量覆盖**，而 data 里除脚本
 * 之外的键仍是上一次 saveNpcData 的旧快照 —— 玩家先改属性（未进共享数据）、再用脚本魔杖
 * 保存，属性就被旧快照拉回去了。
 *
 * <p>现在改为直接调用原版 {@code saveNpcData(npc)}：
 * <ul>
 *   <li>脚本键由 {@code MixinLinkedNpcController} 在 {@code readNpcData} 返回值里补写，
 *       所以 saveNpcData 生成的快照天然包含最新脚本；</li>
 *   <li>沿用原版深比较，没有实际变化就不写盘、不推进 time；</li>
 *   <li>快照是「当前 NPC 的完整状态」，不会用旧数据覆盖别的键。</li>
 * </ul>
 * 脚本同步的语义完全保留，且与属性同步共用同一条比较/落盘链。
 */
@Mixin(value = SPacketScriptSave.class, remap = false)
public class MixinSPacketScriptSaveLinked {

    @Shadow
    private int type;

    @Inject(method = "handle", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveLinkedScripts(CallbackInfo ci) {
        // type != 0 表示保存的是方块/物品脚本，与 NPC 同步标签无关。
        if (type != 0) return;
        var npc = ((PacketServerBasic) (Object) this).npc;
        if (npc == null || npc.linkedData == null) return;
        if (!LinkedSyncFlags.isSyncScripts(npc.linkedData.name)) return;
        if (LinkedNpcController.Instance == null) return;
        LinkedNpcController.Instance.saveNpcData(npc);
    }
}
