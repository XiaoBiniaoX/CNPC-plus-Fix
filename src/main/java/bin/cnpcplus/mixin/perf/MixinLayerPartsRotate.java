package bin.cnpcplus.mixin.perf;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import noppes.npcs.client.layer.LayerParts;
import noppes.npcs.client.parts.ModelPartWrapper;
import noppes.npcs.client.parts.MpmPartAbstractClient;
import noppes.npcs.client.parts.PartBehaviorType;
import bin.cnpcplus.perf.client.ModelPartWrapperRaw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 优化 #10 的第三块：消掉 {@code LayerParts.rotate} 里每帧的 8 个 {@code NopVector3f}。
 *
 * <h2>原版做了什么</h2>
 * {@code LayerParts.rotate}（反编译源码 162-268 行）的 LEGS 与 ARMS 两个分支，
 * 作用是把 vanilla 人形模型的四肢姿态<b>同步到</b>自定义部件上：
 * <pre>
 * if (part.animationType == PartBehaviorType.LEGS) {
 *     model = (HumanoidModel) this.getParentModel();
 *     modelPart2 = part.getPart("right_leg");
 *     if (modelPart2 != null) {
 *         modelPart2.setRot(new NopVector3f(model.rightLeg.xRot, model.rightLeg.yRot, model.rightLeg.zRot));
 *         modelPart2.setPos(new NopVector3f(model.rightLeg.x,    model.rightLeg.y,    model.rightLeg.z));
 *     }
 *     if ((modelPart2 = part.getPart("left_leg")) != null) {
 *         modelPart2.setRot(new NopVector3f(model.leftLeg.xRot, ...));
 *         modelPart2.setPos(new NopVector3f(model.leftLeg.x,    ...));
 *     }
 * }
 * if (part.animationType == PartBehaviorType.ARMS) {
 *     ... 同构，right_arm / left_arm，又是 4 个 ...
 * }
 * </pre>
 *
 * <p>LEGS 分支 4 个 {@code NopVector3f}，ARMS 分支 4 个 —— 共 8 个。
 * 每帧、每个 NPC、每个 MPM 部件。
 *
 * <p>而这 8 个对象<b>全部在 setter 里被立刻拆成三个 float 然后丢弃</b>
 * （见 {@link MixinModelPartWrapperRaw} 的分析）。纯粹是参数容器。
 *
 * <h2>量级</h2>
 * MPM 部件系统里一个 NPC 常见有 5-15 个部件（头发、胡子、耳朵、尾巴、翅膀……），
 * 其中 LEGS/ARMS 行为类型的通常有 2-4 个。
 * 一个视野里有 30 个这类 NPC 的场景，60 FPS 下：
 * 30 × 3 × 8 × 60 ≈ <b>每秒 43000 个短命对象</b>。
 * 单个 {@code NopVector3f} 是 24 字节（对象头 16 + 三个 float 12，对齐到 32），
 * 也就是约 1.4 MB/秒的 Eden 区分配。
 *
 * <h2>本类怎么做</h2>
 * 不重写 {@code rotate}（那个方法有 100 多行动画逻辑，重写风险大且会与其他附属冲突），
 * 而是<b>只拦掉 {@code new NopVector3f} 这条指令</b>。
 *
 * <p>但这里有个技术难点：{@code @Redirect} 到 {@code NEW} 之后，
 * 我们的处理函数必须返回一个 {@code NopVector3f}，
 * 否则后面的 {@code setRot(NopVector3f)} 调用无法进行 ——
 * 也就是说单靠重定向构造器<b>省不掉分配</b>。
 *
 * <p>所以真正的做法是重定向<b>setter 调用</b>：
 * 拦下 {@code ModelPartWrapper.setRot(NopVector3f)} 与 {@code setPos(NopVector3f)}，
 * 在处理函数里改调 {@link ModelPartWrapperRaw} 的免分配重载。
 *
 * <p>那个 {@code NopVector3f} 参数仍然会被构造（我们改不了 JVM 的求值顺序），
 * 但它<b>立刻成为不可达对象</b>，且从未逃逸出这个方法 ——
 * 这正是 HotSpot 逃逸分析最擅长的场景：
 * C2 会把它<b>标量替换</b>（把对象拆成三个栈上的 float 局部变量），
 * 分配彻底消失，不进 Eden 区。
 *
 * <p>原版为什么没有这个效果？因为原版的 {@code setRot(NopVector3f)}
 * 会把参数传给 {@code mpmPart.setRotation(NopVector3f)} ——
 * 对象<b>逃逸到另一个方法</b>，逃逸分析失效，只能真的分配。
 * 我们的 raw 版本把参数拆成 float 传递，切断了逃逸链。
 *
 * <h2>为什么带 {@code require = 0}</h2>
 * 这是纯粹的分配优化，不涉及正确性。如果 CNPC 将来改了 {@code rotate} 的写法
 * 导致注入匹配不上，静默失效（退回原版行为）远好于崩游戏。
 *
 * <p>注意 {@code ordinal} 不指定 —— {@code rotate} 里对
 * {@code setRot} 的调用有多处（LEGS 2 次、ARMS 2 次、WINGS 2 次、WINGS2 2 次），
 * 全部重定向到同一个处理函数是正确的，因为处理函数的语义与原版逐一等价。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = LayerParts.class, remap = false)
public abstract class MixinLayerPartsRotate {

    /**
     * 拦下全部 {@code ModelPartWrapper.setRot(NopVector3f)} 调用。
     *
     * <p>覆盖 {@code rotate} 里的 LEGS(2) / ARMS(2) / WINGS(2) / WINGS2(2) 共 8 处。
     * WINGS 那几处传的是 {@code modelPart.oriRot.add(...)} 的结果 ——
     * 那个 {@code add} 本身也返回新对象，同样会被逃逸分析吃掉。
     */
    @Redirect(
            method = "rotate",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/client/parts/ModelPartWrapper;setRot(Lnoppes/npcs/shared/common/util/NopVector3f;)V"),
            require = 0,
            remap = false
    )
    private void cnpcplus$setRotNoAlloc(ModelPartWrapper wrapper,
                                          noppes.npcs.shared.common.util.NopVector3f rot) {
        if (wrapper instanceof ModelPartWrapperRaw raw) {
            // 拆成三个 float 传递，切断对象的逃逸链，让 C2 能做标量替换。
            raw.cnpcplus$setRotRaw(rot.x, rot.y, rot.z);
        } else {
            // 理论上不会走到（mixin 一定应用成功才有这个 redirect），
            // 但保留原版路径作为兜底。
            wrapper.setRot(rot);
        }
    }

    /** 拦下全部 {@code ModelPartWrapper.setPos(NopVector3f)} 调用（LEGS 2 处、ARMS 2 处）。 */
    @Redirect(
            method = "rotate",
            at = @At(value = "INVOKE",
                    target = "Lnoppes/npcs/client/parts/ModelPartWrapper;setPos(Lnoppes/npcs/shared/common/util/NopVector3f;)V"),
            require = 0,
            remap = false
    )
    private void cnpcplus$setPosNoAlloc(ModelPartWrapper wrapper,
                                          noppes.npcs.shared.common.util.NopVector3f pos) {
        if (wrapper instanceof ModelPartWrapperRaw raw) {
            raw.cnpcplus$setPosRaw(pos.x, pos.y, pos.z);
        } else {
            wrapper.setPos(pos);
        }
    }
}
