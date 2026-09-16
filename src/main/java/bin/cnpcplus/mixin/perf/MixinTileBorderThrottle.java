package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import noppes.npcs.blocks.tiles.TileBorder;
import noppes.npcs.controllers.data.Availability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * 性能问题 12：Border 方块每 tick 查询实体 + 执行 Availability 检查。
 *
 * <h3>根因（javap 字节码实证）—— 频率比哈基彬假设高约 3 个数量级</h3>
 * 哈基彬猜测这是 {@code Block.updateTick}（随机刻）。实际是 TileEntity tick：
 * <pre>
 * TileBorder implements com.google.common.base.Predicate, net.minecraft.util.ITickable
 * BlockBorder 没有 func_180650_b(updateTick)，也没调 func_149675_a(needsRandomTick)
 * </pre>
 * {@code ITickable} ⇒ **每游戏刻 20 次/秒/方块**，不是随机刻的约 1/1365。
 *
 * {@code func_73660_a()} **无任何门禁**（唯一判断是 offset 7 的客户端 return）：
 * <pre>
 *  7: ifeq 11                          ← 仅客户端 return
 * 11: new AxisAlignedBB                ← 每 tick 新建
 * 19-72: minX=x, minY=y, minZ=z, maxX=x+1, maxY=y+height+1, maxZ=z+1
 * 82: ldc class net/minecraft/entity/Entity   ← ★ 查的是 Entity 基类，不是 EntityPlayer
 * 86: World.func_175647_a(Class, AABB, Predicate)  ← 每 tick 新建 ArrayList（即使为空）
 * 155/183: Availability.isAvailable(EntityPlayer)  ← 17 分支 14 invoke
 * 186: ifeq 192                         ← ★ 通行也已完整执行完检查
 * </pre>
 * 默认 {@code height = 10}（构造器 offset 21 {@code bipush 10}），
 * 故 AABB 是 1×11×1。哈基彬说「100 格边界就是 2000 次/秒」完全正确。
 *
 * {@code Availability.isAvailable} 串行 13 项检查：daytime ×2、dialogAvailable ×4、
 * questAvailable ×4、factionAvailable ×2、scoreboardAvailable ×2、玩家等级。
 * 配了 quest/dialog/faction 条件时还要 {@code PlayerData.get} + {@code HashSet.contains}
 * + {@code Integer} 装箱；配了 scoreboard 则 3 次 {@code getWorldScoreboard}。
 *
 * <h3>修法（三件事）</h3>
 * <ol>
 *   <li><b>tick 门禁</b>：每 cfg 间隔（默认 4 tick）才扫一次。
 *       不敢设太长 —— {@code isEntityApplicable} 同时拦 {@code EntityPlayerMP}
 *       与 {@code EntityEnderPearl}，末影珍珠速度快，间隔过大会漏掉。
 *       4 tick 内珍珠位移约 4 格，而 AABB 有 1 格厚度加上珍珠自身碰撞箱，
 *       配合原版的推离逻辑仍能拦住绝大多数情况。</li>
 *   <li><b>查询类收窄</b>：{@code Entity} → {@code EntityPlayer}？
 *       **不能这么做** —— 会漏掉末影珍珠。改为保持 {@code Entity} 但
 *       这一项不动，靠门禁降频即可。</li>
 *   <li><b>Availability 结果缓存</b>：按玩家 UUID 缓存 20 tick。
 *       边界墙上每个方块都对同一个玩家跑一遍 17 分支检查是纯重复劳动 ——
 *       100 格墙 = 同一玩家同一刻被检查 100 次。缓存后降到 1 次/20 tick。</li>
 * </ol>
 *
 * <h3>为什么 Availability 缓存放在这个混入而不是 Availability 类上</h3>
 * {@code Availability.isAvailable} 还被商人、对话、任务、配方等大量路径调用，
 * 那些是低频且要求实时的。只在 Border 这个高频重复场景做缓存，影响面精确。
 *
 * <h3>可撤回</h3>
 * cfg 的 {@code borderTickInterval}，设 1 即恢复原版每 tick 行为。
 *
 * <h3>服务端安全</h3>
 * {@code func_73660_a} offset 7 已有客户端 return。本混入注册 common 侧，
 * 只用 MC 实体与 noppes 数据类型，无客户端引用。
 */
@Mixin(value = TileBorder.class, remap = false)
public class MixinTileBorderThrottle {

    @Unique private int cnpcplus$tick;

    /** 按玩家缓存 Availability 结果，避免整面墙对同一玩家重复检查。 */
    @Unique private final WeakHashMap<UUID, long[]> cnpcplus$availCache =
            new WeakHashMap<UUID, long[]>();

    /** 缓存有效期（tick）。20 = 1 秒，足够覆盖一次穿越尝试。 */
    private static final int AVAIL_CACHE_TICKS = 20;

    /**
     * tick 门禁。原版每 tick 无条件扫描，这里降到每 N tick。
     */
    @Inject(method = "func_73660_a", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private void cnpcplus$throttle(CallbackInfo ci) {
        int interval = CnpcPlusConfig.getBorderTickInterval();
        if (interval <= 1) return;
        if (++this.cnpcplus$tick < interval) {
            ci.cancel();
            return;
        }
        this.cnpcplus$tick = 0;
    }

    /**
     * Availability 结果按玩家缓存。
     *
     * 原版在 offset 155（末影珍珠分支）与 183（玩家分支）各调一次，
     * 两处都拦。{@code require = 1} 容忍版本差异。
     */
    @Redirect(method = "func_73660_a",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/controllers/data/Availability;isAvailable(Lnet/minecraft/entity/player/EntityPlayer;)Z"),
            remap = false, require = 1)
    private boolean cnpcplus$cachedAvailability(Availability availability, EntityPlayer player) {
        // availability 由构造器 new 出来（offset 5-12），实践中非 null；
        // player 由原版从实体列表取出后 checkcast，也非 null。
        // 但仍做防御：任一为 null 就走原版路径（null availability 时直接放行，
        // 与原版对 null 调用会 NPE 相比更安全，且不改变正常路径行为）。
        // isAvailable 有多个重载，传字面 null 会歧义，所以 player 为 null 时
        // 直接放行而不调用（原版这条路径本来也走不到）。
        if (availability == null || player == null) return true;
        TileBorder self = (TileBorder) (Object) this;
        long now = self.getWorld() == null ? 0L : self.getWorld().getTotalWorldTime();

        UUID id = player.getUniqueID();
        long[] entry = this.cnpcplus$availCache.get(id);
        if (entry != null && now - entry[0] < AVAIL_CACHE_TICKS) {
            return entry[1] != 0L;
        }
        boolean allowed = availability.isAvailable(player);
        this.cnpcplus$availCache.put(id, new long[]{now, allowed ? 1L : 0L});
        return allowed;
    }
}
