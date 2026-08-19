package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcProjectiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * B1: 追加 5 条粒子轨迹到弹射物设置界面（SubGuiNpcProjectiles）的 trailNames 数组。
 * 原数组 {"gui.none","Smoke","Portal","Redstone","Lightning","LargeSmoke","Magic","Enchant"} (0-7)，
 * 追加 8-13：Crit 暴击 / Flame 火焰 / SoulFlame 灵魂火 / EndRod 末地烛 / Snowflake 雪花 / Glow 萤火。
 */
@Mixin(value = SubGuiNpcProjectiles.class, remap = false)
public class MixinSubGuiNpcProjectilesTrails {

    @Shadow(remap = false)
    private String[] trailNames;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void cnpcplus$appendTrails(CallbackInfo ci) {
        String[] extended = new String[]{
                "gui.none", "Smoke", "Portal", "Redstone", "Lightning", "LargeSmoke", "Magic", "Enchant",
                "Crit", "Flame", "SoulFlame", "EndRod", "Snowflake", "Glow"
        };
        this.trailNames = extended;
    }
}
