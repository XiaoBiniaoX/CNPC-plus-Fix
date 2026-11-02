package bin.cnpcplus.wand.client;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.CustomItems;
import noppes.npcs.client.gui.GuiNpcRemoteEditor;
import noppes.npcs.entity.EntityNPCInterface;

@Mod.EventBusSubscriber(modid = "cnpcplus", value = Side.CLIENT)
public final class WandClientEvents {
    @SubscribeEvent public static void open(GuiOpenEvent event) {
        if (event.getGui() != null && event.getGui().getClass() == GuiNpcRemoteEditor.class)
            event.setGui(new GuiWandNpcList());
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!CnpcPlusConfig.wandNpcHitboxes() || mc.world == null || mc.player == null
                || !(wand(mc.player.getHeldItemMainhand()) || wand(mc.player.getHeldItemOffhand()))) return;
        Entity camera = mc.getRenderViewEntity(); if (camera == null) return;
        float partial = event.getPartialTicks();
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        GlStateManager.pushMatrix(); GlStateManager.disableTexture2D(); GlStateManager.disableLighting();
        GlStateManager.enableBlend(); GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0); GlStateManager.depthMask(false);
        for (EntityNPCInterface npc : mc.world.getEntitiesWithinAABB(EntityNPCInterface.class, mc.player.getEntityBoundingBox().grow(30))) {
            if (npc.getDistanceSq(mc.player) > 900) continue;
            RenderGlobal.drawSelectionBoundingBox(npc.getEntityBoundingBox().offset(
                    npc.lastTickPosX + (npc.posX - npc.lastTickPosX) * partial - npc.posX - cx,
                    npc.lastTickPosY + (npc.posY - npc.lastTickPosY) * partial - npc.posY - cy,
                    npc.lastTickPosZ + (npc.posZ - npc.lastTickPosZ) * partial - npc.posZ - cz), .25F, .85F, 1F, 1F);
        }
        GlStateManager.depthMask(true); GlStateManager.disableBlend(); GlStateManager.enableLighting();
        GlStateManager.enableTexture2D(); GlStateManager.color(1, 1, 1, 1); GlStateManager.popMatrix();
    }
    private static boolean wand(ItemStack stack) {
        return !stack.isEmpty() && (stack.getItem() == CustomItems.wand || stack.getItem() == CustomItems.cloner || stack.getItem() == CustomItems.scripter);
    }
}
