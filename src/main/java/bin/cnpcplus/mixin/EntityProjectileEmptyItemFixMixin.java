package bin.cnpcplus.mixin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import noppes.npcs.entity.EntityProjectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 射击过程中换成空子弹时，EntityProjectile.setThrownItem 若接收空栈会导致
 * ItemParticleOption 崩溃（不允许 Empty ItemStack）。此 mixin 拦截空栈写入，
 * 并在 onHit 粒子调用时提供占位栈以保证合法性。
 */
@Mixin(value = EntityProjectile.class, remap = false)
public class EntityProjectileEmptyItemFixMixin {

    /**
     * 拦截 setThrownItem，若 item 为 null 或 empty 则取消，保留已有的有效子弹。
     */
    @Inject(
            method = "setThrownItem",
            at = @At("HEAD"),
            cancellable = true,
            require = 1
    )
    private void cnpcplus$preventEmptyStack(ItemStack item, CallbackInfo ci) {
        // 空栈或null时取消设置，避免 ItemParticleOption 崩溃
        if (item == null || item.isEmpty()) {
            ci.cancel();
        }
    }

    /**
     * 旧存档或其它路径子弹仍可能为空，在 onHit 内两处 getItemDisplay 调用时兜底。
     * 返回占位栈 SNOWBALL 保证粒子合法，不改实体同步数据。
     */
    @Redirect(
            method = "onHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnoppes/npcs/entity/EntityProjectile;getItemDisplay()Lnet/minecraft/world/item/ItemStack;"
            ),
            require = 1
    )
    private ItemStack cnpcplus$fallbackEmptyStack(EntityProjectile self) {
        ItemStack stack = self.getItemDisplay();
        // 若为空返回占位栈雪球，仅用于粒子构造不影响存档
        return (stack == null || stack.isEmpty()) ? new ItemStack(Items.SNOWBALL) : stack;
    }
}
