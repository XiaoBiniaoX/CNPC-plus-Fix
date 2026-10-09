package top.cnpcplus.mixin;

import top.cnpcplus.perf.MarketCache;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 优化 #4 的注入点：把 {@code RoleTrader} 的市场文件读写换成 {@link MarketCache}。
 *
 * <h2>注入的两个静态方法</h2>
 * <ul>
 *   <li>{@code RoleTrader.load(RoleTrader, String)} —— 玩家每次右键商人都会走
 *       （{@code interact} 第 98 行），是真正的热路径。</li>
 *   <li>{@code RoleTrader.save(RoleTrader, String)} —— 只在管理员编辑后
 *       （{@code save(CompoundTag)} 第 64-66 行的 {@code toSave} 分支）
 *       和 {@code setMarket} 首次建档时走，频率极低。</li>
 * </ul>
 *
 * <h2>兼容商人多页：为什么必须调 role 自己的 readNBT / writeNBT</h2>
 * 同工作区的 {@code top.cnpcplus} 附属（商人多页）把分页数据<b>搭在
 * {@code RoleTrader.writeNBT}/{@code readNBT} 的车上</b>：
 * <pre>
 * MixinRoleTraderPages:
 *   &#64;Inject(method = "writeNBT", at = &#64;At("HEAD"))    → TraderPager.flushCurrent(role)
 *   &#64;Inject(method = "writeNBT", at = &#64;At("RETURN"))  → nbt.put("TraderPages", ...) 等
 *   &#64;Inject(method = "readNBT",  at = &#64;At("RETURN"))  → TraderPager.fromNBT(role, nbt)
 * </pre>
 *
 * <p>所以本类严守一条：
 * <b>读到的 NBT 一定交给 {@code role.readNBT(tag)}，
 * 写出的 NBT 一定来自 {@code role.writeNBT(new CompoundTag())}。</b>
 *
 * <p>结果是：
 * <ul>
 *   <li>{@code writeNBT} 被调用 → 多页附属的 HEAD 注入 flush 当前页，
 *       RETURN 注入把 {@code TraderPages}/{@code PageTitles}/{@code FullPages} 写进 NBT
 *       → 这份<b>含分页字段的完整 NBT</b> 进缓存、落盘。</li>
 *   <li>{@code readNBT} 被调用 → 多页附属的 RETURN 注入从 NBT 里读回分页
 *       → 分页状态完整恢复。</li>
 * </ul>
 * 缓存层对「哪些字段是谁写的」完全无感知，所以对任何同类附属都透明兼容。
 *
 * <h2>为什么用 {@code @Inject(HEAD, cancellable)} 而不是 {@code @Overwrite}</h2>
 * 一样的理由：不独占方法。如果将来有别的附属想在 {@code RoleTrader.load} 上做点什么
 * （比如加个市场访问日志），它挂在 HEAD/RETURN 的注入照常触发。
 *
 * <h2>行为等价性逐条核对</h2>
 * <table>
 *   <tr><th>原版行为</th><th>本类</th></tr>
 *   <tr><td>{@code load}：客户端直接返回</td><td>保留（{@code isClientSide} 检查）</td></tr>
 *   <tr><td>{@code load}：文件不存在则<b>不动</b> role 的现有库存</td>
 *       <td>保留（{@code MarketCache.load} 返回 null 时不调 readNBT）</td></tr>
 *   <tr><td>{@code load}：解析异常被吞，同样不动库存</td>
 *       <td>保留（{@code MarketCache.load} 内部 catch 后返回 null）</td></tr>
 *   <tr><td>{@code save}：名字为空则直接返回</td><td>保留</td></tr>
 *   <tr><td>{@code save}：写临时文件 → 删旧 → 改名</td>
 *       <td>保留（{@code MarketCache.store} 用同样的三步）</td></tr>
 *   <tr><td>{@code save}：全部异常被吞</td><td>保留</td></tr>
 * </table>
 */
@Mixin(value = RoleTrader.class, remap = false)
public abstract class MixinRoleTraderMarketCache {

    @Inject(method = "load(Lnoppes/npcs/roles/RoleTrader;Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcyouhua$cachedLoad(RoleTrader role, String name, CallbackInfo ci) {
        // 与原版 193-195 行一致：客户端不碰市场文件。
        EntityNPCInterface npc = role.npc;
        if (npc == null || npc.level() == null || npc.level().isClientSide) {
            ci.cancel();
            return;
        }
        if (name == null || name.isEmpty()) {
            // 原版没显式判空，但 getFile("") 会得到 ".json"，file.exists() 为 false → 直接返回。
            // 这里提前挡掉，语义相同且省一次文件系统调用。
            ci.cancel();
            return;
        }

        CompoundTag tag = MarketCache.load(name);
        if (tag != null) {
            // 关键：走 role.readNBT，让商人多页等附属的 RETURN 注入拿到完整 NBT。
            role.readNBT(tag);
        }
        // tag == null 时什么都不做，与原版「文件不存在 / 解析失败」的行为一致：
        // 保留 role 当前的库存内容，不清空。
        ci.cancel();
    }

    @Inject(method = "save(Lnoppes/npcs/roles/RoleTrader;Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void cnpcyouhua$cachedSave(RoleTrader role, String name, CallbackInfo ci) {
        // 与原版 175-177 行一致。
        if (name == null || name.isEmpty()) {
            ci.cancel();
            return;
        }
        // 关键：走 role.writeNBT，让商人多页的 HEAD（flush 当前页）
        // 与 RETURN（写入 TraderPages）注入都执行，产出的 NBT 含分页数据。
        MarketCache.store(name, role.writeNBT(new CompoundTag()));
        ci.cancel();
    }
}
