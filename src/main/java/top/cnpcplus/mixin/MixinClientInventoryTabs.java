package top.cnpcplus.mixin;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.neoforged.neoforge.client.event.ScreenEvent;
import noppes.npcs.CustomNpcs;
import noppes.npcs.client.ClientEventHandler;
import noppes.npcs.client.gui.player.tabs.InventoryTabFactions;
import noppes.npcs.client.gui.player.tabs.InventoryTabQuests;
import noppes.npcs.client.gui.player.tabs.InventoryTabVanilla;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.config.ServerConfigAccess;

/** Filter only inventory injection; native Quest Log/key pages retain their tabs. */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class MixinClientInventoryTabs {
    @Inject(method = "guiPostInit", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$inventoryTabs(ScreenEvent.Init.Post event, CallbackInfo ci) {
        if (!(event.getScreen() instanceof InventoryScreen screen) || !CustomNpcs.InventoryGuiEnabled) return;
        ci.cancel();
        event.addListener(new InventoryTabVanilla().init(screen));
        if (ServerConfigAccess.bool(CnpcPlusConfig.INVENTORY_FACTION_TAB, true)) event.addListener(new InventoryTabFactions().init(screen));
        if (ServerConfigAccess.bool(CnpcPlusConfig.INVENTORY_QUEST_TAB, true)) event.addListener(new InventoryTabQuests().init(screen));
    }
}
