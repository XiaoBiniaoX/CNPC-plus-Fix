package top.cnpcplus.mixin;

import noppes.npcs.shared.client.util.TrueTypeFont;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.Map;

/**
 * 修复任务/对话文本编写界面「输入的文字间断性变成乱码」。
 *
 * <p>根因在 CNPC 自研字体渲染器 {@code TrueTypeFont} 的 512×512 字形图集分配器
 * （{@code getOrCreateGlyph}）。源码与字节码双实证的关键片段：
 * <pre>
 * g.width  = Math.max(metrics.charWidth(c), 1);   // 偏移 55-66
 * g.height = Math.max(metrics.getHeight(), 1);    // 偏移 69-79
 * if (cache.x + g.width &gt;= 512) {                 // 偏移 82-94
 *     cache.x = 0;
 *     cache.y += this.lineHeight + 1;             // 偏移 104-114  ← 用的是「更新前」的 lineHeight
 *     ...
 * }
 * g.x = cache.x;
 * g.y = cache.y;
 * cache.x += g.width + 3;
 * this.lineHeight = Math.max(this.lineHeight, g.height);   // ← 更新发生在分配之后
 * </pre>
 *
 * <p><b>真正的缺陷：行高滞后更新。</b>换行时用的 {@code lineHeight} 是上一批字形算出来的，
 * 而每个字符可能来自不同的回退字体（{@code getFontForChar} 会依次尝试主字体、已用字体、
 * Arial Unicode MS、再遍历系统全部字体），高度并不一致。于是「按偏小的行高换行 →
 * 之后才把 lineHeight 调大」，下一行的纵向偏移不足，<b>新行的字形被画进上一行字形的
 * 像素区域</b>，两个字叠在一起就成了乱码。配合 {@code glyphcache} 没有任何失效机制
 * （缓存的 Glyph 持有图集 UV 坐标，图集被后来的字形覆盖后仍返回旧坐标），
 * 表现就是「同一个字有时正常有时花，花掉之后本次会话一直花」。
 *
 * <p><b>已排除的两个可疑点</b>（避免后人重复怀疑）：
 * <ul>
 *   <li>「换页后没重置游标」不成立：字节码偏移 127-133 确认 {@code cache.full = true}
 *       之后 {@code getCurrentTexture()} 返回的是**新建的** TextureCache，
 *       其 {@code x}/{@code y} 是 Java 默认的 0，随后 {@code g.x/g.y} 取到的就是 0，正确。</li>
 *   <li>不是并发竞态：{@code TrueTypeFont} 全部在渲染线程调用，
 *       {@code PacketConfigFont} 也用 {@code Minecraft.execute} 正确切回主线程。
 *       这是状态累积型溢出，不是线程问题。</li>
 * </ul>
 *
 * <p><b>为什么只在编写界面出现</b>：{@code GuiTextArea} 用的是
 * {@code private static TrueTypeFont font = new TrueTypeFont(new Font("Arial Unicode MS", ...))}
 * —— static、硬编码字体、<b>不受 {@code FontType} 配置控制</b>（即使配置设成 minecraft
 * 也照走自研渲染路径）、{@code PacketConfigFont} 重建字体时也碰不到它。
 * 它的字符集远大于玩家侧用的 {@code ClientProxy.Font}（后者 default 是 OpenSans，无 CJK），
 * 只有它会真正把图集填满并触发上述缺陷。这正好解释了用户观察到的
 * 「config 改成 default 并重置就不会乱码」。
 *
 * <p>补充实证：本机 {@code InstalledFontCollection} 查询确认 <b>Arial Unicode MS 并未安装</b>
 * （只有 Arial / Arial Black / Arial Narrow）。{@code new Font("Arial Unicode MS", ...)}
 * 在缺失时不抛异常而是静默退化为逻辑字体 Dialog，回退链更长、字形高度更杂，
 * 使行高不一致的问题更容易暴露。
 *
 * <p><b>修法</b>：在分配之前把 {@code lineHeight} 抬到足以容纳即将写入的字形，
 * 这样原版那句 {@code cache.y += this.lineHeight + 1} 用到的就是正确的行高，行与行不再重叠。
 * {@code lineHeight} 只增不减，所以它始终 ≥ 已见过的所有字形高度，换行永远够用。
 *
 * <p>刻意<b>不</b>用 {@code @Overwrite}：那会与任何同样改 CNPC 字体的模组硬冲突。
 * 这里只在 HEAD 预先修正一个字段，原方法体（度量、drawString、纹理上传）完全保留。
 *
 * <p>纯客户端：{@code TrueTypeFont} 直接引用 GL 与 AWT，必须登记在 mixins 的 client 段。
 */
@Mixin(value = TrueTypeFont.class, remap = false)
public abstract class MixinTrueTypeFontAtlas {

    // 注意：TrueTypeFont$Glyph 与 $TextureCache 都是包级私有类（javap 确认无 public 修饰），
    // 从 top.cnpcplus.mixin 包引用不到，所以这里一律用通配/原始类型，绝不写出它们的类名。
    @Shadow(remap = false)
    private Map<Character, ?> glyphcache;

    @Shadow(remap = false)
    private Graphics2D globalG;

    @Shadow(remap = false)
    private int lineHeight;

    /**
     * CNPC 自己的私有方法。这里 @Shadow 它是安全的：本 mixin 是 {@code remap = false}，
     * 且目标是 CNPC 自有类的自有方法 —— 我们编译期与运行期用的是同一个 CNPC jar，
     * 名字不经过 SRG 重映射，不存在「@Shadow 不进 refmap 导致生产找不到目标」的问题
     * （那条教训只适用于 Minecraft 自身的成员）。
     */
    @Shadow(remap = false)
    private Font getFontForChar(char c) {
        throw new AssertionError();
    }

    @Inject(method = "getOrCreateGlyph", at = @At("HEAD"), remap = false)
    private void cnpcplus$fixRowHeight(char c, CallbackInfoReturnable<?> cir) {
        // 已缓存的字形不会重新分配，无需干预。
        if (this.glyphcache != null && this.glyphcache.containsKey(c)) return;
        if (this.globalG == null) return;

        // 用与原版完全一致的方式取本字形的真实度量（可能来自回退字体）。
        Font target = this.getFontForChar(c);
        if (target == null) return;
        FontMetrics metrics = this.globalG.getFontMetrics(target);
        if (metrics == null) return;

        int h = Math.max(metrics.getHeight(), 1);
        // 核心修复：先抬行高，再让原版去算换行位置。
        // 原版是分配完坐标才更新 lineHeight，于是按偏小的行高换行、新行压进上一行像素区。
        if (h > this.lineHeight) {
            this.lineHeight = h;
        }
    }
}
