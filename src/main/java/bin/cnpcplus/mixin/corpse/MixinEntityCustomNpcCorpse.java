package bin.cnpcplus.mixin.corpse;

import bin.cnpcplus.corpse.CorpseHitbox;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import noppes.npcs.entity.EntityCustomNpc;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * EntityCustomNpc 覆写了 {@code getDimensions}，且在有 modelData 实体时根本不调 super
 * （EntityCustomNpc:113-144），所以只拦 EntityNPCInterface 会漏掉「用其他实体当模型」的 NPC。
 * 这里补上同样的处理。
 */
@Mixin(value = EntityCustomNpc.class, remap = false)
public class MixinEntityCustomNpcCorpse {

    @Inject(method = "getDimensions", at = @At("RETURN"), cancellable = true)
    private void cnpcplus$corpseNoHitbox(Pose pose, CallbackInfoReturnable<EntityDimensions> cir) {
        if (!CorpseHitbox.isRestingCorpse((EntityNPCInterface) (Object) this)) return;
        cir.setReturnValue(EntityDimensions.scalable(1.0E-5f, 1.0E-5f));
    }
}
