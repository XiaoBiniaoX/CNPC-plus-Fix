package bin.cnpcplus.mixin.puppet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import noppes.npcs.containers.ContainerNPCInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

@Mixin(value = ContainerNPCInv.class, remap = false)
public class MixinContainerNPCInv {

    private static Method mergeMethod;

    @Inject(method = "func_82846_b", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onTransfer(EntityPlayer player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        ContainerNPCInv self = (ContainerNPCInv) (Object) this;

        // armor slots 0-3 -> player inv 16-52 reverse
        if (slotIndex >= 0 && slotIndex <= 3) {
            Slot slot = self.getSlot(slotIndex);
            if (slot == null || !slot.getHasStack()) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            ItemStack stack = slot.getStack();
            ItemStack original = stack.copy();
            if (!cnpcplus$merge(self, stack, 16, 52, true)) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            if (stack.isEmpty()) {
                slot.putStack(ItemStack.EMPTY);
            } else {
                slot.onSlotChanged();
            }
            cir.setReturnValue(original);
            return;
        }

        // player inv 16-51 -> matching armor if empty
        if (slotIndex >= 16 && slotIndex < 52) {
            Slot slot = self.getSlot(slotIndex);
            if (slot == null || !slot.getHasStack()) {
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            ItemStack stack = slot.getStack();
            EntityEquipmentSlot equipSlot = cnpcplus$getEquipmentSlot(stack);
            if (equipSlot != null) {
                int armorSlot = 3 - equipSlot.getIndex();
                if (armorSlot >= 0 && armorSlot <= 3) {
                    Slot targetSlot = self.getSlot(armorSlot);
                    if (targetSlot != null && !targetSlot.getHasStack() && targetSlot.isItemValid(stack)) {
                        ItemStack original = stack.copy();
                        if (!cnpcplus$merge(self, stack, armorSlot, armorSlot + 1, false)) {
                            cir.setReturnValue(ItemStack.EMPTY);
                            return;
                        }
                        if (stack.isEmpty()) {
                            slot.putStack(ItemStack.EMPTY);
                        } else {
                            slot.onSlotChanged();
                        }
                        cir.setReturnValue(original);
                        return;
                    }
                }
            }
        }

        cir.setReturnValue(ItemStack.EMPTY);
    }

    private static EntityEquipmentSlot cnpcplus$getEquipmentSlot(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        Item item = stack.getItem();
        if (item instanceof ItemArmor) {
            return ((ItemArmor) item).armorType;
        }
        return null;
    }

    private static boolean cnpcplus$merge(ContainerNPCInv self, ItemStack stack, int start, int end, boolean reverse) {
        try {
            if (mergeMethod == null) {
                mergeMethod = net.minecraft.inventory.Container.class.getDeclaredMethod(
                        "mergeItemStack", ItemStack.class, int.class, int.class, boolean.class);
                mergeMethod.setAccessible(true);
            }
            Object r = mergeMethod.invoke(self, stack, Integer.valueOf(start), Integer.valueOf(end), Boolean.valueOf(reverse));
            return r instanceof Boolean && ((Boolean) r).booleanValue();
        } catch (Throwable t) {
            // fallback SRG name
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
