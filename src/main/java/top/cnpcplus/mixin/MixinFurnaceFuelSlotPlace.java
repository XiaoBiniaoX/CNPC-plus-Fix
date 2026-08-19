package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.FurnaceFuelSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B3: 打破原版限制 —— 熔炉/高炉/烟熏炉的燃料槽允许放入任何物品。
 * 原版 FurnaceFuelSlot.mayPlace = menu.isFuel(stack) || isBucket(stack)，
 * 盔甲/武器/铁轨等非燃料物品放不进去；而自定义熔炼配方的「指定燃料」恰恰常是这类物品，
 * 玩家也需要能自由试放来测试配方。放行只影响"能不能放进槽位"，
 * 能不能点火依然由 getBurnDuration 决定（非燃料返回 0 就是不烧）。
 */
@Mixin(FurnaceFuelSlot.class)
public class MixinFurnaceFuelSlotPlace {

    // 方法名同时给 mojmap 与 SRG 两个候选，避免生产环境映射差异导致静默失效
    @Inject(method = {"mayPlace", "m_5857_"}, at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$allowAnyFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            cir.setReturnValue(true);
        }
    }
}
