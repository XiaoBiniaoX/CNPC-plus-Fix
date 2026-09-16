package bin.cnpcplus.mixin.perf;

import noppes.npcs.client.layer.LayerInterface;
import noppes.npcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 读取 {@code LayerInterface.npc}，供 {@link MixinLayerBodySkirtLod} 判断距离。
 *
 * <h3>为什么需要它</h3>
 * {@code npc} 是 {@code protected} 且声明在 {@code LayerInterface}（父类），
 * 两条捷径都走不通：
 * <ul>
 *   <li>{@code @Shadow} 对**继承来的**成员在本项目环境下解析失败
 *       （编译期 "Cannot find target for @Shadow field"）—— findings 记过的坑；</li>
 *   <li>直接 cast 后访问 protected 字段编译不过 ——
 *       {@code bin.cnpcplus.mixin.perf} 与 {@code noppes.npcs.client.layer} 不同包。</li>
 * </ul>
 * {@code @Accessor} 在目标类上合成一个 public getter，绕开这两个限制。
 * 这与本项目既有的 {@code ILoadSkinInvoker}、阶段 4 的
 * {@code GuiScreenSizeAccessor} 是同一套做法。
 *
 * <h3>纯客户端</h3>
 * 注册在 client 侧。
 */
@Mixin(value = LayerInterface.class, remap = false)
public interface ILayerNpcAccessor {

    @Accessor("npc")
    EntityCustomNpc cnpcplus$getNpc();
}
