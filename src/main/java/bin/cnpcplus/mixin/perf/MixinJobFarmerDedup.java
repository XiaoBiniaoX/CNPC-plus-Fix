package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.util.math.BlockPos;
import noppes.npcs.roles.JobFarmer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashSet;
import java.util.List;

/**
 * 优化 #2 的遗留项（主内容「农夫源头过滤」在 1.12.2 的对应缺口）：
 * 消除 {@code JobFarmer.processed()} 去重的 O(n²)。
 *
 * <h2>原版做了什么（javap 实证，1.12.2 05Jul20）</h2>
 * {@code processed(List&lt;BlockData&gt;)} 把扫描结果重建进 {@code trackedBlocks}：
 * <pre>
 *  116: aload_2 (trackedBlocks 候选)
 *  122: invokeinterface List.contains:(Ljava/lang/Object;)Z   ← 每个作物一次线性扫描
 *  127: ifne 142
 *  130: List.add(BlockData.pos)
 * </pre>
 * 作物位置是值语义（BlockPos.equals 按坐标比较），原版用 {@code List.contains}
 * 逐个比对。范围 16 的农田几百上千个作物位时是 O(n²) 次 equals，
 * 且这一段在扫描结果交付时同步执行（ServerTickHandler 每 20 tick）。
 *
 * <h2>本类怎么做</h2>
 * 把唯一一处 {@code List.contains} 重定向到 {@link HashSet}（BlockPos 的
 * hashCode/equals 都是值语义，与 {@code List.contains} 的判定完全一致）。
 * HEAD 注入在每次重建前清空集合。
 *
 * <p><b>为什么不能用身份集合</b>：扫描结果里同一坐标可能出现不同实例，
 * 原版按 equals 去重；IdentityHashMap 会漏判，改变 trackedBlocks 内容。
 *
 * <h2>配置</h2>
 * {@code farmerDedupOptimized}（默认 true）。关闭时重定向处理器直接调用
 * 原列表的 {@code contains}，行为退回原版。
 */
@Mixin(value = JobFarmer.class, remap = false)
public class MixinJobFarmerDedup {

    @Unique
    private final HashSet<BlockPos> cnpcplus$seen = new HashSet<BlockPos>();

    @Inject(method = "processed", at = @At("HEAD"), remap = false)
    private void cnpcplus$clearSeen(List<?> blocks, CallbackInfo ci) {
        this.cnpcplus$seen.clear();
    }

    @Redirect(
            method = "processed",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;contains(Ljava/lang/Object;)Z"),
            remap = false
    )
    private boolean cnpcplus$dedupWithSet(List self, Object pos) {
        if (!CnpcPlusConfig.isFarmerDedupOptimized()) {
            return self.contains(pos);
        }
        // set.add 返回 true = 首次出现 → contains 应为 false → 原版继续 add。
        return !this.cnpcplus$seen.add((BlockPos) pos);
    }
}
