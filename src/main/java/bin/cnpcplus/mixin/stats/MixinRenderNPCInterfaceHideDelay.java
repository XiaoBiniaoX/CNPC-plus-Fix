package bin.cnpcplus.mixin.stats;

import bin.cnpcplus.common.IDataStatsHideDelayAccess;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 238-2：把 RenderNPCInterface.doRender 中写死的 20 tick 尸体隐藏延迟
 * 替换为 DataStats 上的可配置值。只改渲染门那一次 deathTime 读取。
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public class MixinRenderNPCInterfaceHideDelay {

    @Redirect(
            method = "doRender(Lnoppes/npcs/entity/EntityNPCInterface;DDDFF)V",
            at = @At(value = "FIELD",
                    target = "Lnoppes/npcs/entity/EntityNPCInterface;field_70725_aQ:I",
                    ordinal = 0),
            remap = false,
            require = 1
    )
    private int cnpcplus$useConfigurableHideDelay(EntityNPCInterface npc) {
        if (npc == null || npc.stats == null) {
            return npc == null ? 0 : npc.deathTime;
        }
        int delay = ((IDataStatsHideDelayAccess) npc.stats).cnpcplus$getHideDelay();
        return npc.deathTime < delay ? 0 : 21;
    }
}
