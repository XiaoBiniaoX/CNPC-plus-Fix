package bin.cnpcplus.perf.client;

/**
 * 优化 #14 的类型桥：暴露 {@code EntityRenderer.shadowRadius} 的写入。
 *
 * <h2>为什么需要它</h2>
 * {@code MixinRenderNPCInterfaceShadow} 要在 {@code @Redirect} 处理函数里给
 * {@code shadowRadius} 赋值。javap 实证那个字段是
 * <pre>protected float shadowRadius;   // 声明在 net.minecraft.client.renderer.entity.EntityRenderer</pre>
 *
 * <p>{@code protected} 的含义是「同包，或子类内部」。而我们的 mixin 类
 * {@code bin.cnpcplus.mixin.perf.MixinRenderNPCInterfaceShadow}：
 * <ul>
 *   <li>不在 {@code net.minecraft.client.renderer.entity} 包里</li>
 *   <li>编译期也<b>不是</b> {@code EntityRenderer} 的子类
 *       —— 它只是一个普通类，「被合并进 {@code RenderNPCInterface}」是<b>运行期</b>的事</li>
 * </ul>
 * 所以直接写 {@code self.shadowRadius = ...} 编译不过：
 * <pre>错误: 找不到符号  符号: 变量 shadowRadius  位置: 类型为EntityRenderer&lt;?&gt;的变量 self</pre>
 *
 * <p>{@code @Shadow} 也不行 —— 那个字段声明在<b>父类</b>上，
 * 而 Mixin 的 {@code @Shadow} 只在目标类本身查找成员。
 * 这个坑本项目已经踩过四次（{@code JobFarmer.npc}、{@code LayerNpcElytra.npc}、
 * {@code Model2DRenderer} 的两个 texSize），不再重复。
 *
 * <h2>解法：接口注入</h2>
 * 用 {@code @Accessor} 把这个字段的 setter 合成到 {@code EntityRenderer} 上，
 * 然后通过本接口调用。这是 Mixin 处理「跨包访问 protected 继承成员」的标准做法：
 * 快（普通接口虚调用，JIT 能内联）、编译期类型安全、不用反射。
 *
 * <h2>为什么放在 {@code perf.client} 而不是 {@code mixin} 包</h2>
 * {@code cnpcplus.mixins.json} 的 {@code "package": "bin.cnpcplus.mixin"}
 * 会把整个包登记为 mixin 专属包，包内类<b>不可被外部直接引用</b>
 * （只看包名，不看有没有 {@code @Mixin} 注解）。
 * 这条规则已经让我在实机测试中被 MorePlayerModels 刷屏过一次，
 * 详见 {@link ModelPartWrapperRaw} 的类注释。
 *
 * <p>注意本接口<b>自身</b>需要被登记为 mixin（因为它带 {@code @Mixin} 注解，
 * 是一个 accessor mixin），所以它的 {@code @Mixin} 声明写在
 * {@code bin.cnpcplus.mixin.perf.EntityRendererShadowAccess} 里，
 * 本文件只是它的<b>父接口</b>，承载纯粹的方法声明供外部引用。
 *
 * <p>这个分层（纯接口在包外 + accessor mixin 在包内）是绕开上面那条包规则的标准姿势。
 */
public interface ShadowRadiusAccess {

    /** 写入 {@code EntityRenderer.shadowRadius}。 */
    void cnpcplus$setShadowRadius(float value);
}
