package top.cnpcplus.perf.client;

import top.cnpcplus.CnpcPlus;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 优化 #1 的配套回收器：预览关闭后释放 {@link BlueprintPreviewCache} 占用的显存。
 *
 * <h2>为什么需要一个独立的回收器</h2>
 * 关闭预览的路径是 {@code GuiBlockBuilder.buttonEvent}（id == 3）里的
 * {@code TileBuilder.SetDrawPos(null)} + {@code tile.setDrawSchematic(null)}。
 * 之后 {@code BlockBuilderRenderer.render} 里的
 * {@code tile.getBlockPos().equals(TileBuilder.DrawPos)} 不再成立，
 * {@code ClientEventHandler.onRenderTick} 直接不被调用。
 *
 * <p>也就是说<b>没有任何「关闭」回调可以挂钩</b>。
 * 同样的情况还有：玩家走远（{@code onRenderTick} 里有距离 &gt; 1000000 的提前返回）、
 * 建筑方块被破坏、区块卸载、退出世界。
 *
 * <p>与其给每条关闭路径都打一个 Mixin（漏一条就泄漏显存），
 * 不如用一个「停止绘制即回收」的判据：这一个条件天然覆盖上面所有情况。
 *
 * <h2>为什么用 {@code ClientTickEvent} 而不是渲染事件</h2>
 * {@code VertexBuffer.close()} 要求在渲染线程执行。
 * {@code ClientTickEvent} 就在主（渲染）线程上跑，满足要求，
 * 且 20 次/秒的频率对于「读一个 long 做减法」这种检查完全够用。
 *
 * <h2>为什么宽限期是 1 秒</h2>
 * 判据是「距上次绘制超过 GRACE_NANOS」。这个值要同时满足两点：
 * <ul>
 *   <li>足够长，不能在正常游戏中误判。最低帧率场合（比如加载区块时掉到 5 FPS）
 *       帧间隔约 200 ms，1 秒有 5 倍余量。</li>
 *   <li>足够短，关掉预览后不会让 80 MB 显存长期挂着。1 秒后即释放。</li>
 * </ul>
 * 误判的代价也很低：只是下次绘制时重新烘焙一遍（十几帧），不会有任何视觉错误。
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = CnpcPlus.MOD_ID, value = Dist.CLIENT)
public final class BlueprintPreviewJanitor {

    private static final long GRACE_NANOS = 1_000_000_000L;

    private BlueprintPreviewJanitor() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        RenderResourceCaches.tick(Minecraft.getInstance().level);
        if (!BlueprintPreviewCache.hasCache()) {
            return;
        }
        // 退出到主菜单时立刻回收，不必等宽限期。
        if (Minecraft.getInstance().level == null) {
            BlueprintPreviewCache.discard();
            return;
        }
        if (BlueprintPreviewCache.sinceLastDraw() > GRACE_NANOS) {
            BlueprintPreviewCache.discard();
        }
    }
}
