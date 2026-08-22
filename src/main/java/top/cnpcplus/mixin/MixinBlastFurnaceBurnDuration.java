package top.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.smelting.SmeltingFuelLookup;

/**
 * 高炉的自定义燃料燃烧时长。
 *
 * <p>烟熏炉/高炉 不应该因为其本身的加速熔炉性质，影响到对应配方 自定义燃料的特性
 * 「必定炼一个 1 换 1」。
 *
 * <p>原版 BlastFurnaceBlockEntity.getBurnDuration 是 {@code super.getBurnDuration(stack) / 2}
 * （javap 实证：invokespecial 父类方法后紧跟 iconst_2 / idiv）。父类上的注入返回值会被子类
 * 再整除 2，于是「一份燃料刚好烧完一个」被腰斩成半个，配方烧不完就熄火；若时长本身较小
 * （例如 1），除完变成 0，而原版把 0 当作「这不是燃料」，高炉就完全不点火。
 *
 * <p>所以必须在子类拦截：HEAD 直接 setReturnValue，绕过 super 与那个 /2，
 * 让自定义燃料拿到配方设定的完整时长。原版燃料不受影响（拿不到自定义时长时直接放行原逻辑，
 * 仍然享受高炉的半速燃料消耗手感）。
 */
@Mixin(BlastFurnaceBlockEntity.class)
public class MixinBlastFurnaceBurnDuration {

    // 方法名同时给 mojmap 与 SRG 两个候选：生产环境映射差异导致注入静默失效已踩过一次
    @Inject(method = {"getBurnDuration", "m_7743_"}, at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$fullBurnForCustomFuel(ItemStack fuel, CallbackInfoReturnable<Integer> cir) {
        if (fuel.isEmpty()) return;
        Integer custom = SmeltingFuelLookup.customBurnTime((AbstractFurnaceBlockEntity) (Object) this, fuel);
        // custom == null 表示炉内被熔炼物没匹配到任何自定义配方 → 交回原版
        // custom == 0 表示匹配到了配方但这个物品不是它认可的燃料 → 也要如实返回 0（不点火）
        if (custom != null) {
            cir.setReturnValue(custom);
        }
    }
}
