package bin.cnpcplus.crafting.client;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.crafting.SmithingContainer;
import bin.cnpcplus.crafting.SmithingTable;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.Item;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = CnpcPlus.MODID, value = Side.CLIENT)
public final class SmithingScreen extends GuiContainer {
    private static final ResourceLocation TEXTURE = new ResourceLocation("cnpcplus", "textures/gui/smithing.png");
    public SmithingScreen(SmithingContainer container) { super(container); }
    @SubscribeEvent public static void registerModel(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(SmithingTable.TABLE), 0,
                new ModelResourceLocation(SmithingTable.TABLE.getRegistryName(), "inventory"));
    }
    @Override protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color(1, 1, 1, 1);
        this.mc.getTextureManager().bindTexture(TEXTURE);
        this.drawTexturedModalRect(this.guiLeft, this.guiTop, 0, 0, this.xSize, this.ySize);
    }
    @Override protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.fontRenderer.drawString(I18n.format("cnpcplus.smithing.title"), 8, 6, 4210752);
        this.fontRenderer.drawString(this.mc.player.inventory.getDisplayName().getUnformattedText(), 8, 72, 4210752);
    }
    @Override public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground(); super.drawScreen(mouseX, mouseY, partialTicks); renderHoveredToolTip(mouseX, mouseY);
    }
}
