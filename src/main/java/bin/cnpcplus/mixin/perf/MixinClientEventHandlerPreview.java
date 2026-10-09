package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.perf.client.BlueprintPreviewCache;
import bin.cnpcplus.perf.client.RenderResourceCaches;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import noppes.npcs.blocks.tiles.TileBuilder;
import noppes.npcs.client.ClientEventHandler;
import noppes.npcs.schematics.SchematicWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 优化 #1 的注入点：接管 {@code ClientEventHandler.onRenderTick}，
 * 改走 {@link BlueprintPreviewCache} 的一次性烘焙路径。
 *
 * <h2>为什么用 {@code @Inject(cancellable = true)} 而不是 {@code @Overwrite}</h2>
 * {@code @Overwrite} 会独占这个方法：任何别的附属如果也想增强蓝图预览
 * （比如加个半透明效果、加个坐标标注），就会与本 mod 硬冲突，
 * 且 Mixin 只会报一个含义模糊的 "Critical injection failure"。
 *
 * <p>用 HEAD + {@code cancel()} 的话，我们只是「先做完自己的事然后阻止原版继续」。
 * 其他附属挂在同一方法 HEAD 上的注入照常执行（按优先级排序），
 * 挂在 RETURN 上的也照常执行（{@code cancel()} 会走正常返回路径）。
 * 语义上等价于 Overwrite，但兼容面完全不同。
 *
 * <h2>为什么要在这里重复原版的前置检查</h2>
 * 原版方法 106-118 行有四道提前返回（{@code rpos == null}、
 * {@code rpos == BlockPos.ZERO}、距离平方 &gt; 1000000、{@code schem == null}）。
 * 我们在 HEAD 取代整个方法体，所以必须把这些检查原样搬过来 ——
 * 少一道就可能空指针，多一道就可能改变可见范围。
 *
 * <p>距离阈值 1000000（平方）= 1000 格，与原版一字不差地保留。
 */
@Mixin(value = ClientEventHandler.class, remap = false)
public abstract class MixinClientEventHandlerPreview {

    @Inject(
            method = "onRenderTick(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false
    )
    private static void cnpcplus$bakedPreview(PoseStack matrixStack, BlockPos rpos, BlockEntity te,
                                                CallbackInfo ci) {
        if (!RenderResourceCaches.blueprintCacheEnabled) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            ci.cancel();
            return;
        }
        // 以下四道检查与原版 ClientEventHandler.onRenderTick 第 109-118 行逐条对应。
        if (rpos == null || rpos == BlockPos.ZERO) {
            ci.cancel();
            return;
        }
        if (rpos.distSqr(player.blockPosition()) > 1000000.0D) {
            ci.cancel();
            return;
        }
        if (!(te instanceof TileBuilder tile)) {
            ci.cancel();
            return;
        }
        SchematicWrapper schem = tile.getSchematic();
        if (schem == null) {
            ci.cancel();
            return;
        }

        try {
            BlueprintPreviewCache.render(matrixStack, tile, schem);
            ci.cancel();
        } catch (RuntimeException e) {
            // 第三方特殊方块不兼容时，本次及后续预览回到 CNPC 原绘制路径。
            RenderResourceCaches.blueprintCacheEnabled = false;
            BlueprintPreviewCache.discard();
            org.apache.logging.log4j.LogManager.getLogger("CNPCOptimization")
                    .error("Preview cache disabled after render error; using original CNPC renderer", e);
        }
    }
}
