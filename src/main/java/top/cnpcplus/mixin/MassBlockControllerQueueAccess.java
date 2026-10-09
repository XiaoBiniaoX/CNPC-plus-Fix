package top.cnpcplus.mixin;

import noppes.npcs.controllers.MassBlockController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

/**
 * {@code MassBlockController.queue} 的静态字段访问器，服务于 {@link MixinMassBlockController}。
 *
 * <h2>为什么需要单独一个 Accessor 接口</h2>
 * {@code MassBlockController} 里的队列是
 * <pre>
 * private static Queue&lt;IMassBlock&gt; queue;
 * </pre>
 * {@link MixinMassBlockController} 在 {@code @Inject(HEAD)} 里完整取代了
 * {@code Update()} 的方法体，所以必须自己从队列取元素 —— 但那是个 private static 字段。
 *
 * <p>Mixin 对<b>静态</b>私有字段的 {@code @Shadow} 支持在不同版本上有差异，
 * 而 {@code @Accessor} 接口 Mixin 是官方推荐、行为稳定的做法：
 * 它会在目标类里合成一个静态 getter，然后我们通过接口静态方法调用。
 * 这个写法在 0.8.x 上稳定可用，也不会与其他 mod 的 Shadow 抢名字。
 *
 * <h2>为什么 {@code remap = false}</h2>
 * {@code MassBlockController} 是 CNPC 自有类，{@code queue} 是 CNPC 自有字段。
 * 编译期与运行期用的是同一个 CNPC jar，名字不经过 SRG 重映射。
 * 加 {@code remap = false} 是明确告诉 Mixin「照字面找」，
 * 避免它徒劳地去 refmap 里查一个不存在的映射。
 */
@Mixin(value = MassBlockController.class, remap = false)
public interface MassBlockControllerQueueAccess {

    @Accessor(value = "queue", remap = false)
    static Queue<MassBlockController.IMassBlock> cnpcyouhua$getQueue() {
        throw new AssertionError("Mixin accessor 未被应用");
    }
}
