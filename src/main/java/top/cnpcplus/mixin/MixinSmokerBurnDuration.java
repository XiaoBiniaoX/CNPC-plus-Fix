package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.smelting.SmeltingFuelLookup;

/**
 * 烟熏炉的自定义燃料燃烧时长。与 MixinBlastFurnaceBurnDuration 同理，见那边的完整说明。
 *
 * <p>烟熏炉/高炉 不应该因为其本身的加速熔炉性质，影响到对应配方 自定义燃料的特性
 * 「必定炼一个 1 换 1」。原版 SmokerBlockEntity.getBurnDuration 也是
 * {@code super.getBurnDuration(stack) / 2}（javap 实证），父类注入会被它再腰斩。
 */
@Mixin(SmokerBlockEntity.class)
public class MixinSmokerBurnDuration {

    // 方法名同时给 mojmap 与 SRG 两个候选：生产环境映射差异导致注入静默失效已踩过一次
    @Inject(method = {"getBurnDuration", "m_7743_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$fullBurnForCustomFuel(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        if (fuel.isEmpty()) return;
        Integer custom = SmeltingFuelLookup.customBurnTime((AbstractFurnaceBlockEntity) (Object) this, fuel);
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
