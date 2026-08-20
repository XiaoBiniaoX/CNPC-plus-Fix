package bin.cnpcplus.mixin.quest;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.containers.ContainerNpcQuestTypeItem;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rebuild the container as a 3x3 collect grid (9 slots, was 1x3). Player
 * inventory slots keep vanilla positions; only the quest grid is re-laid
 * out, so container sync stays identical on both sides.
 */
@Mixin(value = ContainerNpcQuestTypeItem.class, remap = false)
public class MixinContainerNpcQuestTypeItem {

    @Inject(method = "func_82846_b", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private void cnpcplus$disableShiftClick(EntityPlayer player, int slotId,
                                             CallbackInfoReturnable<ItemStack> cir) {
        cir.setReturnValue(ItemStack.EMPTY);
    }

    private void cnpcplus$addSlot(ContainerNpcQuestTypeItem self, Slot slot) {
        slot.slotNumber = self.inventorySlots.size();
        self.inventorySlots.add(slot);
        self.inventoryItemStacks.add(ItemStack.EMPTY);
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false)
    private void cnpcplus$rebuildGrid(EntityPlayer player, CallbackInfo ci) {
        ContainerNpcQuestTypeItem self = (ContainerNpcQuestTypeItem) (Object) this;
        self.inventorySlots.clear();
        self.inventoryItemStacks.clear();
        Quest quest = NoppesUtilServer.getEditingQuest(player);
        for (int l = 0; l < 3; ++l) {
            for (int k = 0; k < 3; ++k) {
                cnpcplus$addSlot(self, new Slot(((QuestItem) quest.questInterface).items, k + l * 3, 19 + k * 25, 39 + l * 25));
            }
        }
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                cnpcplus$addSlot(self, new Slot(player.inventory, j + i * 9 + 9, 8 + j * 18, 113 + i * 18));
            }
        }
        for (int j = 0; j < 9; ++j) {
            cnpcplus$addSlot(self, new Slot(player.inventory, j, 8 + j * 18, 171));
        }
    }
}
