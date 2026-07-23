package bin.cnpcplus.mixin.puppet;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = AbstractContainerMenu.class, remap = false)
public interface ContainerMenuAccessor {
    @Invoker("moveItemStackTo")
    boolean cnpcplus$moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverseDirection);
}
