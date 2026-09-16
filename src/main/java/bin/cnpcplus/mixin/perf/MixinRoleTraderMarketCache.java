package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import bin.cnpcplus.trader.MarketFileCache;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.roles.RoleTrader;
import noppes.npcs.util.NBTJsonUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;

/**
 * 性能问题 6：Trader 每次交互同步读磁盘 + 手写 JSON 解析。
 *
 * <h3>根因（javap 字节码实证）</h3>
 * {@code RoleTrader.interact} offset 23 是无条件的
 * {@code invokestatic load(RoleTrader, String)}，前面没有任何缓存判断。
 * {@code load} 的字节码：
 * <pre>
 * 15: invokestatic  getFile                → 内部 3 次 File.exists() / mkdirs 检查
 * 20: invokevirtual java/io/File.exists    → 不存在则 return
 * 29: invokestatic  NBTJsonUtil.LoadFile   ← 磁盘读全文 + 自研递归 JSON 解析（要缓存这一步）
 * 32: invokevirtual readNBT                ← 54 槽位反序列化（必须照常执行）
 * </pre>
 * 全程服务端主线程同步。商人是热门职业，物品越多越卡。
 *
 * <h3>为什么只 Redirect LoadFile 而不是整体跳过 load（保住我们的翻页）</h3>
 * 哈基彬明确担心「我们的翻页会因为这个死掉」——**这个担心是对的**。
 * 我们自己的 {@code mixin/trader/MixinRoleTraderPages} 在 {@code readNBT} 的
 * **RETURN** 注入 {@code TraderPager.fromNBT(...)} 来重建分页数据（该文件 39-42 行）。
 *
 * 如果缓存命中就 cancel 掉整个 {@code load}，offset 32 的 {@code readNBT} 不执行
 * → {@code TraderPager.fromNBT} 永不被调用 → **分页数据不重建，翻页功能直接死**。
 *
 * 所以这里只把 offset 29 的 {@code LoadFile}（磁盘 IO + JSON 解析，真正的开销）
 * 换成缓存查询，offset 32 的 {@code readNBT} 保持原样执行。
 * 收益：省掉全文读 + 442 行字节码的递归解析器；代价：一次 NBT 拷贝，便宜得多。
 *
 * <h3>随时可撤回（哈基彬要求留接口）</h3>
 * cfg 的 {@code traderMarketCacheMode}：
 * <ul>
 *   <li>0 = 关闭 → {@code MarketFileCache.get} 恒返回 null，行为与原版**完全一致**</li>
 *   <li>1 = 时间戳 + 长度校验（默认，方案 A）</li>
 *   <li>2 = 纯内存不校验（方案 B，最快，但外部改文件不生效）</li>
 * </ul>
 * 出问题只需把 cfg 改 0，无需回退版本。
 *
 * <h3>服务端安全</h3>
 * {@code load} 开头 offset 7-13 已有 {@code field_72995_K} 客户端 return 门禁。
 * 本混入注册在 common 侧，只用 {@code java.io.File}、NBT 与 noppes 自有类型，
 * 无客户端引用。
 */
@Mixin(value = RoleTrader.class, remap = false)
public class MixinRoleTraderMarketCache {

    /**
     * 把 {@code load} 内的 {@code NBTJsonUtil.LoadFile(File)} 换成「先查缓存」。
     *
     * target 指向 noppes 自有的静态方法，无 SRG 映射，写原名。
     */
    @Redirect(method = "load",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/util/NBTJsonUtil;LoadFile(Ljava/io/File;)Lnet/minecraft/nbt/NBTTagCompound;"),
            remap = false, require = 1)
    private static NBTTagCompound cnpcplus$cachedLoadFile(File file) throws Exception {
        int mode = CnpcPlusConfig.getTraderMarketCacheMode();
        NBTTagCompound cached = MarketFileCache.get(file, mode);
        if (cached != null) {
            return cached;
        }
        // 未命中：照原样读盘解析，然后存缓存。
        NBTTagCompound loaded = NBTJsonUtil.LoadFile(file);
        // 只缓存有效结果。原版 load 的 offset 32 会直接把返回值传给 readNBT，
        // 而 readNBT offset 4-7 立刻解引用参数 —— null 会 NPE。
        // 原版靠 load 的 try/catch（Exception table 27→35 target 38）吞掉，
        // 但不依赖异常兜底：null 时原样返回，行为与原版一致，只是不进缓存。
        if (loaded != null) {
            MarketFileCache.put(file, loaded, mode);
        }
        return loaded;
    }

    /**
     * 市场保存后作废缓存，保证下次读取拿到新数据。
     *
     * 注意 {@code save} 先写 {@code <name>_new} 临时文件（offset 28），
     * 再删除并改名到真实文件（offset 52-60）。所以必须在 TAIL 用**真实文件名**
     * 作废，不能用临时名。这里重新调 {@code getFile(marketName)} 取真实路径。
     */
    @Inject(method = "save", at = @At("TAIL"), remap = false, require = 1)
    private static void cnpcplus$invalidateOnSave(RoleTrader role, String marketName, CallbackInfo ci) {
        if (marketName == null || marketName.isEmpty()) return;
        // getFile 是私有静态，这里用同样的路径规则自行拼装，避免 @Invoker 的额外开销。
        // 与 RoleTrader.getFile 保持一致：<worldSave>/markets/<name.toLowerCase()>.json
        try {
            File dir = new File(noppes.npcs.CustomNpcs.getWorldSaveDirectory(), "markets");
            MarketFileCache.invalidate(new File(dir, marketName.toLowerCase() + ".json"));
        } catch (Exception ignored) {
            // 取不到存档目录（世界正在卸载）时无所谓 —— 那种情况缓存也要被 clear。
        }
    }
}
