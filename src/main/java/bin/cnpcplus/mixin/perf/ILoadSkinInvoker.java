package bin.cnpcplus.mixin.perf;

import net.minecraft.util.ResourceLocation;
import noppes.npcs.client.renderer.RenderNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.io.File;

/**
 * 让 {@link MixinRenderNPCSkinCache} 能调回 {@code RenderNPCInterface} 的
 * 私有方法 {@code loadSkin}。
 *
 * <h3>为什么需要单独一个 @Invoker 混入</h3>
 * {@code MixinRenderNPCSkinCache} 用 {@code @Redirect} 拦下了 {@code loadSkin}
 * 的调用，处理完后必须**调回原方法**。但 {@code loadSkin} 是 private，
 * 混入类里不能直接调（编译不过），也不能用 {@code @Shadow}
 * （{@code @Shadow} 声明的方法体在混入类里是抽象的，用于「假装有这个成员」，
 * 而这里需要真正 invoke 到目标类的实现）。
 *
 * {@code @Invoker} 正是为此设计：它在目标类上合成一个可访问的桥接方法。
 *
 * <h3>为什么不合并进同一个混入类</h3>
 * 一个 {@code @Mixin} 类里同时用 {@code @Redirect} 和 {@code @Invoker}
 * 是允许的，但 {@code @Invoker} 要求宿主接口/类的方法是 abstract，
 * 而 {@code @Redirect} 的 handler 必须有方法体 —— 混在一个具体类里会让
 * 类既要 abstract 又要有实现，容易踩「非法混入类形态」的坑。
 * 拆成一个纯 accessor 混入更干净，这也是本项目既有的做法
 * （阶段 4 的 {@code ContainerAddSlotInvoker}、{@code GuiScreenSizeAccessor}）。
 *
 * <h3>纯客户端</h3>
 * 注册在 client 侧，与 {@code MixinRenderNPCSkinCache} 一致。
 */
@Mixin(value = RenderNPCInterface.class, remap = false)
public interface ILoadSkinInvoker {

    @Invoker("loadSkin")
    void cnpcplus$loadSkin(File cacheFile, ResourceLocation location, String url);
}
