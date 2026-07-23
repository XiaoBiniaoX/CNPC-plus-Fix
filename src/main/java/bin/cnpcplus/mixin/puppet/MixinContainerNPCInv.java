package bin.cnpcplus.mixin.puppet;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.Slot;
import noppes.npcs.containers.ContainerNPCInv;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ContainerNPCInv.class, remap = false)
public class MixinContainerNPCInv {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onQuickMoveStack(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        ContainerNPCInv self = (ContainerNPCInv)(Object)this;

        if (slotIndex >= 0 && slotIndex <= 3) {
            Slot slot = self.getSlot(slotIndex);
            if (!slot.hasItem()) { cir.setReturnValue(ItemStack.EMPTY); return; }
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            slot.set(ItemStack.EMPTY);
            ContainerMenuAccessor acc = (ContainerMenuAccessor) self;
            if (!acc.cnpcplus$moveItemStackTo(stack, 16, 52, true)) {
                slot.set(stack);
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            cir.setReturnValue(original);
            return;
        }

        if (slotIndex >= 16 && slotIndex < 52) {
            Slot slot = self.getSlot(slotIndex);
            if (!slot.hasItem()) return;
            ItemStack stack = slot.getItem();
            EquipmentSlot equipSlot = cnpcplus$getEquipmentSlot(stack);
            if (equipSlot != null) {
                int armorSlot = 3 - equipSlot.getIndex();
                Slot targetSlot = self.getSlot(armorSlot);
                if (targetSlot != null && !targetSlot.hasItem() && targetSlot.mayPlace(stack)) {
                    ItemStack original = stack.copy();
                    slot.set(ItemStack.EMPTY);
                    ContainerMenuAccessor acc = (ContainerMenuAccessor) self;
                    if (!acc.cnpcplus$moveItemStackTo(stack, armorSlot, armorSlot + 1, false)) {
                        slot.set(stack);
                        cir.setReturnValue(ItemStack.EMPTY);
                        return;
                    }
                    cir.setReturnValue(original);
                    return;
                }
            }
        }
    }

    private static EquipmentSlot cnpcplus$getEquipmentSlot(ItemStack stack) {
        if (stack.isEmpty()) return null;
        net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof ArmorItem) {
            return ((ArmorItem) item).getEquipmentSlot();
        }
        return null;
    }
}
