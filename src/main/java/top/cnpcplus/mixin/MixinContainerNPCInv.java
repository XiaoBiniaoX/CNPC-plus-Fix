package top.cnpcplus.mixin;

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

    @Inject(method = "m_7648_", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onQuickMoveStack(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        ContainerNPCInv self = (ContainerNPCInv)(Object)this;

        // Armor slots (0-3): move to player inventory
        if (slotIndex >= 0 && slotIndex <= 3) {
            Slot slot = self.getSlot(slotIndex);
            if (!slot.hasItem()) { cir.setReturnValue(ItemStack.EMPTY); return; }
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            slot.set(ItemStack.EMPTY);
            if (!self.moveItemStackTo(stack, 16, 52, true)) {
                slot.set(stack);
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            cir.setReturnValue(original);
            return;
        }

        // Drop slots (7-15): one-way move to player inventory
        if (slotIndex >= 7 && slotIndex <= 15) {
            Slot slot = self.getSlot(slotIndex);
            if (!slot.hasItem()) { cir.setReturnValue(ItemStack.EMPTY); return; }
            ItemStack stack = slot.getItem();
            ItemStack original = stack.copy();
            slot.set(ItemStack.EMPTY);
            if (!self.moveItemStackTo(stack, 16, 52, true)) {
                slot.set(stack);
                cir.setReturnValue(ItemStack.EMPTY);
                return;
            }
            cir.setReturnValue(original);
            return;
        }

        // Player inventory (16-51): try armor slot for armor items
        if (slotIndex >= 16 && slotIndex < 52) {
            Slot slot = self.getSlot(slotIndex);
            if (!slot.hasItem()) return;
            ItemStack stack = slot.getItem();
            EquipmentSlot equipSlot = getEquipmentSlot(stack);
            if (equipSlot != null && equipSlot.getType() == EquipmentSlot.Type.ARMOR) {
                // NPC armor slots: 0=HEAD, 1=CHEST, 2=LEGS, 3=FEET
                // EquipmentSlot.getIndex(): HEAD=3, CHEST=2, LEGS=1, FEET=0
                int armorSlot = 3 - equipSlot.getIndex();
                Slot targetSlot = self.getSlot(armorSlot);
                if (targetSlot != null && !targetSlot.hasItem() && targetSlot.mayPlace(stack)) {
                    ItemStack original = stack.copy();
                    slot.set(ItemStack.EMPTY);
                    if (!self.moveItemStackTo(stack, armorSlot, armorSlot + 1, false)) {
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

    private static EquipmentSlot getEquipmentSlot(ItemStack stack) {
        if (stack.isEmpty()) return null;
        net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof ArmorItem) {
            return ((ArmorItem) item).getEquipmentSlot();
        }
        return null;
    }
}
