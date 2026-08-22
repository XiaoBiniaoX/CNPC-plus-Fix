package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 高炉燃料时长。
 *
 * <p>为什么必须单独混入子类：`BlastFurnaceBlockEntity` 自己 override 了 getBurnDuration：
 * <pre>return super.getBurnDuration(stack) / 2;</pre>
 * 只混入父类 `AbstractFurnaceBlockEntity` 时，父类注入的返回值会被这里整除 2，
 * 指定燃料按 cookTime 返回的小数值（例如 1）会被除成 0 —— 0 表示不是燃料，高炉直接不点火。
 * 这正是「通用燃料(煤炭)能烧、指定燃料不能烧，同配方在熔炉正常」的原因：
 * 通用燃料返回 1600，除 2 后仍有 800 所以看不出问题。
 *
 * <p>在子类 HEAD 拦截并直接给出最终值，绕过 super 与 /2，
 * 使指定燃料在高炉里的实际燃烧时长与配方设定一致。
 */
@Mixin(BlastFurnaceBlockEntity.class)
public abstract class MixinBlastFurnaceBurnDuration {

    @Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$blastBurnDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Integer custom = SmeltingFuelRules.customBurnTime(
                (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) (Object) this, stack, true);
        if (custom != null) cir.setReturnValue(custom);
    }
}
