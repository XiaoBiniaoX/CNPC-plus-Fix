package bin.cnpcplus.mixin.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import noppes.npcs.client.renderer.RenderNPCInterface;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;

@Mixin(value = RenderNPCInterface.class, remap = false)
public class MixinRenderNPCShadow {
    @Inject(method = "func_76979_b", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/RenderLiving;func_76979_b(Lnet/minecraft/entity/Entity;DDDFF)V"),
            remap = false)
    private void cnpcplus$useModelShadow(Entity entity, double x, double y, double z,
                                         float yaw, float partialTicks, CallbackInfo ci) {
        if (!(entity instanceof EntityCustomNpc)) return;
        EntityCustomNpc npc = (EntityCustomNpc) entity;
        EntityLivingBase modelEntity = npc.modelData.getEntity(npc);
        if (modelEntity == null) return;
        Render<?> modelRenderer = Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(modelEntity);
        if (!(modelRenderer instanceof RenderLivingBase)) return;
        try {
            Field shadow = Render.class.getDeclaredField("field_76989_e");
            shadow.setAccessible(true);
            float radius = shadow.getFloat(modelRenderer) * npc.display.getSize() / 5.0F;
            shadow.setFloat(this, radius);
        } catch (ReflectiveOperationException ignored) {
        }
    }
}
