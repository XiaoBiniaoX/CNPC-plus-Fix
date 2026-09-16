package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;

/**
 * 性能问题 15：URL 皮肤无落盘缓存，跨会话每次重新下载。
 *
 * <h3>先纠正三点（哈基彬的描述与字节码不符）</h3>
 * <ol>
 *   <li><b>「每次渲染都会新建 HTTP 线程」—— 不成立。</b>
 *       有三道去重闸门：
 *       <pre>
 *       RenderNPCInterface.getEntityTexture offset 4:  ifnonnull 293
 *           → textureLocation 非空即短路，不重算不下载
 *       RenderNPCInterface.loadSkin offset 11-17:      func_110581_b + ifnull → return
 *           → TextureManager 已注册该 ResourceLocation 则不建对象
 *       ImageDownloadAlt.func_110551_a offset 20-23:   getfield imageThread + ifnonnull 145
 *           → 每个实例的线程只建一次
 *       </pre>
 *       稳态是 <b>0 个线程</b>。</li>
 *   <li><b>「失败还会持续重试」—— 方向相反。</b>
 *       {@code ImageDownloadAlt$1.run()} 的 catch（offset 194-228）确实
 *       <b>没有任何 putfield</b>（只有 {@code Logger.error} 与 {@code disconnect}），
 *       非 2xx 路径（offset 96-104）也不写标记。
 *       但因为 {@code ImageDownloadAlt} 实例已经注册进 {@code TextureManager}，
 *       上面第二道闸门会拦住后续尝试 → <b>永久不再重试</b>，
 *       皮肤永久显示 default，直到手动改 URL。
 *       所以「断网时卡死」的机制不是重试风暴，而是首次下载的连接超时
 *       阻塞在下载线程上（不阻塞主线程）。</li>
 *   <li><b>「没有落盘缓存」—— 完全正确，且比描述更彻底。</b>
 *       {@code RenderNPCInterface.loadSkin} offset 274 <b>硬传 {@code aconst_null}</b>
 *       作为 cacheFile 参数，导致：
 *       <ul>
 *         <li>{@code func_110551_a} offset 30 的 {@code ifnull → 141} 必然成立，
 *             offset 64-99 的 {@code ImageIO.read(File)} 落盘读分支是<b>死代码</b>；</li>
 *         <li>{@code $1.run()} offset 115-139 的
 *             {@code FileUtils.copyInputStreamToFile} 写盘分支同样<b>不可达</b>。</li>
 *       </ul>
 *       CNPC 作者写好了整套落盘缓存却传了个 null 把它关掉了。</li>
 * </ol>
 *
 * <h3>修法：把那个 null 换成真实缓存文件（激活作者已写好的死代码）</h3>
 * {@code @ModifyArg} 改 {@code loadSkin} 调用点的第一个参数。
 * 一旦 cacheFile 非 null：
 * <ul>
 *   <li>{@code func_110551_a} offset 37-40 的 {@code File.isFile()} 命中时
 *       直接从磁盘读，<b>完全不发 HTTP 请求</b> —— 断网也能显示皮肤；</li>
 *   <li>下载成功时 {@code $1.run()} 自动写盘，下次启动直接命中。</li>
 * </ul>
 * 这是最小改动：不新增线程池、不改 {@code ImageDownloadAlt}（其 {@code cacheFile}
 * 是 final 无法在混入里改），只把参数从 null 换成路径。
 *
 * <h3>缓存路径与文件名</h3>
 * {@code .minecraft/cnpcplus_skincache/<md5>.png}。
 * 文件名直接用 {@code getEntityTexture} 已经算好的 MD5
 * （offset 141-235 的 MessageDigest + 16 次 String.format），
 * 从 ResourceLocation 的 path 里取即可，不重复计算。
 *
 * <h3>没做的部分</h3>
 * 「有限重试 3 次后放弃」与「共享线程池上限 4」需要改
 * {@code ImageDownloadAlt}（final 字段 + 匿名内部类 {@code $1}），
 * 而落盘缓存已经消除了绝大部分重复下载与断网问题 ——
 * 按 ponytail 的最小改动原则先只做这一步。
 *
 * <h3>可撤回</h3>
 * cfg 的 {@code urlSkinDiskCache}，默认开。关掉即恢复原版传 null 的行为。
 *
 * <h3>纯客户端</h3>
 * {@code RenderNPCInterface} 是渲染器。本混入注册 client 侧。
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public class MixinRenderNPCSkinCache {

    /**
     * 把 {@code loadSkin(null, location, url)} 的第一个参数换成真实缓存文件。
     *
     * {@code index = 0} 指定第一个参数（cacheFile）。
     * target 指向 noppes 自有的私有方法，无 SRG 映射，写原名。
     *
     * 注意 {@code loadSkin} 在该类里可能有多个调用点（皮肤 / 披风等），
     * 用 {@code require = 1} 容忍差异 —— 每个命中的调用点都会拿到缓存路径，
     * 这正是我们想要的。
     */
    @Redirect(method = "getEntityTexture",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/client/renderer/RenderNPCInterface;loadSkin(Ljava/io/File;Lnet/minecraft/util/ResourceLocation;Ljava/lang/String;)V"),
            remap = false, require = 1)
    private void cnpcplus$loadSkinWithCache(RenderNPCInterface<?> self, File cacheFile,
                                            ResourceLocation location, String url) {
        File target = cacheFile;
        if (CnpcPlusConfig.isUrlSkinDiskCache() && target == null) {
            target = cnpcplus$cacheFileFor(location);
        }
        // 用 accessor 调回原版私有方法（见 ILoadSkinInvoker）。
        ((ILoadSkinInvoker) self).cnpcplus$loadSkin(target, location, url);
    }

    /** 缓存文件路径。取不到游戏目录时返回 null，退回原版行为。 */
    private static File cnpcplus$cacheFileFor(ResourceLocation location) {
        try {
            File dir = new File(net.minecraft.client.Minecraft.getMinecraft().gameDir,
                    "cnpcplus_skincache");
            if (!dir.exists() && !dir.mkdirs()) return null;
            return new File(dir, cnpcplus$skinKey(location) + ".png");
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 把 ResourceLocation 转成安全的缓存文件名。
     *
     * 它的 path 形如 {@code skins/<md5>}（由 {@code getEntityTexture}
     * offset 141-267 的 MD5 + String.format 拼出），取末段即可，不重复计算。
     */
    private static String cnpcplus$skinKey(ResourceLocation loc) {
        if (loc == null) return "unknown";
        // 1.12.2 的 MCP 名是 getPath()，不是 getResourcePath()（那是更早版本的名字）。
        String path = loc.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash >= 0 ? path.substring(slash + 1) : path;
        // 只保留文件名安全字符，防止路径穿越。
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
