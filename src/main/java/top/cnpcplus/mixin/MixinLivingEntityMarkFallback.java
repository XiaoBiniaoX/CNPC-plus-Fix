package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.controllers.data.MarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import top.cnpcplus.marks.MarkFallbackAccess;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntityMarkFallback implements MarkFallbackAccess {
    @Unique private MarkData cnpcplus$markFallback;
    @Override public MarkData cnpcplus$markFallback() { return cnpcplus$markFallback; }
    @Override public void cnpcplus$markFallback(MarkData data) { cnpcplus$markFallback = data; }
}
