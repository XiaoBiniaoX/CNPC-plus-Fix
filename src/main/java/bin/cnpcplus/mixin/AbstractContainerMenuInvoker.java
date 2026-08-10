package bin.cnpcplus.mixin;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerMenu.class)
public interface AbstractContainerMenuInvoker {

    @Invoker("addSlot")
    Slot cnpcplus$addSlot(Slot slot);

    @Accessor("slots")
    NonNullList<Slot> cnpcplus$getSlots();

    @Accessor("lastSlots")
    NonNullList<ItemStack> cnpcplus$getLastSlots();

    @Accessor("remoteSlots")
    NonNullList<ItemStack> cnpcplus$getRemoteSlots();
}
