package bin.cnpcplus.wand.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import noppes.npcs.CustomItems;
import noppes.npcs.entity.EntityNPCInterface;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

/** Client-only, terrain-following gold route ribbons matching the modern path overlay. */
@Mod.EventBusSubscriber(modid = "cnpcplus", value = Side.CLIENT)
public final class WandPathOverlay {
    private static final double RANGE = 30.0;
    private static final int OUTLINE = 0xC029241D;
    private static final int ROUTE = 0xFFFFD34D;
    private static final double OUTLINE_WIDTH = .045;
    private static final double ROUTE_WIDTH = .032;

    private WandPathOverlay() {}

    private static boolean showsPaths(ItemStack stack) {
        return stack.getItem() == CustomItems.wand || stack.getItem() == CustomItems.cloner
                || stack.getItem() == CustomItems.scripter || stack.getItem() == CustomItems.moving;
    }

    @SubscribeEvent public static void render(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || mc.player == null || !(showsPaths(mc.player.getHeldItemMainhand())
                || showsPaths(mc.player.getHeldItemOffhand()))) return;
        net.minecraft.entity.Entity camera = mc.getRenderViewEntity();
        if (camera == null) return;
        float partial = event.getPartialTicks();
        double cx = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partial;
        double cy = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partial;
        double cz = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partial;
        Vec3d player = mc.player.getPositionVector();
        double rangeSq = RANGE * RANGE;
        List<Stroke> strokes = new ArrayList<>();
        List<Vec3d> waypoints = new ArrayList<>();
        AxisAlignedBB bounds = mc.player.getEntityBoundingBox().grow(RANGE);
        for (EntityNPCInterface npc : mc.world.getEntitiesWithinAABB(EntityNPCInterface.class, bounds)) {
            if (npc.getDistanceSq(mc.player) > rangeSq) continue;
            List<int[]> route = npc.ais.getMovingPath();
            if (route.size() < 2) continue;
            boolean closed = npc.ais.getMovingPathType() == 0;
            for (int i = 0; i < route.size(); i++) {
                int[] point = route.get(i);
                if (!valid(point) || !isTurn(route, i, closed)) continue;
                Vec3d raw = blockCenter(point);
                if (raw.squareDistanceTo(player) > rangeSq) continue;
                waypoints.add(ground(raw, npc));
            }
            int edges = route.size() - (closed ? 0 : 1);
            for (int i = 0; i < edges; i++) {
                int[] a = route.get(i), b = route.get((i + 1) % route.size());
                if (!valid(a) || !valid(b)) continue;
                Vec3d start = blockCenter(a), end = blockCenter(b);
                double length = start.distanceTo(end);
                if (length < .01) continue;
                int steps = Math.min(512, Math.max(1, (int)Math.ceil(length * 2)));
                int arrowSpacing = Math.max(16, (int)Math.round(8.0 * steps / length));
                int arrowPhase = arrowSpacing / 2;
                Vec3d previous = ground(start, npc);
                Vec3d direction = new Vec3d(end.x - start.x, 0, end.z - start.z).normalize();
                for (int n = 1; n <= steps; n++) {
                    Vec3d point = interpolate(start, end, (double) n / steps);
                    Vec3d next = ground(point, npc);
                    if (point.squareDistanceTo(player) <= rangeSq) {
                        strokes.add(new Stroke(previous, next));
                        if (n >= arrowPhase && (n - arrowPhase) % arrowSpacing == 0 && n <= steps - arrowPhase
                                && direction.lengthSquared() > 1.0e-8) {
                            Vec3d rear = point.subtract(direction.scale(.28));
                            Vec3d side = new Vec3d(-direction.z, 0, direction.x).scale(.13);
                            strokes.add(new Stroke(next, ground(rear.add(side), npc)));
                            strokes.add(new Stroke(next, ground(rear.subtract(side), npc)));
                        }
                    }
                    previous = next;
                }
            }
        }

