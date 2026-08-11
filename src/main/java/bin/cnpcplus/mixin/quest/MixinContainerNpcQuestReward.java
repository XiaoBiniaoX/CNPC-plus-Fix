package bin.cnpcplus.mixin.quest;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.containers.ContainerNpcQuestReward;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rebuild the container as a 3x5 reward grid (15 slots, was 3x3). Player
 * inventory slots keep vanilla positions; only the reward grid is re-laid
 * out, so container sync stays identical on both sides.
 */
@Mixin(value = ContainerNpcQuestReward.class, remap = false)
public class MixinContainerNpcQuestReward {

    private void cnpcplus$addSlot(ContainerNpcQuestReward self, Slot slot) {
        slot.slotNumber = self.inventorySlots.size();
        self.inventorySlots.add(slot);
        self.inventoryItemStacks.add(ItemStack.EMPTY);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void cnpcplus$rebuildGrid(EntityPlayer player, CallbackInfo ci) {
        ContainerNpcQuestReward self = (ContainerNpcQuestReward) (Object) this;
        self.inventorySlots.clear();
        self.inventoryItemStacks.clear();
        Quest quest = NoppesUtilServer.getEditingQuest(player);
        for (int l = 0; l < 3; ++l) {
            for (int k = 0; k < 5; ++k) {
                cnpcplus$addSlot(self, new Slot(quest.rewardItems, k + l * 5, 72 + k * 18, 17 + l * 18));
            }
        }
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                cnpcplus$addSlot(self, new Slot(player.inventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int j = 0; j < 9; ++j) {
            cnpcplus$addSlot(self, new Slot(player.inventory, j, 8 + j * 18, 142));
        }
    }
}
