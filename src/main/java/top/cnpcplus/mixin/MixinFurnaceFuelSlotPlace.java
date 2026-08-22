package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.FurnaceFuelSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import top.cnpcplus.smelting.SmeltingFuelLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B3: 放宽原版限制 —— 熔炉/高炉/烟熏炉的燃料槽允许放入自定义配方指定的燃料。
 * 原版 FurnaceFuelSlot.mayPlace = menu.isFuel(stack) || isBucket(stack)，
 * 盔甲/武器/铁轨等非燃料物品放不进去；而自定义熔炼配方的「指定燃料」恰恰常是这类物品。
 *
 * <p>注意这里只放行「确实被某条自定义配方指定为燃料」的物品，不是放行任何物品。
 * 早先无条件放行打破了原版「能放进燃料槽的必然是燃料」的前提：原版
 * AbstractFurnaceMenu.quickMoveStack 的燃料槽分支依赖 isFuel 决定物品去向，
 * 遇到「能放但不是燃料」的物品会把它搬走却不被承认，表现为 shift+左键吞物品（用户实测）。
 */
@Mixin(FurnaceFuelSlot.class)
public class MixinFurnaceFuelSlotPlace {

    // 方法名同时给 mojmap 与 SRG 两个候选，避免生产环境映射差异导致静默失效
    @Inject(method = {"mayPlace", "m_5857_"}, at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$allowAnyFuel(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) return;
        // Slot.container 就是炉子的 BlockEntity，借它拿 Level 做配方查询（服务端权威）。
        // 日志实证 isFurnace 有 60 次为 false（玩家背包槽也会走这里），所以必须判类型。
        Slot self = (Slot) (Object) this;
        if (self.container instanceof AbstractFurnaceBlockEntity furnace
                && SmeltingFuelLookup.isCustomFuel(furnace.getLevel(), stack)) {
            cir.setReturnValue(true);
        }
    }
}
