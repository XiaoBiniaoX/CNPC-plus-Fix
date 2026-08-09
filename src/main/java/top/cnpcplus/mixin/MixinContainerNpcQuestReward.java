package top.cnpcplus.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import noppes.npcs.NoppesUtilServer;
import noppes.npcs.containers.ContainerNpcQuestReward;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ContainerNpcQuestReward.class, remap = false)
public class MixinContainerNpcQuestReward {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cnpcplus$symmetricGrid(int containerId, Inventory playerInventory, CallbackInfo ci) {
        ContainerNpcQuestReward self = (ContainerNpcQuestReward) (Object) this;
        ((AbstractContainerMenu) self).slots.clear();
        Quest quest = NoppesUtilServer.getEditingQuest(playerInventory.player);
        for (int l = 0; l < 3; ++l) {
            for (int k = 0; k < 5; ++k) {
                ((AbstractContainerMenuInvoker) self).cnpcplus$addSlot(new Slot(quest.rewardItems, k + l * 5, 72 + k * 18, 17 + l * 18));
            }
        }
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                ((AbstractContainerMenuInvoker) self).cnpcplus$addSlot(new Slot(playerInventory, j + i * 9 + 9, 8 + j * 18, 84 + i * 18));
            }
        }
        for (int j = 0; j < 9; ++j) {
            ((AbstractContainerMenuInvoker) self).cnpcplus$addSlot(new Slot(playerInventory, j, 8 + j * 18, 142));
        }
    }
}
