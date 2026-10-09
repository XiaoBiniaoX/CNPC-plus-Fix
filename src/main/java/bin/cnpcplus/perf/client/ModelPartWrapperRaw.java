package bin.cnpcplus.perf.client;

/**
 * 优化 #10 的类型桥：让 {@code MixinLayerPartsRotate} 能调用
 * {@code MixinModelPartWrapperRaw} 注入到 {@code ModelPartWrapper} 上的免分配方法。
 *
 * <h2>为什么需要这个接口</h2>
 * Mixin 的 {@code @Unique} 成员会被合成到<b>目标类</b>里
 * （这里是 {@code noppes.npcs.client.parts.ModelPartWrapper}）。
 * 但在编译期，我们源码看到的 {@code ModelPartWrapper} 是 CNPC jar 里的原版类，
 * 上面并没有 {@code cnpcplus$setRotRaw} —— 直接调用编译不过。
 *
 * <p>标准解法是「接口注入」：
 * <ol>
 *   <li>定义一个我们自己的接口，声明要注入的方法。</li>
 *   <li>让 mixin 类 {@code implements} 它 —— Mixin 会把这个接口
 *       <b>加到目标类的实现列表</b>上。</li>
 *   <li>调用方把对象强转成这个接口再调用。运行期目标类确实实现了它，转换成功。</li>
 * </ol>
 * 这比反射快（普通 interface 虚调用，JIT 能内联），
 * 也比 {@code @Invoker} 干净（后者是给已存在的私有方法用的，这里的方法是新加的）。
 *
 * <h2>为什么这个接口<b>不能</b>放在 {@code bin.cnpcplus.mixin.perf} 包里</h2>
 * 这是实机跑出来的一个真实 bug，值得记下来。
 *
 * <p>最初我把它放在 {@code bin.cnpcplus.mixin.perf} 下，结果游戏里一装
 * MorePlayerModels 就被刷屏（每个部件 JSON 一条）：
 * <pre>
 * Error in moreplayermodels:parts/head/ears_bunny.json -
 *   bin.cnpcplus.mixin.perf.ModelPartWrapperRaw is in a defined mixin package
 *   bin.cnpcplus.mixin.* owned by cnpcplus.mixins.json
 *   and cannot be referenced directly
 * </pre>
 *
 * <p><b>原因</b>：{@code cnpcplus.mixins.json} 里的
 * {@code "package": "bin.cnpcplus.mixin"} 会让 Mixin 把<b>整个包</b>登记为
 * 「mixin 专属包」。Mixin 的类加载器对这个包有一条硬规则：
 * 包内的类<b>只能</b>作为 mixin 被应用到目标类上，
 * 任何从外部对它的<b>直接引用</b>都会被拒绝并抛异常。
 *
 * <p>这条规则是有道理的 —— mixin 类在运行期是「模板」，它的成员被拷走之后
 * 那个类本身处于半失效状态，直接引用它几乎总是 bug。
 * 但它对我这个「纯接口、没有任何 {@code @Mixin} 注解」的类也一样生效：
 * Mixin 只看包名，不看类上有没有注解。
 *
 * <p>触发路径：{@code MpmPartReader.readPart}（反编译源码 145-157 行）
 * 加载每个部件 JSON 时会构造 {@code MpmPartSimple}/{@code MpmPartBedrock}，
 * 那会触发 {@code ModelPartWrapper} 的类初始化 ——
 * 而它此时已被注入 {@code implements ModelPartWrapperRaw}，
 * 于是类加载器解析这个接口引用，撞上规则，抛异常。
 * {@code MpmPartReader} 第 155-157 行的 {@code catch (Throwable)} 把它转成
 * 「Error in xxx.json」的聊天消息，所以表现成刷屏而不是崩游戏。
 *
 * <p><b>教训</b>：mixin 包里只放真正的 mixin 类。
 * 接口桥、共享状态、工具类一律放在 mixin 包<b>之外</b>。
 * 这和 {@code HighlightVersion} / {@code MassBlockScanState} 挪出 mixin 包
 * 是同一个原则的两个侧面（一个是「静态成员被拷贝」，一个是「包内类不可外部引用」）。
 *
 * <h2>命名前缀</h2>
 * 两个方法都带 {@code cnpcplus$} 前缀。这是 Mixin 的惯例：
 * 合成进目标类的成员名字必须<b>不可能</b>与 CNPC 自身或其他 mod 注入的成员撞名，
 * 撞名的后果是 Mixin 应用失败并抛出难以定位的错误。
 */
public interface ModelPartWrapperRaw {

    /** 免分配地设置位置。等价于 {@code setPos(new NopVector3f(x, y, z))}。 */
    void cnpcplus$setPosRaw(float x, float y, float z);

    /** 免分配地设置旋转。等价于 {@code setRot(new NopVector3f(x, y, z))}。 */
    void cnpcplus$setRotRaw(float x, float y, float z);
}
