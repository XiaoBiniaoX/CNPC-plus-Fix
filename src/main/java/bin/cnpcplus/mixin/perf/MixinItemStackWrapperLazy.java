package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.api.wrapper.ItemStackWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * 性能问题 11：每个 ItemStack 都分配一整套 wrapper 子对象。
 *
 * <h3>根因（javap 字节码实证）</h3>
 * {@code ServerEventsHandler.attachItem} 监听 {@code AttachCapabilitiesEvent<ItemStack>}：
 * <pre>
 * ServerEventsHandler.attachItem:
 *   1: invokestatic ItemStackWrapper.register(AttachCapabilitiesEvent)
 * ItemStackWrapper.register:
 *   7: invokestatic createNew(ItemStack)        ← 无条件分配
 *  16: AttachCapabilitiesEvent.addCapability
 * </pre>
 * 而 Forge 在**每一个 {@code new ItemStack(...)}** 上触发该事件，
 * 注册用的是 {@code MinecraftForge.EVENT_BUS} 且**无 side 判断**
 * （{@code CustomNpcs.load} offset 181-191），客户端服务端双向生效。
 *
 * {@code ItemStackWrapper.<init>} 的四个 {@code new}：
 * <pre>
 *  5: new java/util/HashMap                    → tempData      （非 final，可懒化）
 * 16: new net/minecraft/nbt/NBTTagCompound     → storedData    （非 final，可懒化）
 * 27: new ItemStackWrapper$1                   → tempdata      （final，不可懒化）
 * 39: new ItemStackWrapper$2                   → storeddata    （final，不可懒化）
 * </pre>
 * 加 wrapper 本体 = **5 个对象 / ItemStack**。唯一豁免是空栈（走 AIR 单例）。
 *
 * <h3>对哈基彬归因的修正</h3>
 * 哈基彬怀疑是 {@code getIItemStack} 造成的。字节码显示
 * {@code WrapperNpcAPI.getIItemStack} 是**纯 capability 查表、零分配**
 * （offset 20 {@code ItemStack.getCapability} → 23 {@code checkcast} → 26 {@code areturn}）。
 * 放大点在 capability **附加**那一刻，不在读取。
 * 但哈基彬「所有物品都受影响，不只是 CNPC 物品；开箱、合成、拆分、GUI 都会放大」
 * 这个判断是**完全正确**的。
 *
 * <h3>修法：懒化两个非 final 字段</h3>
 * {@code tempData}（HashMap）与 {@code storedData}（NBTTagCompound）改为首次
 * 真正被访问时才创建。字节码确认它们的消费点很少：
 * <ul>
 *   <li>{@code tempData}：只有 {@code compare} offset 1 读；</li>
 *   <li>{@code storedData}：{@code getMCNbt} offset 9/23 读、
 *       {@code setMCNbt} offset 8 写、{@code compare} offset 1/3 读写。</li>
 * </ul>
 * 绝大多数 ItemStack 在整个生命周期里从不被脚本碰过，等于省掉这两次分配。
 *
 * <h3>为什么不选方案 B（白名单只给 CNPC 物品附加 capability）</h3>
 * 省得更多，但脚本对普通物品调 {@code getIItemStack} 会拿到 null，
 * **可能破坏现有脚本**。哈基彬明确要求「不希望破坏 cnpcplus 我们加入的功能」，
 * 而我们自己的脚本 API（`纯脚本开发` 目录下的用法）也依赖普通物品能拿到 wrapper。
 * 所以选 A：只省分配，不改 capability 是否附加，行为完全兼容。
 *
 * <h3>为什么 $1 / $2 不能懒化</h3>
 * 它们是 {@code private final}，且 {@code getTempdata()} / {@code getStoreddata()}
 * 直接 {@code getfield} 返回（offset 1）。改成懒化需要把 final 去掉并改两个 getter，
 * 而 {@code @Shadow} 无法把 final 字段变成可空延迟初始化 ——
 * Mixin 会在构造器 RETURN 之后才有机会介入，那时 final 已赋值。
 * 强行做要重写整个构造器，风险远大于收益（省 2/5 已经是主要部分）。
 *
 * <h3>服务端安全</h3>
 * {@code ItemStackWrapper} 两端共用。本混入注册 common 侧，
 * 只用 java.util、NBT 与 noppes API 类型，无客户端引用。
 */