        boolean texture = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        int depthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        GlStateManager.pushMatrix();
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        try {
            GlStateManager.translate(-cx, -cy, -cz);
            GlStateManager.disableTexture2D();
            GlStateManager.disableLighting();
            GlStateManager.disableFog();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            // RenderType.debugQuads() in 1.20.1: translucent, no culling, LEQUAL and depth writes.
            GlStateManager.disableCull();
            GlStateManager.enableDepth();
            GlStateManager.depthFunc(GL11.GL_LEQUAL);
            GlStateManager.depthMask(true);
            drawStrokes(strokes);
            drawWaypoints(waypoints);
        } finally {
            GL11.glPopAttrib();
            if (texture) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            if (fog) GlStateManager.enableFog(); else GlStateManager.disableFog();
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (cull) GlStateManager.enableCull(); else GlStateManager.disableCull();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            GlStateManager.depthFunc(depthFunc);
            GlStateManager.tryBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GlStateManager.depthMask(depthMask);
            GlStateManager.popMatrix();
        }
    }

    private static boolean valid(int[] point) { return point != null && point.length >= 3; }

    private static Vec3d blockCenter(int[] point) { return new Vec3d(point[0] + .5, point[1], point[2] + .5); }

    private static Vec3d interpolate(Vec3d start, Vec3d end, double t) {
        return start.add(end.subtract(start).scale(t));
    }

    private static boolean isTurn(List<int[]> route, int index, boolean closed) {
        if (!closed && (index == 0 || index == route.size() - 1)) return true;
        if (!closed && route.size() < 3) return false;
        int[] previous = route.get((index + route.size() - 1) % route.size());
        int[] current = route.get(index);
        int[] next = route.get((index + 1) % route.size());
        if (!valid(previous) || !valid(current) || !valid(next)) return false;
        double ax = current[0] - previous[0], ay = current[1] - previous[1], az = current[2] - previous[2];
        double bx = next[0] - current[0], by = next[1] - current[1], bz = next[2] - current[2];
        double lengths = Math.sqrt((ax * ax + ay * ay + az * az) * (bx * bx + by * by + bz * bz));
        return lengths == 0 || (ax * bx + ay * by + az * bz) / lengths < .94;
    }

    private static Vec3d ground(Vec3d point, EntityNPCInterface npc) {
        RayTraceResult hit = npc.world.rayTraceBlocks(point.add(0, 1.5, 0), point.add(0, -4, 0), false, true, false);
        return hit == null ? point.add(0, .035, 0) : hit.hitVec.add(0, .035, 0);
    }

    private static void drawStrokes(List<Stroke> strokes) {
        if (strokes.isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (Stroke stroke : strokes) {
            ribbon(buffer, stroke.start, stroke.end, OUTLINE_WIDTH, OUTLINE);
            ribbon(buffer, stroke.start.add(0, .004, 0), stroke.end.add(0, .004, 0), ROUTE_WIDTH, ROUTE);
        }
        Tessellator.getInstance().draw();
    }

    private static void drawWaypoints(List<Vec3d> points) {
        if (points.isEmpty()) return;
        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        for (Vec3d point : points) {
            diamond(buffer, point, .11, OUTLINE);
            diamond(buffer, point.add(0, .006, 0), .065, ROUTE);
        }
        Tessellator.getInstance().draw();
    }

    private static void diamond(BufferBuilder buffer, Vec3d center, double radius, int color) {
        Vec3d middle = center.add(0, .012, 0);
        quad(buffer, middle.add(0, 0, -radius), middle.add(radius, 0, 0),
                middle.add(0, 0, radius), middle.add(-radius, 0, 0), color);
    }
    private static void ribbon(BufferBuilder buffer, Vec3d a, Vec3d b, double width, int color) {
        Vec3d delta = b.subtract(a);
        Vec3d side = new Vec3d(-delta.z, 0, delta.x);
        if (side.lengthSquared() < 1.0e-8) side = new Vec3d(1, 0, 0);
        side = side.normalize().scale(width);
        quad(buffer, a.add(side), b.add(side), b.subtract(side), a.subtract(side), color);
    }

    private static void quad(BufferBuilder buffer, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int color) {
        vertex(buffer, a, color); vertex(buffer, b, color); vertex(buffer, c, color); vertex(buffer, d, color);
    }

    private static void vertex(BufferBuilder buffer, Vec3d point, int color) {
        buffer.pos(point.x, point.y, point.z).color(color >> 16 & 255, color >> 8 & 255,
                color & 255, color >>> 24).endVertex();
    }

    private static final class Stroke {
        final Vec3d start, end;
        Stroke(Vec3d start, Vec3d end) { this.start = start; this.end = end; }
    }
}
