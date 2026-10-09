package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.perf.ResourceLocationCache;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import noppes.npcs.client.overlay.OverlayTexturedRectComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 优化 #7：{@code OverlayTexturedRectComponent.render} 每帧重复解析 {@code ResourceLocation}。
 *
 * <h2>原版做了什么</h2>
 * {@code OverlayTexturedRectComponent.render}（反编译源码 48-72 行）第 60 行：
 * <pre>
 * public void render(GuiGraphics graphics, int linkSide) {
 *     ...
 *     if (Objects.equals(this.texture, "")) {
 *         this.renderGradientRect(...);
 *     } else {
 *         ResourceLocation resLoc = new ResourceLocation(this.texture);   // ← 每帧
 *         ...
 *     }
 * }
 * </pre>
 *
 * <h2>为什么这是无效开销</h2>
 * {@code this.texture} 是 {@code private final String}（第 26 行），构造后永不改变。
 * 而 {@code new ResourceLocation(String)} 在 1.20.1 里要做：
 * <ul>
 *   <li>{@code split(':')} —— 一次正则-free 的字符串切分，产生一个 {@code String[]} 和一到两个 {@code String}</li>
 *   <li>{@code assertValidNamespace} / {@code assertValidPath} ——
 *       对命名空间和路径<b>逐字符</b>校验合法性</li>
 *   <li>失败时还要构造 {@code ResourceLocationException}</li>
 * </ul>
 *
 * <p>渲染频率：{@code OverlayEventHandler.onRenderOverlay}（每帧）
 * → {@code OverlayController.renderOverlays}（遍历全部 overlay）
 * → {@code Overlay.render}（遍历该 overlay 的全部 component）
 * → 本方法。脚本做的 HUD 界面轻松有几十个纹理组件，
 * 于是每帧几十次完全相同的字符串解析 + 几十个短命对象。
 *
 * <h2>本类怎么做</h2>
 * {@code @Redirect} 拦下 {@code new ResourceLocation(String)}，
 * 换成 {@link ResourceLocationCache#get(String)} 的一次 {@code HashMap} 查找。
 *
 * <h2>为什么选缓存池而不是「构造时解析成字段」</h2>
 * 两种做法我都考虑过：
 * <ul>
 *   <li><b>构造时解析</b>（{@code @Inject} 到 {@code <init>} + {@code @Unique} 字段）：
 *       理论最优，但需要处理「构造时纹理路径非法怎么办」——
 *       原版是在 render 时抛异常（被上层吞掉，那一帧不画），
 *       改成构造时抛会让整个 overlay 创建失败，<b>行为变了</b>。</li>
 *   <li><b>缓存池</b>（本方案）：异常抛出的时机与原版完全一致
 *       （仍在 render 里，仍每帧抛），只是合法路径变成一次哈希查找。
 *       而且缓存是全局的，多个 component 用同一张纹理时共享同一个对象。</li>
 * </ul>
 * 选后者：更简单，且行为等价性更容易论证。
 *
 * <p>另外这个缓存池同时服务优化 #9（鞘翅披风），一份设施两处用，不额外增加复杂度。
 *
 * <h2>为什么用 {@code @Redirect} 而不是 {@code @Overwrite}</h2>
 * {@code render} 方法体里还有对齐计算、九宫格定位、渐变矩形分支等逻辑，
 * 那些都不该动。{@code @Redirect} 只替换<b>一条指令</b>，
 * 精确、最小、且不妨碍其他附属在同一方法上注入。
 *
 * <h2>1.21.1 现状（移植适配）</h2>
 * CNPC 1.21.1.20251230 的 {@code OverlayTexturedRectComponent.render}
 * 已改用 {@code ResourceLocation.tryParse(String)}（1.21.1 字节码实证：render 内
 * 唯一一次 {@code INVOKESTATIC tryParse}，偏移 97），不再执行
 * {@code new ResourceLocation(String)}。本类按此适配：{@code @At} 改拦
 * {@code tryParse}，处理器走 {@link ResourceLocationCache#getOrNull(String)}，
 * 保持「非法路径返回 null（不抛）」的原版语义不变。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = OverlayTexturedRectComponent.class, remap = false)
public abstract class MixinOverlayTexturedRect {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/resources/ResourceLocation;tryParse(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            remap = false
    )
    private ResourceLocation cnpcplus$cachedTexture(String raw) {
        return ResourceLocationCache.getOrNull(raw);
    }
}
