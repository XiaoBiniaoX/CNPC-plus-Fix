package bin.cnpcplus.station;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.*;
import net.minecraft.client.renderer.tileentity.TileEntitySpecialRenderer;
import net.minecraft.item.Item;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = "cnpcplus", value = Side.CLIENT)
public final class StationClient extends TileEntitySpecialRenderer<CampfireTile> {
    @SubscribeEvent public static void models(ModelRegistryEvent event) {
        for (StationBlocks block : new StationBlocks[]{StationBlocks.GRINDSTONE, StationBlocks.CAMPFIRE})
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0, new ModelResourceLocation(block.getRegistryName(), "inventory"));
        ClientRegistry.bindTileEntitySpecialRenderer(CampfireTile.class, new StationClient());
    }
    @SubscribeEvent public static void disconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        Minecraft.getMinecraft().addScheduledTask(StationRecipes.CLIENT::clear);
    }
    @Override public void render(CampfireTile tile, double x, double y, double z, float partial, int destroy, float alpha) {
        int facing = tile.getWorld().getBlockState(tile.getPos()).getValue(net.minecraft.block.BlockHorizontal.FACING).getHorizontalIndex();
        for (int i = 0; i < 4; i++) if (!tile.items.get(i).isEmpty()) {
            GlStateManager.pushMatrix();
            GlStateManager.translate(x + .5, y + .45, z + .5); GlStateManager.rotate(-facing * 90, 0, 1, 0);
            GlStateManager.translate(i % 2 == 0 ? -.2 : .2, 0, i < 2 ? -.2 : .2);
            GlStateManager.rotate(90, 1, 0, 0); GlStateManager.scale(.375, .375, .375);
            Minecraft.getMinecraft().getRenderItem().renderItem(tile.items.get(i), ItemCameraTransforms.TransformType.FIXED);
            GlStateManager.popMatrix();
        }
    }
}
