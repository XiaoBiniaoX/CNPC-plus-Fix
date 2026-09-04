package bin.cnpcplus.mixin.lines;

import bin.cnpcplus.lines.MeleeHitLinesAccess;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.controllers.data.Line;
import noppes.npcs.controllers.data.Lines;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 在 NPC 近战命中成功后播放附加的打击台词。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public class MixinEntityNPCInterfaceMeleeHitLine {

    @Inject(method = "doHurtTarget", at = @At("RETURN"), require = 1)
    private void cnpcplus$sayMeleeHitLine(Entity target, CallbackInfoReturnable<Boolean> cir) {
        EntityNPCInterface self = (EntityNPCInterface) (Object) this;
        // 只在服务端、目标为生物且实际造成伤害成功后发言。
        if (!cir.getReturnValue() || self.level().isClientSide || !(target instanceof LivingEntity living)) return;

        Lines lines = ((MeleeHitLinesAccess) self.advanced).cnpcplus$getMeleeHitLines();
        // 随机开关复用现有 orderedLines 配置。
        Line line = lines.getLine(!self.advanced.orderedLines);
        if (line != null) {
            // 成功命中后以受击目标格式化台词并向周围发送。
            self.saySurrounding(Line.formatTarget(line, living));
        }
    }
}
