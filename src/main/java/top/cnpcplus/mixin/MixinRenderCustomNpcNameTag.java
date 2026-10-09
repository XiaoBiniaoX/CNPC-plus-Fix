package top.cnpcplus.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import noppes.npcs.client.renderer.RenderCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 优化 #11 的第二处：{@code RenderCustomNpc.render} 每帧一个 {@code Component.empty()}。
 *
 * <h2>原版做了什么</h2>
 * {@code RenderCustomNpc.render}（反编译源码 167-227 行）第 188 行，
 * 在 NPC 使用「简单渲染」的替代实体模型时：
 * <pre>
 * if (npc.modelData.simpleRender) {
 *     this.renderEntity = null;
 *     matrixStack.pushPose();
 *     render.render(this.entity, entityYaw, partialTicks, matrixStack, buffer, packedLight);
 *     this.renderNameTag(npc, Component.empty(), matrixStack, buffer, packedLight);   // ← 每帧
 *     matrixStack.popPose();
 *     return;
 * }
 * </pre>
 *
 * <h2>为什么这是个纯浪费</h2>
 * 两点：
 * <ol>
 *   <li><b>{@code Component.empty()} 在 1.20.1 不是常量。</b>
 *       它的实现是 {@code MutableComponent.create(ComponentContents.EMPTY)}，
 *       每次调用都 new 一个 {@code MutableComponent}（含一个 {@code Style} 引用
 *       和一个 lazily 初始化的 siblings 列表槽位）。</li>
 *   <li><b>这个参数根本没被用。</b>
 *       看 {@code RenderNPCInterface.renderNameTag}（75-99 行）的方法体：
 *       <pre>
 *       public void renderNameTag(T npc, Component text, PoseStack ms, MultiBufferSource buf, int light) {
 *           ...
 *           if (npc.messages != null) { ... npc.messages.renderMessages(...) }
 *           if (npc.display.showName()) { this.renderLivingLabel(npc, ms, buf, light); }
 *           ...
 *       }
 *       </pre>
 *       形参 {@code text} <b>在整个方法体里一次都没出现</b>。
 *       它是从 vanilla {@code EntityRenderer.renderNameTag(Entity, Component, ...)}
 *       的签名继承来的，CNPC 重载了同名方法但没用那个参数
 *       （名字实际来自 {@code renderLivingLabel} 里的 {@code npc.getName()}）。</li>
 * </ol>
 *
 * <p>所以这里每帧分配一个对象，唯一用途是填一个被忽略的形参。
 *
 * <h2>本类怎么做</h2>
 * 把它换成一个静态复用实例。
 *
 * <p><b>为什么复用一个可变对象是安全的</b>：
 * 上面已经论证过接收方完全不碰它。为了不依赖那个论证的永久有效性，
 * 这里刻意<b>不</b>用 {@code Component.empty()} 造实例然后共享 ——
 * 万一将来 CNPC 真的用上了那个参数并调 {@code append} 修改它，
 * 共享的可变实例会被污染。
 *
 * <p>用 {@code CommonComponents.EMPTY} 就没有这个隐患：它是 vanilla 自己的
 * {@code public static final Component}，本来就是全局共享的空组件常量，
 * vanilla 各处（包括 {@code Font.drawInBatch} 的入参）都直接用它。
 * 语义上等价于 {@code Component.empty()} 的返回值，但是不可变共享安全。
 *
 * <h2>为什么写 SRG 名</h2>
 * 本 mixin 是 {@code remap = false}（目标 {@code render} 是 CNPC 自有方法），
 * 而 {@code remap = false} 对整个注解树生效，{@code @At.target} 也不会被重映射。
 * 所以要写生产环境类文件里的真实名字：{@code Component.empty()} 的 SRG 名是
 * {@code m_237119_}。
 *
 * <p>{@code require = 0}：纯分配优化，失配就退回原版，不影响正确性。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = RenderCustomNpc.class, remap = false)
public abstract class MixinRenderCustomNpcNameTag {

    /**
     * 复用的空组件。
     *
     * <p>用 vanilla 的 {@code CommonComponents.EMPTY} 而不是自己 new 一个 ——
     * 它是不可变共享常量，即使将来接收方真的开始使用这个参数也不会被污染。
     */
    @Unique
    private static final Component CNPCYOUHUA_EMPTY = net.minecraft.network.chat.CommonComponents.EMPTY;

    /**
     * <b>必须写完整描述符</b>：{@code RenderCustomNpc} 有两个 {@code render} 重载
     * （{@code EntityCustomNpc} 版是真实实现，{@code EntityNPCInterface} 版是泛型桥接）。
     * 只写 {@code "render"} 会同时匹配两个而注入失败。
     *
     * <p>{@code m_237119_} 是 {@code Component.empty()} 的 SRG 名。
     * 本 mixin 是 {@code remap = false}，注解树里的目标不会被重映射，
     * 所以必须写生产环境类文件里的真实名字（javap 已实证偏移 190 处就是它）。
     */
    @Redirect(
            method = "render(Lnoppes/npcs/entity/EntityCustomNpc;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/network/chat/Component;m_237119_()Lnet/minecraft/network/chat/MutableComponent;"),
            require = 0,
            remap = false
    )
    private MutableComponent cnpcyouhua$reuseEmpty() {
        // renderNameTag 的形参类型是 Component，但被 redirect 的方法返回 MutableComponent，
        // 所以这里的返回类型必须匹配原调用的返回类型。
        // CommonComponents.EMPTY 的实际类型是 MutableComponent（Component.literal("") 的产物）。
        if (CNPCYOUHUA_EMPTY instanceof MutableComponent mutable) {
            return mutable;
        }
        return Component.empty();
    }
}
