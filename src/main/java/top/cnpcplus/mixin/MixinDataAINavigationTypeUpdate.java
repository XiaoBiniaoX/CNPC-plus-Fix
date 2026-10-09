package top.cnpcplus.mixin;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 修复：脚本 ai.setNavigationType() 切换移动方式后 NPC 空中罚站。
 *
 * <p>CNPC 的 {@code DataAI.setNavigationType(int)}（DataAI.java:531-533）只写
 * {@code movementType} 字段，不置 {@code npc.updateAI = true}；而兄弟 setter
 * （setCanLeap/setTacticalType 等）全都会置位，SPacketMenuSave 也会补置。
 * {@code updateAI} 不置位导致 {@code updateTasks()} 不重配 navigator/moveControl：
 * 字段直读让 {@code canFly()} 立即生效、{@code travel()} 走飞行分支不掉落，
 * 但导航仍是地面那一套 —— 地面切飞行后 NPC 悬空罚站。这里在 setter 尾部补齐标志。
 */
@Mixin(value = DataAI.class, remap = false)
public class MixinDataAINavigationTypeUpdate {

    /** DataAI 自有私有字段（DataAI.java:21），非继承成员，@Shadow 可用。 */
    @Shadow(remap = false)
    private EntityNPCInterface npc;

    @Inject(method = "setNavigationType", at = @At("TAIL"))
    private void cnpcplus$updateAiFlag(int type, CallbackInfo ci) {
        if (this.npc != null) {
            this.npc.updateAI = true;
        }
    }
}
