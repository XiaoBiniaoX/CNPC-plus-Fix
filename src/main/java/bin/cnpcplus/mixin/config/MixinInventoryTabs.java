package bin.cnpcplus.mixin.config;

import bin.cnpcplus.config.CnpcPlusConfig;
import micdoodle8.mods.galacticraft.api.client.tabs.InventoryTabFactions;
import micdoodle8.mods.galacticraft.api.client.tabs.InventoryTabQuests;
import micdoodle8.mods.galacticraft.api.client.tabs.TabRegistry;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraftforge.client.event.GuiScreenEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filter only survival-inventory injection, never shared Quest Log tab registration. */
@Mixin(value = TabRegistry.class, remap = false)
public abstract class MixinInventoryTabs {
    @Inject(method = "guiPostInit", at = @At("RETURN"), require = 1)
    private void cnpcplus$filterInventoryTabs(GuiScreenEvent.InitGuiEvent.Post event, CallbackInfo ci) {
        if (!(event.getGui() instanceof GuiInventory)) return;
        CnpcPlusConfig.refreshInventoryOptions();
        event.getButtonList().removeIf(button -> button instanceof InventoryTabFactions && !CnpcPlusConfig.showInventoryFactionTab()
                || button instanceof InventoryTabQuests && !CnpcPlusConfig.showInventoryQuestTab());
    }
}
