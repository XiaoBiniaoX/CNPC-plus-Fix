package top.cnpcplus.mixin;

import noppes.npcs.controllers.LinkedNpcController;
import noppes.npcs.packets.PacketServerBasic;
import noppes.npcs.packets.server.SPacketMenuSave;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让「保存 NPC 编辑」也把改动写进同步标签。这是「全局同步设置改了不生效」的另一半根因。
 *
 * <p>原版只有 {@code SPacketMenuClose.handle} 会调 {@code saveNpcData}
 * （源码实证），而这个包**只由顶部菜单的「关闭」按钮发出**。
 * 玩家实际常用的两种收尾方式都不发它：
 * <ul>
 *   <li>按 Esc：走 {@code GuiWrapper.close()}，字节码实证只调 {@code save()}
 *       （发 {@code SPacketMenuSave}），没有 MenuClose；</li>
 *   <li>切换顶部标签页：各页 {@code save()} 同样只发 MenuSave。</li>
 * </ul>
 * 而 {@code SPacketMenuSave.handle} 只更新 NPC 自身（stats/ais/advanced 等），
 * **完全不碰 {@code linkedData}** —— 于是玩家改了攻击力 5→6、按 Esc 退出，
 * 改动只进了这一个 NPC，共享标签里还是 5。下次 {@code loadNpcData} 触发时
 * （或重启后第一 tick）就被旧的 5 覆盖回去，表现为「改了没用、重进还原」。
 *
 * <p>修法：在 {@code handle} 的 RETURN 补一次 {@code saveNpcData}。
 *
 * <p>刻意复用原版 {@code saveNpcData} 而不自己写落盘逻辑，这样：
 * <ul>
 *   <li>沿用原版的深比较（{@code linkedData.data.equals(compound)}），没有实际变化时
 *       不写盘、不推进 {@code time}，避免每次保存都让全标签 NPC 重载一遍；</li>
 *   <li>{@code time} 的推进与落盘时机与原版完全一致，不会出现我们手改 time
 *       导致其他 NPC 被旧快照覆盖的问题（那正是旧 MixinSPacketScriptSaveLinked 的毛病）。</li>
 * </ul>
 *
 * <p>与脚本同步的关系：{@code MixinLinkedNpcController} 已在 {@code readNpcData}
 * 的返回值里按开关补写脚本键，所以这条链路天然把脚本一起带上，两者共用同一次比较与落盘，
 * 互不覆盖。
 *
 * <p>权限与安全：注入点在原版 {@code handle} 内，而 {@code PacketServerBasic} 的分发链
 * 已经做过权限校验（{@code getPermission()} → NPC_STATS/NPC_AI/...）与 {@code requiresNpc}
 * 校验，这里不新增任何信任面。仅服务端执行。
 */
@Mixin(value = SPacketMenuSave.class, remap = false)
public class MixinSPacketMenuSaveLinked {

    @Inject(method = "handle", at = @At("RETURN"), remap = false)
    private void cnpcplus$saveLinkedOnMenuSave(CallbackInfo ci) {
        var npc = ((PacketServerBasic) (Object) this).npc;
        if (npc == null || npc.linkedData == null) return;
        if (LinkedNpcController.Instance == null) return;
        LinkedNpcController.Instance.saveNpcData(npc);
    }
}
