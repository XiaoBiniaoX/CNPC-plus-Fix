package bin.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcProjectiles;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SubGuiNpcProjectiles.class, remap = false)
public class SubGuiNpcProjectilesTrailsMixin {
    @Shadow(remap = false)
    private String[] trailNames;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$appendTrails(CallbackInfo ci) {
        this.trailNames = new String[]{
                "gui.none", "Smoke", "Portal", "Redstone", "Lightning", "LargeSmoke", "Magic", "Enchant",
                "Ember Crown", "Soul Helix", "Prism Ring", "Frost Plume", "Star Pulse"
        };
    }
}