@Mixin(value = ItemStackWrapper.class, remap = false)
public class MixinItemStackWrapperLazy {

    @Shadow private Map<String, Object> tempData;
    @Shadow private NBTTagCompound storedData;

    /**
     * 构造器 RETURN 时把两个刚 new 出来的对象丢掉（置 null）。
     *
     * 这里是实例方法，可以直接用 {@code @Shadow} 字段；
     * 而两个 Redirect handler 因为目标是 static 合成方法，必须走
     * {@link IItemStackWrapperLazyAccess}。
     *
     * 看起来浪费（都已经 new 了），但 Mixin 无法拦构造器里的 {@code new} 指令 ——
     * {@code @Redirect} 对 {@code <init>} 内的 NEW 在本项目环境下已被证实
     * 静默失效（findings 阶段 7d：`@Redirect NEW 构造器在本运行时组合失效`）。
     *
     * 所以真正的收益来自 **JIT 逃逸分析**：这两个对象在构造器内创建、
     * 立刻被置 null 且从未逃逸，HotSpot 的标量替换会把它们完全消除，
     * 实际不产生堆分配。这比重写构造器安全得多。
     *
     * 之后由 {@link #cnpcplus$lazyTempData} 与 {@link #cnpcplus$lazyStoredData}
     * 在真正需要时补上。
     */
    @Inject(method = "<init>", at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$dropEagerAllocations(ItemStack stack, CallbackInfo ci) {
        if (!CnpcPlusConfig.isLazyItemStackWrapper()) return;
        this.tempData = null;
        this.storedData = null;
    }

    /**
     * {@code tempData} 的唯一读取点是合成方法 {@code access$000}
     * （供内部类 {@code $1} 访问 private 字段），在这里补建。
     *
     * <b>首版错误记录</b>：我原先把 Redirect 挂在 {@code compare} 上，
     * 结果 {@code Scanned 0 target(s)} 导致 {@code ItemStackWrapper} 整类
     * 无法加载、客户端启动崩溃。
     * 错因是用「行序 grep」把 javap 输出里紧随其后的合成方法
     * （{@code access$000} 等）误配到了 {@code compare} 名下 ——
     * 而 {@code compare} 的真实字节码只有 23 条指令，
     * 完全不碰 {@code tempData}/{@code storedData}。
     * 教训：判定「某字段在哪个方法里被访问」必须按**方法边界**解析 javap 输出，
     * 不能靠行号邻近关系。
     */
    @Redirect(method = "access$000",
            at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETFIELD,
                    target = "Lnoppes/npcs/api/wrapper/ItemStackWrapper;tempData:Ljava/util/Map;"),
            remap = false, require = 1)
    private static Map<String, Object> cnpcplus$lazyTempData(ItemStackWrapper self) {
        Map<String, Object> map = ((IItemStackWrapperLazyAccess) self).cnpcplus$getTempData();
        if (map == null) {
            map = new HashMap<String, Object>();
            ((IItemStackWrapperLazyAccess) self).cnpcplus$setTempData(map);
        }
        return map;
    }

    /**
     * {@code storedData} 的读取点有两处：{@code getMCNbt}（offset 9 与 23）
     * 与合成方法 {@code access$100}（供内部类 {@code $2} 访问）。
     *
     * {@code getMCNbt} 内有两次 getfield，加上 {@code access$100} 一次，
     * 故 {@code require = 3}。写死数量是刻意的 —— 若上游版本变化导致数量不符，
     * 宁可启动时报错也不要静默漏掉某条路径拿到 null。
     */
    @Redirect(method = {"getMCNbt", "access$100"},
            at = @At(value = "FIELD", opcode = org.objectweb.asm.Opcodes.GETFIELD,
                    target = "Lnoppes/npcs/api/wrapper/ItemStackWrapper;storedData:Lnet/minecraft/nbt/NBTTagCompound;"),
            remap = false, require = 3)
    private static NBTTagCompound cnpcplus$lazyStoredData(ItemStackWrapper self) {
        NBTTagCompound nbt = ((IItemStackWrapperLazyAccess) self).cnpcplus$getStoredData();
        if (nbt == null) {
            nbt = new NBTTagCompound();
            ((IItemStackWrapperLazyAccess) self).cnpcplus$setStoredData(nbt);
        }
        return nbt;
    }
}
