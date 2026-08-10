package bin.cnpcplus.mixin.quest;

import bin.cnpcplus.mixin.AbstractContainerMenuInvoker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.containers.ContainerNpcQuestTypeItem;
import noppes.npcs.controllers.data.Quest;
import noppes.npcs.quests.QuestItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerNpcQuestTypeItem.class, remap = false)
public class MixinContainerNpcQuestTypeItem {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cnpcplus$symmetricGrid(int containerId, Inventory playerInventory, CallbackInfo ci) {
        AbstractContainerMenuInvoker invoker = (AbstractContainerMenuInvoker) this;
        invoker.cnpcplus$getSlots().clear();
        invoker.cnpcplus$getLastSlots().clear();
        invoker.cnpcplus$getRemoteSlots().clear();
        Quest quest = NoppesUtilServer.getEditingQuest(playerInventory.player);
        for (int l = 0; l < 3; ++l) {
            for (int k = 0; k < 3; ++k) {
                invoker.cnpcplus$addSlot(new Slot(((QuestItem) quest.questInterface).items, k + l * 3, 19 + k * 25, 39 + l * 25));
            }
        }
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                invoker.cnpcplus$addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 113 + i * 18));
            }
        }
        for (int j = 0; j < 9; ++j) {
            invoker.cnpcplus$addSlot(new Slot(playerInventory, j, 8 + j * 18, 171));
        }
    }
}
