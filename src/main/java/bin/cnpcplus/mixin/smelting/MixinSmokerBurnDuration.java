package bin.cnpcplus.mixin.smelting;

import bin.cnpcplus.smelting.SmeltingFuelRules;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 烟熏炉燃料时长。原因与 {@link MixinBlastFurnaceBurnDuration} 完全相同：
 * `SmokerBlockEntity` 同样 override 了 getBurnDuration 并对结果整除 2，
 * 会把指定燃料的小 cookTime 除成 0 导致不点火。
 */
@Mixin(SmokerBlockEntity.class)
public abstract class MixinSmokerBurnDuration {

    @Inject(method = "getBurnDuration", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$smokerBurnDuration(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        Integer custom = SmeltingFuelRules.customBurnTime(
                (net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) (Object) this, stack, true);
        if (custom != null) cir.setReturnValue(custom);
    }
}
