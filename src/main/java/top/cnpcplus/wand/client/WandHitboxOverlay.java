package top.cnpcplus.wand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import noppes.npcs.entity.EntityNPCInterface;
import top.cnpcplus.config.CnpcPlusConfigData;
import top.cnpcplus.wand.WandTools;

/** Draw actual entity AABBs, never model dimensions or an estimated cube. */
public final class WandHitboxOverlay {
    private WandHitboxOverlay() {}

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES
                || !CnpcPlusConfigData.WandNpcHitboxes.get()) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null
                || !(WandTools.showsHitboxes(mc.player.getMainHandItem())
                || WandTools.showsHitboxes(mc.player.getOffhandItem()))) return;
        var npcs = mc.level.getEntitiesOfClass(EntityNPCInterface.class, mc.player.getBoundingBox().inflate(30),
                npc -> WandTools.inRange(npc.distanceToSqr(mc.player)));
        if (npcs.isEmpty()) return;
        var pose = event.getPoseStack();
        var camera = event.getCamera().getPosition();
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        try {
            pose.translate(-camera.x, -camera.y, -camera.z);
            var lines = buffers.getBuffer(RenderType.lines());
            float partial = event.getPartialTick();
            for (var npc : npcs) {
                var box = npc.getBoundingBox().move(
                        Mth.lerp(partial, npc.xOld, npc.getX()) - npc.getX(),
                        Mth.lerp(partial, npc.yOld, npc.getY()) - npc.getY(),
                        Mth.lerp(partial, npc.zOld, npc.getZ()) - npc.getZ());
                LevelRenderer.renderLineBox(pose, lines, box, 0.25F, 0.85F, 1.0F, 1.0F);
            }
            buffers.endBatch(RenderType.lines());
        } finally { pose.popPose(); }
    }
}
