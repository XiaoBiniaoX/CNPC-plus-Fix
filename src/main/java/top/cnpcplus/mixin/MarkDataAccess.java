package top.cnpcplus.mixin;

import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.controllers.data.MarkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = MarkData.class, remap = false)
public interface MarkDataAccess {
    @Accessor("entity") void cnpcplus$entity(LivingEntity entity);
}
