package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.smelting.SmeltingFuelLookup;

/**
 * B3: 原版熔炉/高炉/烟熏炉接入自定义熔炼配方的燃料判定。
 * 1) getBurnDuration RETURN：炉内被熔炼物匹配到自定义配方时，燃烧时长改由该配方的燃料匹配器决定
 *    （通用燃料开→按 Forge 燃料判定；关→只有指定燃料给时长且时长=该配方熔炼时间，其余返回 0 不点火）。
 *    不匹配任何自定义配方则保持原版值。
 * 2) canPlaceItem RETURN：燃料槽放行任何物品（漏斗/自动化入口；玩家手动放置见 MixinFurnaceFuelSlotPlace）。
 * 用 @Inject 而非 @Overwrite，保留原版逻辑与其它模组的兼容性。
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public class MixinAbstractFurnaceBlockEntityFuel {

    // 方法名同时给 mojmap 与 SRG 两个候选：生产环境映射差异导致注入静默失效已踩过一次
    @Inject(method = {"getBurnDuration", "m_7743_"}, at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$customBurnDuration(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        if (fuel.isEmpty()) return;
        Integer custom = SmeltingFuelLookup.customBurnTime((AbstractFurnaceBlockEntity) (Object) this, fuel);
        if (custom != null && custom.intValue() != cir.getReturnValueI()) {
            cir.setReturnValue(custom);
        }
    }

    @Inject(method = {"canPlaceItem", "m_7013_"}, at = @At("RETURN"), cancellable = true, require = 1)
    private void cnpcplus$allowAnyFuel(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        // 燃料槽放行任何物品（与 MixinFurnaceFuelSlotPlace 规则一致，这条覆盖漏斗/自动化入口）
        if (slot == 1 && !cir.getReturnValueZ()) {
            cir.setReturnValue(true);
        }
    }
}
