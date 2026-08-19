package top.cnpcplus.mixin;

import noppes.npcs.client.gui.SubGuiNpcMeleeProperties;
import noppes.npcs.entity.data.DataMelee;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = SubGuiNpcMeleeProperties.class, remap = false)
public interface SubGuiNpcMeleePropertiesAccess {
    @Accessor("stats")
    DataMelee cnpcplus$getStats();
}
