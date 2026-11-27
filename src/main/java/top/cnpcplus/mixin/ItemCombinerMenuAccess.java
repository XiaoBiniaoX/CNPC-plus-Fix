package top.cnpcplus.mixin;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ItemCombinerMenu.class, remap = false)
public interface ItemCombinerMenuAccess {
    @Accessor("access") ContainerLevelAccess cnpcplus$access();
}
