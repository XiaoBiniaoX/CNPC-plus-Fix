package bin.cnpcplus.mixin.crafting;

import bin.cnpcplus.common.IAnvilRecipeAccess;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.client.resources.I18n;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRepair.class)
public abstract class MixinGuiRepairRecipes extends GuiContainer {
    protected MixinGuiRepairRecipes(Container container) { super(container); }

    @Inject(method = "drawGuiContainerForegroundLayer", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$displayCustomCost(int mouseX, int mouseY, CallbackInfo ci) {
        if (!((IAnvilRecipeAccess) inventorySlots).cnpcplus$isCustomRecipe()) return;
        int cost = ((ContainerRepair) inventorySlots).maximumCost;
        this.fontRenderer.drawString(I18n.format("container.repair"), 60, 6, 4210752);
        this.fontRenderer.drawString(this.mc.player.inventory.getDisplayName().getUnformattedText(), 8, this.ySize - 94, 4210752);
        if (inventorySlots.getSlot(2).getHasStack()) {
            String text = I18n.format("cnpcplus.anvil.cost", cost);
            this.fontRenderer.drawStringWithShadow(text, this.xSize - 8 - this.fontRenderer.getStringWidth(text), 69,
                    this.mc.player.capabilities.isCreativeMode || this.mc.player.experienceLevel >= cost ? 8453920 : 16736352);
        }
        ci.cancel();
    }
}
