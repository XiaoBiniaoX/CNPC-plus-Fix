package top.cnpcplus.mixin;

import java.util.HashSet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.CustomNpcs;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataDisplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * S7（Medium）：reset() 单播条件与隐形算法对齐。
 * 原版条件 isVisibleTo || spectator || 持魔杖，与 InvisibilityAlgorithm==1 的
 * tracking 集不是同一判据——在 tracking 里的玩家可能拿不到 reset 回包。
 * 修法：isVisibleTo 为 false 时，若算法1且玩家在 tracking 集内也放行。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCResetVisibility {

    @Shadow(remap = false)
    public HashSet<Integer> tracking;

    @Redirect(method = "reset", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/entity/data/DataDisplay;isVisibleTo(Lnet/minecraft/world/entity/player/Player;)Z"),
            remap = false)
    private boolean cnpcplus$expandResetVisibility(DataDisplay display, Player player) {
        if (display.isVisibleTo(player)) return true;
        if (CustomNpcs.EnableInvisibleNpcs && CustomNpcs.InvisibilityAlgorithm == 1
                && player instanceof ServerPlayer sp
                && this.tracking != null
                && this.tracking.contains(sp.getId())) {
            return true;
        }
        return false;
    }
}
