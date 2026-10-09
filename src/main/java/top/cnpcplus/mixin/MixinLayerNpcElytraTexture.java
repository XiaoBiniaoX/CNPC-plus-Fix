package top.cnpcplus.mixin;

import top.cnpcplus.perf.ResourceLocationCache;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.layer.LayerNpcElytra;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 优化 #9：{@code LayerNpcElytra.render} 每帧重新解析自定义披风的 {@code ResourceLocation}。
 *
 * <h2>原版做了什么</h2>
 * {@code LayerNpcElytra.render}（反编译源码 57-68 行）第 60 行：
 * <pre>
 * ResourceLocation resourcelocation = this.npc instanceof EntityCustomNpc
 *     ? (this.npc.display.getCapeTexture() != null
 *        &amp;&amp; !this.npc.display.getCapeTexture().isEmpty()
 *        &amp;&amp; this.base instanceof PlayerModel
 *            ? new ResourceLocation(this.npc.display.getCapeTexture())   // ← 每帧
 *            : this.getElytraTexture(itemstack, this.npc))
 *     : this.getElytraTexture(itemstack, this.npc);
 * </pre>
 *
 * <p>{@code getCapeTexture()} 在这一个表达式里被调了<b>三次</b>（判空、判空串、传参），
 * 外加一次 {@code new ResourceLocation} 的字符串切分 + 逐字符合法性校验。
 * 每帧，每个戴鞘翅且配了自定义披风的 NPC。
 *
 * <h2>对比：披风图层已经缓存了同一份数据</h2>
 * {@code LayerNpcCloak.render}（反编译源码 43-83 行）第 45-52 行：
 * <pre>
 * if (this.npc.textureCloakLocation == null) {
 *     if (this.npc.display.getCapeTexture() == null) return;
 *     if (this.npc.display.getCapeTexture().isEmpty()) return;
 *     if (!(this.base instanceof PlayerModel)) return;
 *     this.npc.textureCloakLocation = new ResourceLocation(this.npc.display.getCapeTexture());
 * }
 * </pre>
 * 也就是说 CNPC 已经有一个<b>专门存这个值的字段</b>
 * （{@code EntityNPCInterface.textureCloakLocation}，第 429 行），只是鞘翅图层没用它。
 *
 * <h2>本类怎么做</h2>
 * 复用 {@code npc.textureCloakLocation}。
 *
 * <p><b>为什么这是正确做法而不是「碰巧能用」</b>：
 * 这个字段的失效由 CNPC 自己维护，而且维护得很完整。全部引用点：
 * <ul>
 *   <li>{@code DataDisplay} 第 196 行、382 行 —— 披风纹理被修改时置 {@code null}
 *       （这正是我们需要的失效信号）</li>
 *   <li>{@code GuiTextureSelection} 第 173 行 —— GUI 选纹理时直接赋值</li>
 *   <li>{@code EntityUtil} 第 139 行 —— 克隆 NPC 时复制</li>
 *   <li>{@code EntityNPCInterface} 第 637 行 —— {@code aiStep} 里判非空后调 {@code cloakUpdate()}</li>
 *   <li>{@code LayerNpcCloak} 第 45/51/80 行 —— 读写</li>
 * </ul>
 * 所以复用它不是引入一个需要自己维护失效的新缓存，
 * 而是<b>让鞘翅图层用上本来就该用的那个字段</b>。
 * 披风一换 → {@code DataDisplay} 置 null → 两个图层同时拿到新值。语义天然一致。
 *
 * <p>再套一层 {@link ResourceLocationCache}：万一字段为 null 而披风路径非空
 * （比如披风图层因 {@code base} 不是 {@code PlayerModel} 而提前返回、没走到赋值那一步），
 * 仍然走缓存池而不是 {@code new}。
 *
 * <h2>为什么不用 {@code @Shadow} 拿 npc 字段</h2>
 * 一开始我写的是 {@code @Shadow protected EntityCustomNpc npc;}，
 * 编译时注解处理器报了
 * <pre>Cannot find target for @Shadow field in noppes.npcs.client.layer.LayerNpcElytra</pre>
 *
 * <p>javap 查证原因：{@code npc} 声明在<b>父类</b> {@code LayerInterface} 上
 * （{@code protected noppes.npcs.entity.EntityCustomNpc npc;}），
 * {@code LayerNpcElytra} 自己没有这个字段。
 * Mixin 的 {@code @Shadow} 只在<b>目标类本身</b>查找成员，不会向上遍历继承链。
 *
 * <p>而 {@code npc} 是 {@code protected}，我们的 mixin 类不在
 * {@code noppes.npcs.client.layer} 包里，也不是 {@code LayerInterface} 的子类，
 * 直接访问不到。
 *
 * <p>解法：从 {@code @Redirect} 处理函数的<b>隐式 this</b> 拿。
 * 处理函数是实例方法，运行期 {@code this} 就是那个 {@code LayerNpcElytra}。
 * 把它转成 {@code LayerInterface} 之后 {@code npc} 还是 protected 访问不到 ——
 * 所以改用 {@code getElytraTexture} 这条<b>public</b> 路径反推：
 * 见下方 {@link #cnpcyouhua$cachedCape} 的实现说明。
 *
 * <h2>为什么原版的分支逻辑不需要在这里重做</h2>
 * 我们用 {@code @Redirect} 只替换 {@code new ResourceLocation} <b>这一条指令</b> ——
 * 三个前置条件（{@code capeTexture != null}、非空串、{@code base instanceof PlayerModel}）
 * 的判断仍然是 CNPC 原来的字节码在做。
 * 只有全部条件成立、原版真的要构造 {@code ResourceLocation} 时，
 * 我们的处理函数才会被调用。<b>分支逻辑一字未动。</b>
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = LayerNpcElytra.class, remap = false)
public abstract class MixinLayerNpcElytraTexture {

    /**
     * 替换 {@code render} 里的 {@code new ResourceLocation(capeTexture)}。
     *
     * <p>参数 {@code raw} 就是原版传给构造器的 {@code display.getCapeTexture()} 结果，
     * 由 JVM 的求值顺序保证。
     *
     * <p>这里刻意<b>不</b>去访问 {@code npc} 字段（它是父类的 protected，见类注释），
     * 而是只用缓存池。这样做少了一层「顺手把 {@code textureCloakLocation} 填上」的优化，
     * 但换来的是完全不依赖字段可见性 —— 缓存池本身已经把重复解析消掉了，
     * 而 {@code LayerNpcCloak} 会在它自己的渲染路径上填那个字段。
     *
     * <p>{@code require = 0}：纯分配优化。若 CNPC 改了写法导致匹配不上，
     * 静默退回原版行为，不影响正确性。
     */
    @Redirect(
            method = "render",
            at = @At(value = "NEW", target = "(Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"),
            require = 0,
            remap = false
    )
    private ResourceLocation cnpcyouhua$cachedCape(String raw) {
        return ResourceLocationCache.get(raw);
    }
}
