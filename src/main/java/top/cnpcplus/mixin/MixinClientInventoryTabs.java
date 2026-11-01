package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.client.event.ScreenEvent;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.ClientEventHandler;
import noppes.npcs.client.gui.player.tabs.InventoryTabFactions;
import noppes.npcs.client.gui.player.tabs.InventoryTabQuests;
import noppes.npcs.client.gui.player.tabs.InventoryTabVanilla;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.config.ServerConfigAccess;

/** Only the inventory injection is filtered; native Quest Log/key and faction tabs stay intact. */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class MixinClientInventoryTabs {
    @Inject(method = "guiPostInit", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$inventoryTabs(ScreenEvent.Init.Post event, CallbackInfo ci) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !CustomNpcs.InventoryGuiEnabled) return;
        ci.cancel();
        event.addListener(new InventoryTabVanilla().init(screen));
        if (ServerConfigAccess.bool(CnpcPlusConfigData.InventoryFactionTab, true)) {
            event.addListener(new InventoryTabFactions().init(screen));
        }
        if (ServerConfigAccess.bool(CnpcPlusConfigData.InventoryQuestTab, true)) {
            event.addListener(new InventoryTabQuests().init(screen));
        }
    }
}
