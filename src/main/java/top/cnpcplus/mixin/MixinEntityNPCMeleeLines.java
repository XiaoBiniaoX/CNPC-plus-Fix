package top.cnpcplus.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAdvanced;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.lines.MeleeLinesStorage;

/**
 * 近战打击台词的触发点：NPC 真正打中目标时播报一条台词。
 *
 * <p>注入 {@code EntityNPCInterface.m_7327_}（doHurtTarget）的 RETURN，
 * 只在返回值为 true 时说话 —— 返回值就是原版 {@code target.hurt(...)} 的结果，
 * 为 false 表示被格挡 / 无敌帧 / 免疫，那种情况不该播报「打中」台词。
 *
 * <p>为什么用 RETURN 而不是 HEAD 或事件钩子：
 * <ul>
 *   <li>HEAD 时还不知道有没有命中。</li>
 *   <li>{@code EventHooks.onNPCAttacksMelee} 在伤害计算之前，同样不知道结果，
 *       而且脚本可以取消攻击，在那里说话会出现「说了台词但没打中」。</li>
 * </ul>
 *
 * <p>只在服务端播报：{@code saySurrounding} 内部会给 20 格内玩家发聊天气泡包，
 * 客户端重复调用会导致气泡出现两次。原版六类台词全部都有
 * {@code !isClientSide()} 守卫，这里保持一致。
 *
 * <p>随机 / 顺序由原版的 {@code advanced.orderedLines} 开关统一控制，
 * 与其他六类台词行为完全一致（{@code getLine(!orderedLines)}）。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCMeleeLines {

    @Shadow(remap = false)
    public DataAdvanced advanced;

    @Inject(method = "m_7327_", at = @At("RETURN"), remap = false)
    private void cnpcplus$sayMeleeLine(Entity target, CallbackInfoReturnable<Boolean> cir) {
        // 只有真正造成伤害才播报；被格挡/无敌帧/免疫时保持沉默。
        if (!cir.getReturnValueZ()) return;
        if (this.advanced == null) return;

        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        if (self.isClientSide()) return;

        // peek 而非 get：绝大多数 NPC 不会配这类台词，不给它们创建空对象。
        Lines lines = MeleeLinesStorage.peek(this.advanced);
        if (lines == null || lines.isEmpty()) return;

        Line line = lines.getLine(!this.advanced.orderedLines);
        if (line == null) return;

        // 与原版 attack/kill/killed 台词一致地做 @target 占位符替换。
        // 注意原版 Line.formatTarget 自身有 bug（改了副本却返回原对象），
        // 所以 @target 实际不会被替换；这里照样调用是为了与其他台词行为一致，
        // 等原版修好后自动受益，我们不去改动依赖 jar 的行为。
        if (target instanceof LivingEntity living) {
            line = Line.formatTarget(line, living);
        }
        self.saySurrounding(line);
    }
}
