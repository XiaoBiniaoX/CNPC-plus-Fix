package bin.cnpcplus.mixin.puppet;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerNPCBankInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ContainerNPCBankInterface.class, remap = false)
public class MixinContainerNPCBankInterface {

    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$quickMoveBank(Player player, int slotIndex, CallbackInfoReturnable<ItemStack> cir) {
        ContainerNPCBankInterface self = (ContainerNPCBankInterface) (Object) this;
        int bankSlots = self.getRowNumber() * 9;
        if (bankSlots <= 0) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        int bankStart = (!self.isAvailable() || self.canBeUpgraded()) ? 1 : 0;
        int bankEnd = bankStart + bankSlots;
        int playerStart = bankEnd;
        int playerEnd = self.slots.size();

        if (slotIndex < 0 || slotIndex >= playerEnd) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        Slot slot = self.getSlot(slotIndex);
        if (!slot.hasItem()) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        ContainerMenuAccessor acc = (ContainerMenuAccessor) self;
        boolean moved;
        if (slotIndex >= bankStart && slotIndex < bankEnd) {
            moved = acc.cnpcplus$moveItemStackTo(stack, playerStart, playerEnd, true);
        } else if (slotIndex >= playerStart && slotIndex < playerEnd) {
            moved = acc.cnpcplus$moveItemStackTo(stack, bankStart, bankEnd, false);
        } else {
            moved = false;
        }

        if (!moved) {
            cir.setReturnValue(ItemStack.EMPTY);
            return;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        cir.setReturnValue(original);
    }
}
