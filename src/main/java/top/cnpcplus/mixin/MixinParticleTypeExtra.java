package top.cnpcplus.mixin;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import noppes.npcs.api.constants.ParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * B1: 追加 5 条粒子轨迹（id 9-13）。
 * 9=FLAME 火焰  10=SOUL_FIRE_FLAME 灵魂火  11=END_ROD 末地烛  12=SNOWFLAKE 雪花  13=GLOW 萤火
 */
@Mixin(value = ParticleType.class, remap = false)
public class MixinParticleTypeExtra {

    @Inject(method = "getMCType", at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcplus$extraTrails(int type, CallbackInfoReturnable<ParticleOptions> cir) {
        switch (type) {
            case 9 -> cir.setReturnValue(ParticleTypes.FLAME);
            case 10 -> cir.setReturnValue(ParticleTypes.SOUL_FIRE_FLAME);
            case 11 -> cir.setReturnValue(ParticleTypes.END_ROD);
            case 12 -> cir.setReturnValue(ParticleTypes.SNOWFLAKE);
            case 13 -> cir.setReturnValue(ParticleTypes.GLOW);
            default -> { }
        }
    }
}
