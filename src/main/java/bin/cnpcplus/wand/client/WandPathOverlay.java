package bin.cnpcplus.wand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.*;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.CustomItems;
import noppes.npcs.entity.EntityNPCInterface;
import org.lwjgl.opengl.GL11;

/** Client-only port of 1.20.1's terrain-following route and direction arrowheads. */
@Mod.EventBusSubscriber(modid = "cnpcplus", value = Side.CLIENT)
public final class WandPathOverlay {
    private WandPathOverlay() {}
    private static boolean showsPaths(ItemStack stack) {
        return stack.getItem() == CustomItems.wand || stack.getItem() == CustomItems.cloner || stack.getItem() == CustomItems.scripter || stack.getItem() == CustomItems.moving;
    }
    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || !(showsPaths(mc.player.getHeldItemMainhand()) || showsPaths(mc.player.getHeldItemOffhand()))) return;
        net.minecraft.entity.Entity camera = mc.getRenderViewEntity();
        float partial = event.getPartialTicks();
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D), lighting = GL11.glIsEnabled(GL11.GL_LIGHTING), fog = GL11.glIsEnabled(GL11.GL_FOG), mask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        float lineWidth = GL11.glGetFloat(GL11.GL_LINE_WIDTH);
        GlStateManager.pushMatrix(); GlStateManager.translate(-cx, -cy, -cz);
        GlStateManager.disableTexture2D(); GlStateManager.disableLighting(); GlStateManager.disableFog(); GlStateManager.depthMask(false);
        GL11.glLineWidth(1.5f);
        BufferBuilder lines = Tessellator.getInstance().getBuffer(); lines.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR);
        for (EntityNPCInterface npc : mc.world.getEntitiesWithinAABB(EntityNPCInterface.class, mc.player.getEntityBoundingBox().grow(30))) {
            if (npc.getDistanceSq(mc.player) > 900) continue;
            java.util.List<int[]> route = npc.ais.getMovingPath(); if (route.size() < 2) continue;
            int edges = route.size() - (npc.ais.getMovingPathType() == 0 ? 0 : 1);
            for (int i = 0; i < edges; i++) {
                int[] a = route.get(i), b = route.get((i + 1) % route.size());
                Vec3d start = new Vec3d(a[0] + .5, a[1], a[2] + .5), end = new Vec3d(b[0] + .5, b[1], b[2] + .5);
                double length = start.distanceTo(end); if (length < .01) continue;
                int steps = Math.min(512, Math.max(1, (int)Math.ceil(length * 2)));
                Vec3d previous = ground(start, npc);
                for (int n = 1; n <= steps; n++) {
                    Vec3d point = start.add(end.subtract(start).scale((double)n / steps)), next = ground(point, npc);
                    if (point.squareDistanceTo(mc.player.getPositionVector()) <= 900) {
                        line(lines, previous, next);
                        if (n % 6 == 0 || n == steps) {
                            Vec3d direction = new Vec3d(end.x - start.x, 0, end.z - start.z).normalize();
                            Vec3d rear = point.subtract(direction.scale(.38)), side = new Vec3d(-direction.z, 0, direction.x).scale(.2);
                            line(lines, next, ground(rear.add(side), npc)); line(lines, next, ground(rear.subtract(side), npc));
                        }
                    }
                    previous = next;
                }
            }
        }
        Tessellator.getInstance().draw(); GlStateManager.depthMask(mask);
        if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
        if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
        if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
        GL11.glLineWidth(lineWidth); GlStateManager.popMatrix();
    }
    private static Vec3d ground(Vec3d point, EntityNPCInterface npc) {
        RayTraceResult hit = npc.world.rayTraceBlocks(point.add(0, 1.5, 0), point.add(0, -4, 0), false, true, false);
        return hit == null ? point.add(0, .035, 0) : hit.hitVec.add(0, .035, 0);
    }
    private static void line(BufferBuilder lines, Vec3d a, Vec3d b) {
        lines.pos(a.x, a.y, a.z).color(255, 255, 255, 255).endVertex(); lines.pos(b.x, b.y, b.z).color(255, 255, 255, 255).endVertex();
    }
}
