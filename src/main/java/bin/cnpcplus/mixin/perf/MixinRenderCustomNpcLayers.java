package bin.cnpcplus.mixin.perf;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import noppes.npcs.client.layer.LayerGlow;
import noppes.npcs.client.renderer.RenderCustomNpc;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 优化 #8：替代实体模型 NPC 每帧 {@code new LayerGlow}。
 *
 * <h2>原版做了什么</h2>
 * {@code RenderCustomNpc.render}（反编译源码 167-227 行）在 NPC 使用
 * 「替代实体模型」（{@code modelData.getEntity(npc) != null}，
 * 也就是把 NPC 显示成僧尸/村民/宝可梦等别的实体）时，走 192-207 行：
 * <pre>
 * if (render instanceof LivingEntityRenderer) {
 *     this.renderEntity = (LivingEntityRenderer) render;
 *     this.otherModel = this.renderEntity.getModel();
 *     ...
 *     this.model = this.renderModel;
 *     this.layers.clear();
 *     this.layers.add(this.renderLayer);
 *     this.layers.add(new LayerGlow(this));       // ← 第 201 行，每帧一个新对象
 *     ...
 * }
 * </pre>
 *
 * <h2>为什么可以复用同一个实例</h2>
 * {@code LayerGlow}（反编译源码全文 35-53 行）是<b>完全无状态</b>的：
 * <pre>
 * public class LayerGlow&lt;T extends EntityNPCInterface, M extends EntityModel&lt;T&gt;&gt; extends RenderLayer&lt;T, M&gt; {
 *     public LayerGlow(RenderCustomNpc npcRenderer) { super(npcRenderer); }
 *
 *     public void render(PoseStack ms, MultiBufferSource buf, int light, T npc, float ...) {
 *         if (npc.display.getOverlayTexture().isEmpty()) return;
 *         if (npc.textureGlowLocation == null)
 *             npc.textureGlowLocation = new ResourceLocation(npc.display.getOverlayTexture());
 *         VertexConsumer vc = npc.display.isOverlayGlowing() ? ... : ...;
 *         this.getParentModel().renderToBuffer(ms, vc, light, ...);
 *     }
 * }
 * </pre>
 * 除了父类的 {@code renderer} 引用（构造时传入，就是 {@code this}），
 * 它没有任何实例字段。渲染所需的一切都来自方法参数 {@code npc}
 * 和 {@code getParentModel()}（读的是同一个 renderer 的当前 model）。
 *
 * <p>所以「每帧新建」和「复用一个」在行为上完全等价 ——
 * 后者只是不再每帧扔掉一个对象。
 *
 * <h2>影响量级</h2>
 * 每个使用替代实体模型的 NPC，每帧一个 {@code LayerGlow}。
 * 一个有 50 个这类 NPC 的场景，60 FPS 下就是每秒 3000 个短命对象。
 * 单个对象很小（一个引用字段 + 对象头，约 16 字节），
 * 但这是纯粹白给 GC 的工作量。
 *
 * <h2>为什么不动 {@code layers.clear()} / {@code addAll(npclayers)}</h2>
 * 原方案里我提过用「状态机」跳过状态未变时的列表重建。实现时我改了主意，原因：
 *
 * <p>{@code RenderCustomNpc} 的图层列表在<b>一帧之内会被多次改写</b>，
 * 而且改写依赖的是「上一次渲染的是哪个 NPC」这种跨调用状态
 * （字段 {@code this.entity}、{@code this.renderEntity}）。
 * 同一个 {@code RenderCustomNpc} 实例被<b>所有</b> {@code EntityCustomNpc} 共用
 * （Minecraft 的 renderer 是按实体类型单例的），
 * 所以「上一帧的状态」并不代表「上一个 NPC 的状态」。
 *
 * <p>要正确地做状态机，得区分「同一 NPC 的连续帧」和「不同 NPC 的交替渲染」，
 * 而后者在一帧内就会发生（视野里同时有普通 NPC 和替代模型 NPC 时，
 * 列表会在两种配置间来回切换 —— 这是必需的，不是浪费）。
 *
 * <p>误判的代价是<b>图层错乱</b>：某个 NPC 少了披风、或者普通 NPC 意外带上了
 * 替代模型的图层。这是可见的视觉 bug，而收益只是省几次 {@code ArrayList.clear()}
 * （几个引用写，纳秒级）。风险收益比不成立，所以只做 {@code new LayerGlow} 这一项。
 *
 * <p>顺带记录一个发现（<b>刻意不修</b>）：{@code npclayers}（第 109 行
 * {@code Lists.newArrayList()}）在整个类里<b>从未被填充过</b> ——
 * 构造函数只调 {@code addLayer}（写的是父类的 {@code layers}）。
 * 于是 179-181 / 212-213 行的 {@code layers.clear(); layers.addAll(npclayers)}
 * 实际效果是<b>清空所有图层</b>。这是 CNPC 自己的 bug，
 * 修它会让原本消失的披风/发光/装备重新出现 —— 那是行为变更，不是性能优化，
 * 不在本 mod 的职责范围内。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class MixinRenderCustomNpcLayers {

    /**
     * 复用的发光图层实例。
     *
     * <p>每个 {@code RenderCustomNpc} 一个（不是全局静态），
     * 因为 {@code RenderLayer} 的父类持有 {@code renderer} 引用，必须与所属 renderer 对应。
     *
     * <p><b>类型必须写成裸 {@code LayerGlow}</b>（raw type）而不是
     * {@code LayerGlow<EntityCustomNpc, HumanoidModel<EntityCustomNpc>>}。
     * 泛型参数只存在于编译期，字节码里 {@code new LayerGlow} 的类型就是
     * {@code Lnoppes/npcs/client/layer/LayerGlow;}，而 Mixin 校验
     * {@code @Redirect} 处理函数签名时比的是<b>擦除后的字节码类型</b>。
     */
    @Unique
    @SuppressWarnings("rawtypes")
    private LayerGlow cnpcplus$sharedGlow;

    /**
     * <b>必须写完整描述符</b>。{@code RenderCustomNpc} 里有两个 {@code render} 重载
     * （javap 实证）：
     * <pre>
     * public void render(EntityCustomNpc,     float, float, PoseStack, MultiBufferSource, int)   ← 真实实现
     * public void render(EntityNPCInterface,  float, float, PoseStack, MultiBufferSource, int)   ← 泛型桥接
     * </pre>
     * 只写 {@code method = "render"} 会同时匹配两个，Mixin 会因为
     * 「桥接方法里没有 {@code new LayerGlow}」而报 Invalid descriptor / 注入失败。
     *
     * <p>另外构造函数里也有一处 {@code new LayerGlow}（偏移 130），
     * 那一处<b>不该动</b> —— 它是初始化时建的常驻图层，只执行一次。
     * 限定 {@code method} 到 render 的具体重载后，构造函数那处自然不会被匹配。
     */
    /**
     * <b>返回类型必须精确等于被构造的类型</b>，即 {@code LayerGlow} 而不是它的父类。
     *
     * <p>第一次实机测试时我写的是 {@code RenderLayer<?, ?>}（{@code LayerGlow} 的父类），
     * 结果 Mixin 在应用阶段直接拒绝：
     * <pre>
     * InvalidInjectionException: @Redirect factory method
     *   RenderCustomNpc::cnpcplus$reuseGlowLayer has an invalid signature.
     *   Found unexpected return type net.minecraft.client.renderer.entity.layers.RenderLayer,
     *   expected noppes.npcs.client.layer.LayerGlow.
     *   Handler signature: (LRenderCustomNpc;)LRenderLayer;
     *   Expected signature: (LRenderCustomNpc;)LLayerGlow;
     * </pre>
     *
     * <p>原因：{@code @Redirect} 到 {@code NEW} 时，Mixin 是把
     * {@code NEW + DUP + INVOKESPECIAL} 这三条指令整体换成一次
     * {@code INVOKESTATIC/INVOKEVIRTUAL} 到我们的处理函数。
     * 换完之后栈顶那个值会被原来的字节码<b>按原类型继续使用</b>
     * （这里是存进 {@code List<RenderLayer<T, M>>}，但 verifier 校验的是精确类型匹配）。
     * 所以 Mixin 要求处理函数的返回类型与构造器产出的类型<b>逐字节一致</b>，
     * 协变（返回父类）不被接受 —— 那会让替换后的字节码类型不匹配。
     *
     * <p>这是 {@code @Redirect NEW} 与 {@code @Redirect INVOKE} 的一个重要差别，
     * 后者对返回类型宽松得多。
     */
    @Redirect(
            method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "NEW", target = "(Lnoppes/npcs/client/renderer/RenderCustomNpc;)Lnoppes/npcs/client/layer/LayerGlow;"),
            remap = false
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private LayerGlow cnpcplus$reuseGlowLayer(RenderCustomNpc renderer) {
        if (this.cnpcplus$sharedGlow == null) {
            this.cnpcplus$sharedGlow = new LayerGlow(renderer);
        }
        return this.cnpcplus$sharedGlow;
    }
}
