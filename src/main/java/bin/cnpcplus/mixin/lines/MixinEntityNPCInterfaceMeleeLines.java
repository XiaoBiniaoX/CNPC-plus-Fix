package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.MeleeLineStore;
import net.minecraft.entity.Entity;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 近战打击台词的播放触发点。
 *
 * 需求：NPC 近战攻击**打中**对方时播放设置好的台词，并且随机。
 *
 * 注入点选择（证据）：
 * {@code EntityNPCInterface.func_70652_k(Entity)}（attackEntityAsMob）
 * 内 {@code var4 = par1Entity.attackEntityFrom(...)} 直接取伤害结果，
 * 两个 return 点（效果类型为 0 的提前 return 与末尾 return）都返回 var4。
 * 因此在 RETURN 处用 {@code cir.getReturnValue()} 判 true 即「命中成功」，
 * 且脚本事件取消的分支返回 false，不会误播。
 *
 * 随机性直接复用原生 {@code Lines.getLine(boolean isRandom)}，
 * 并沿用原版语义 {@code !advanced.orderedLines}（默认随机，勾了「顺序」才轮转），
 * 与其余 6 类台词行为一致。
 *
 * 冷却：攻击间隔最低可到个位数 tick，不加限制会刷屏。这里按 tick 做一个
 * 最小间隔，取值与原版世界台词的节流量级相当。
 *
 * 服务端安全：AI 只在服务端 tick，仍显式判 {@code !isRemote()} 双重保险；
 * 播放走原生 {@code saySurrounding}（内部会触发 ServerChatEvent 并只对
 * 20 格内玩家发聊天气泡包），不新增任何网络入口。
 *
 * 不使用 {@code Line.formatTarget}：该方法在 1.12.2 有原生 bug
 * （返回未替换的原对象），依赖它等于依赖一个不生效的功能。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceMeleeLines {

    /** 两次近战台词之间的最小间隔（tick）。20 tick = 1 秒。 */
    @Unique
    private static final int CNPCPLUS_MELEE_LINE_COOLDOWN = 20;

    @Unique
    private int cnpcplus$lastMeleeLineTick = -CNPCPLUS_MELEE_LINE_COOLDOWN;

    @Inject(method = "func_70652_k", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$sayMeleeLine(Entity target, CallbackInfoReturnable<Boolean> cir) {
        // 未命中（含脚本取消）不播。
        if (!cir.getReturnValueZ()) {
            return;
        }
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;
        if (npc.isRemote() || npc.advanced == null) {
            return;
        }
        int now = npc.ticksExisted;
        if (now - this.cnpcplus$lastMeleeLineTick < CNPCPLUS_MELEE_LINE_COOLDOWN) {
            return;
        }
        Lines lines = MeleeLineStore.peek(npc.advanced);
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Line line = lines.getLine(!npc.advanced.orderedLines);
        if (line == null) {
            return;
        }
        this.cnpcplus$lastMeleeLineTick = now;
        npc.saySurrounding(line);
    }
}
