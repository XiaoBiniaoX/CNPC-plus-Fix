package bin.cnpcplus.mixin.puppet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.containers.ContainerNPCBankInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(value = ContainerNPCBankInterface.class, remap = false)
public class MixinContainerNPCBankInterface {

    private static Method mergeMethod;

    @Inject(method = "func_82846_b", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$quickMoveBank(EntityPlayer player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        ContainerNPCBankInterface self = (ContainerNPCBankInterface) (Object) this;
        int bankSlots = self.getRowNumber() * 9;
        if (bankSlots <= 0) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        int bankStart = (!self.isAvailable() || self.canBeUpgraded()) ? 1 : 0;
        int bankEnd = bankStart + bankSlots;
        int playerStart = bankEnd;
        int playerEnd = self.inventorySlots.size();

        if (slotIndex < 0 || slotIndex >= playerEnd) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        Slot slot = self.getSlot(slotIndex);
        if (slot == null || !slot.getHasStack()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        boolean moved;
        if (slotIndex >= bankStart && slotIndex < bankEnd) {
            moved = cnpcplus$merge(self, stack, playerStart, playerEnd, true);
        } else if (slotIndex >= playerStart && slotIndex < playerEnd) {
            moved = cnpcplus$merge(self, stack, bankStart, bankEnd, false);
        } else {
            moved = false;
        }

        if (!moved) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (stack.isEmpty()) {
            slot.putStack(ItemStack.EMPTY);
        } else {
            slot.onSlotChanged();
        }
        cir.setReturnValue(original);
    }

    private static boolean cnpcplus$merge(ContainerNPCBankInterface self, ItemStack stack, int start, int end, boolean reverse) {
        try {
            if (mergeMethod == null) {
                mergeMethod = net.minecraft.inventory.Container.class.getDeclaredMethod(
                        "mergeItemStack", ItemStack.class, int.class, int.class, boolean.class);
                mergeMethod.setAccessible(true);
            }
            Object r = mergeMethod.invoke(self, stack, Integer.valueOf(start), Integer.valueOf(end), Boolean.valueOf(reverse));
            return r instanceof Boolean && ((Boolean) r).booleanValue();
        } catch (Throwable t) {
            try {
                Method m = net.minecraft.inventory.Container.class.getDeclaredMethod(
                        "func_75135_a", ItemStack.class, int.class, int.class, boolean.class);
                m.setAccessible(true);
                mergeMethod = m;
                Object r = m.invoke(self, stack, Integer.valueOf(start), Integer.valueOf(end), Boolean.valueOf(reverse));
                return r instanceof Boolean && ((Boolean) r).booleanValue();
            } catch (Throwable t2) {
                return false;
            }
        }
    }
}
