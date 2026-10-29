package bin.cnpcplus.mixin.anvil;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 阶段231b：放宽 vanilla AnvilMenu.mayPickup 对自定义配方的经验门槛。
 *
 * <p>javap 实证的原版判定（AnvilMenu.mayPickup）：
 * {@code (instabuild || experienceLevel >= cost) && cost > 0}
 * —— {@code cost>0} 使 0（免费）与负数（自定义配方「负数=给锻造者经验」）结果取不走，
 * 而 onTake 原生 {@code giveExperienceLevels(-cost)} 对负 cost 天然=给经验（ineg 实证），
 * 唯独这道门拦路。本注入复刻原判定并去掉 {@code cost>0}：
 * cost<=0 时 {@code level>=cost} 恒真（等级恒>=0>=负数）→ 可取走；正数仍按等级检查，语义不变。
 *
 * <p>对原版行为零影响：原版 cost=0 时结果槽必为空（createResult 空操作即置空/结果空无从取），
 * 放宽条件对空结果无实际作用；原版 cost 恒>=1 路径判定结果与原版完全一致。
 *
 * <p>双端同跑（客户端预览渲染与服务端权威同源）；getCost() 为 AnvilMenu public 方法。
 * method 官方名+默认 remap → 由项目已配的 mixin refmap（cnpcplus.refmap.json）自动 SRG 映射。
 */
@Mixin(value = AnvilMenu.class, remap = false)
public class MixinAnvilMenu {

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void cnpcplus$allowZeroNegativeCost(Player player, boolean canTake,
                                                CallbackInfoReturnable<Boolean> cir) {
        AnvilMenu self = (AnvilMenu) (Object) this;
        cir.setReturnValue(player.getAbilities().instabuild
                || player.experienceLevel >= self.getCost());
    }
}
