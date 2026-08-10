package bin.cnpcplus.mixin.trader;

import bin.cnpcplus.mixin.AbstractContainerMenuInvoker;
import bin.cnpcplus.mixin.puppet.ContainerMenuAccessor;
import bin.cnpcplus.trader.SlotNpcTrader;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import noppes.npcs.containers.ContainerNPCTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerNPCTrader.class, remap = false)
public abstract class MixinContainerNPCTrader {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cnpcplus$restrictSellSlots(int containerId, Inventory player, int entityId, CallbackInfo ci) {
        AbstractContainerMenuInvoker menu = (AbstractContainerMenuInvoker) this;
        int limit = Math.min(18, menu.cnpcplus$getSlots().size());
        for (int i = 0; i < limit; i++) {
            Slot old = menu.cnpcplus$getSlots().get(i);
            Slot repl = new SlotNpcTrader(((ContainerNPCTrader) (Object) this).role.inventorySold, i, old.x, old.y);
            repl.index = i;
            menu.cnpcplus$getSlots().set(i, repl);
        }
    }

    /**
     * @author cnpcplus
     * @reason Restore 1.20.1 CNPC quick move: shift-click in player area must work.
     */
    @Overwrite
    public ItemStack quickMoveStack(Player player, int index) {
        AbstractContainerMenuInvoker menu = (AbstractContainerMenuInvoker) this;
        ContainerMenuAccessor move = (ContainerMenuAccessor) this;
        ItemStack result = ItemStack.EMPTY;
        Slot slot = menu.cnpcplus$getSlots().get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack item = slot.getItem();
            result = item.copy();
            if (index < 45) {
                if (!move.cnpcplus$moveItemStackTo(item, 45, menu.cnpcplus$getSlots().size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!move.cnpcplus$moveItemStackTo(item, 18, 45, false)) {
                return ItemStack.EMPTY;
            }
            if (item.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return result;
    }
}
