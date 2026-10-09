package bin.cnpcplus.mixin.perf;

import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import noppes.npcs.client.parts.ModelPartWrapper;
import noppes.npcs.shared.client.model.NopModelPart;
import bin.cnpcplus.perf.client.ModelPartWrapperRaw;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/**
 * 优化 #10 的第一块：给 {@code ModelPartWrapper} 加免分配的旋转/位移写入。
 *
 * <h2>原版做了什么</h2>
 * {@code ModelPartWrapper}（反编译源码 19-89 行）的写入接口全部走 {@code NopVector3f}：
 * <pre>
 * public void setPos(NopVector3f pos) {
 *     if (this.mcPart != null) this.mcPart.setPos(pos.x, pos.y, pos.z);
 *     else this.mpmPart.setPos(pos.x, pos.y, pos.z);
 * }
 * public void setRot(NopVector3f rot) {
 *     if (this.mcPart != null) this.mcPart.setRotation(rot.x, rot.y, rot.z);
 *     else this.mpmPart.setRotation(rot);
 * }
 * </pre>
 *
 * <p>注意 {@code setPos} 的两个分支<b>都只读 pos 的三个 float</b> ——
 * 那个 {@code NopVector3f} 对象纯粹是个参数容器，创建出来立刻被拆开、然后丢弃。
 *
 * <h2>为什么不能把 {@code NopVector3f} 改成可变的复用对象</h2>
 * {@code NopVector3f}（反编译源码 12-97 行）是<b>不可变值类</b>：
 * <pre>
 * public final float x;
 * public final float y;
 * public final float z;
 * public static final NopVector3f ZERO = new NopVector3f(0, 0, 0);
 * public static final NopVector3f ONE  = new NopVector3f(1, 1, 1);
 * </pre>
 * {@code ZERO} / {@code ONE} 是<b>全局共享的静态常量</b>，
 * 被 {@code MpmPart.translate/scale/rotatePoint/rotate} 等大量字段引用为初值。
 * 一旦改成可变并被写坏，整个模型系统会全局崩坏。
 *
 * <p>所以正确的路子是<b>加一条免分配的旁路</b>，而不是动那个值类。
 *
 * <h2>本类怎么做</h2>
 * 用 {@code @Unique} 注入两个 float 三元组重载。
 * 它们的行为与原版的 {@code NopVector3f} 版本<b>逐语句相同</b>，
 * 只是把「先装箱成对象再拆开」这一步省掉了。
 *
 * <p>{@code mpmPart} 分支需要说明一下：{@code NopModelPart.setRotation(NopVector3f)}
 * 只接受向量。但 {@code NopModelPart} 的 {@code xRot}/{@code yRot}/{@code zRot}
 * 是 {@code public float} 字段（反编译源码第 54-56 行），
 * 而 {@code setRotation(NopVector3f)} 做的就是给这三个字段赋值。
 * 所以直接写字段与调那个方法完全等价，且免掉一个对象。
 *
 * <p>{@code mpmPart.setPos(float, float, float)} 本来就存在
 * （{@code NopModelPart} 第 136 行），直接调即可。
 *
 * <h2>为什么这两个方法要 public</h2>
 * 调用方是 {@link MixinLayerPartsRotate}，位于同一个 mixin 包但作用于<b>不同的目标类</b>
 * （{@code LayerParts}）。Mixin 把 {@code @Unique} 成员合成到目标类里，
 * 跨目标类调用必须通过一个双方都能看到的类型。
 * 这里用 {@link ModelPartWrapperRaw} 接口来表达这个约定 —— 见那个文件的说明。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = ModelPartWrapper.class, remap = false)
public abstract class MixinModelPartWrapperRaw implements ModelPartWrapperRaw {

    @Shadow(remap = false) protected ModelPart mcPart;
    @Shadow(remap = false) protected NopModelPart mpmPart;

    /**
     * {@code setPos(NopVector3f)} 的免分配版本。
     *
     * <p>与原版第 46-52 行逐分支等价。
     */
    @Override
    @Unique
    public void cnpcplus$setPosRaw(float x, float y, float z) {
        if (this.mcPart != null) {
            this.mcPart.setPos(x, y, z);
        } else if (this.mpmPart != null) {
            // NopModelPart.setPos(float, float, float) 就是原版走的那条路。
            this.mpmPart.setPos(x, y, z);
        }
    }

    /**
     * {@code setRot(NopVector3f)} 的免分配版本。
     *
     * <p>{@code mcPart} 分支与原版第 61 行相同（{@code ModelPart.setRotation}）。
     *
     * <p>{@code mpmPart} 分支：原版调 {@code NopModelPart.setRotation(NopVector3f)}，
     * 而那个方法（反编译源码第 148-153 行）做的就是给
     * {@code xRot}/{@code yRot}/{@code zRot} 三个 public float 字段赋值。
     * 直接写字段等价且免掉一次分配。
     */
    @Override
    @Unique
    public void cnpcplus$setRotRaw(float x, float y, float z) {
        if (this.mcPart != null) {
            this.mcPart.setRotation(x, y, z);
        } else if (this.mpmPart != null) {
            this.mpmPart.xRot = x;
            this.mpmPart.yRot = y;
            this.mpmPart.zRot = z;
        }
    }
}
