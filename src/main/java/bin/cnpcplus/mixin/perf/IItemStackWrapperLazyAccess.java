package bin.cnpcplus.mixin.perf;

import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 读写 {@code ItemStackWrapper} 的两个 private 字段，供
 * {@link MixinItemStackWrapperLazy} 的懒初始化使用。
 *
 * <h3>为什么需要它</h3>
 * 那两个字段的读取点是**静态**合成方法（{@code access$000} / {@code access$100}，
 * 由编译器为内部类 {@code $1}/{@code $2} 访问 private 字段而生成）。
 * {@code @Redirect} 到静态方法时 handler 也必须是 static，
 * 于是拿不到 {@code this}，也用不了 {@code @Shadow} 实例字段 ——
 * 只能通过传入的 {@code self} 参数访问，而 private 字段跨类不可见。
 *
 * {@code @Accessor} 在目标类上合成 public getter/setter 解决这个问题。
 * 与本项目既有的 {@code ILoadSkinInvoker}、{@code ILayerNpcAccessor} 同一套做法。
 *
 * <h3>服务端安全</h3>
 * {@code ItemStackWrapper} 两端共用，本接口注册 common 侧，
 * 只用 java.util 与 NBT 类型，无客户端引用。
 */
@Mixin(value = ItemStackWrapper.class, remap = false)
public interface IItemStackWrapperLazyAccess {

    @Accessor("tempData")
    Map<String, Object> cnpcplus$getTempData();

    @Accessor("tempData")
    void cnpcplus$setTempData(Map<String, Object> value);

    @Accessor("storedData")
    NBTTagCompound cnpcplus$getStoredData();

    @Accessor("storedData")
    void cnpcplus$setStoredData(NBTTagCompound value);
}
