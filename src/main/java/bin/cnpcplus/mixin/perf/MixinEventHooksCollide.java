package bin.cnpcplus.mixin.perf;

import bin.cnpcplus.config.CnpcPlusConfig;
import net.minecraft.entity.Entity;
import noppes.npcs.EventHooks;
import noppes.npcs.entity.EntityNPCInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 性能问题 13：NPC 碰撞事件对象无条件创建。
 *
 * <h3>根因（javap 字节码实证）—— 瓶颈位置与哈基彬预期相反</h3>
 * 哈基彬报告「每 4 tick 扫附近所有生物并创建碰撞事件」，怀疑瓶颈在扫描。
 * 字节码显示**扫描本身可以忽略**：
 * <pre>
 * EntityNPCInterface.onCollide:
 *   12: irem                        // ticksExisted % 4
 *   70-79: func_72314_b(1.0, 0.5, 1.0)   ← 盒子只有 ~2.6 × 2.9 × 2.6，单区块
 *   91: World.func_72872_a(EntityLivingBase, box)
 *  104: List.size()                 ← 每次迭代重新调用（未提取局部变量）
 *  141: EventHooks.onNPCCollide(npc, entity)
 * </pre>
 *
 * 真瓶颈在 {@code EventHooks.onNPCCollide}：
 * <pre>
 *   4: DataScript.isClient()
 *   7: ifeq 11                      ← 唯一的门禁，只判「是不是客户端」
 *  11: new NpcEvent$CollideEvent     ← ★ 无条件分配
 *  20: <init>(ICustomNpc, Entity)    ← 构造器内还会 NpcAPI.getIEntity → capability 查询
 *  32: DataScript.runScript(...)     ← isEnabled() 检查在这个方法**内部**，即 new 之后
 *  38: EVENT_BUS.post(...)           ← ★ 完全不看脚本开关
 * </pre>
 * 所以哈基彬说的「即使没有脚本也会创建，浪费性能严重」**完全正确**，
 * 只是位置不在扫描而在事件构建。25 个 NPC 挤在一起时
 * 每 4 tick 产生 N×M 个短命对象 + N×M 次 EventBus 分发。
 *
 * <h3>修法</h3>
 * 在 {@code new} 之前判断「这个事件到底有没有人消费」：
 * <ul>
 *   <li>脚本启用（{@code DataScript.isEnabled()}）→ 需要，照常构建；</li>
 *   <li>否则检查 {@code WrapperNpcAPI.EVENT_BUS} 有没有注册监听者 —— 
 *       没有就整个跳过，零分配零分发。</li>
 * </ul>
 *
 * <h3>为什么不直接判 isEnabled 就 return</h3>
 * {@code EVENT_BUS.post} 是给**其他 mod / 附属**用的公开扩展点
 * （我们自己的 `cnpcobj`、`CNPC-Gecko-Addon` 之类可能监听）。
 * 只判脚本会静默掉别人的监听器，那是破坏兼容性而非优化。
 * 所以必须两个条件都不成立才跳过。
 *
 * <h3>EventBus 监听者数量怎么拿</h3>
 * Forge 的 {@code EventBus} 没有公开的「监听者数量」API。
 * 折中：用一次性反射读它的内部 listener 表，结果缓存 —— 
 * 反射只在首次调用时发生，之后是一次布尔读取。
 * 拿不到（Forge 内部结构变化）时**保守认为有监听者**，退回原版行为。
 *
 * <h3>可撤回</h3>
 * cfg 的 {@code skipUnusedCollideEvents}，默认开。
 *
 * <h3>服务端安全</h3>
 * {@code onNPCCollide} 由 {@code onCollide} 的服务端分支驱动
 * （{@code func_70636_d} offset 432，在 {@code field_72995_K} 为 false 的分支内），
 * 且方法自身 offset 4-10 还有 {@code isClient()} 门禁。
 * 本混入注册 common 侧，只用 noppes 与 MC 实体类型，无客户端引用。
 */
@Mixin(value = EventHooks.class, remap = false)
public class MixinEventHooksCollide {

    /**
     * 缓存的 listeners 字段句柄。null = 尚未解析或解析失败。
     *
     * 只缓存反射句柄，**不缓存探测结果** —— 监听者可以在运行期动态注册
     * （其他 mod 的延迟初始化、脚本重载），一次性缓存 false 会永久掉事件。
     * 读一次 Map.isEmpty() 的开销远小于 new 对象 + EventBus 分发。
     */
    private static java.lang.reflect.Field cnpcplus$listenersField;
    private static boolean cnpcplus$reflectionFailed;

    @Inject(method = "onNPCCollide", at = @At("HEAD"), cancellable = true,
            remap = false, require = 1)
    private static void cnpcplus$skipUnused(EntityNPCInterface npc, Entity entity, CallbackInfo ci) {
        if (!CnpcPlusConfig.isSkipUnusedCollideEvents()) return;
        if (npc == null || npc.script == null) return;

        // 脚本要用 → 照常构建。
        if (npc.script.isEnabled()) return;

        // 脚本不用，再看有没有别的 mod 监听 —— 有就照常构建，保兼容。
        if (cnpcplus$hasBusListeners()) return;

        // 两边都不消费：跳过 new + capability 查询 + EventBus 分发。
        ci.cancel();
    }

    /**
     * 每次实时探测 {@code WrapperNpcAPI.EVENT_BUS} 有无注册监听者。
     *
     * 只缓存反射句柄，不缓存结果 —— 监听者可以在运行期动态注册。
     * 反射失败时永久返回 true（保守），行为完全退回原版。
     */
    private static boolean cnpcplus$hasBusListeners() {
        if (cnpcplus$reflectionFailed) return true;
        try {
            if (cnpcplus$listenersField == null) {
                java.lang.reflect.Field f =
                        net.minecraftforge.fml.common.eventhandler.EventBus.class
                                .getDeclaredField("listeners");
                f.setAccessible(true);
                cnpcplus$listenersField = f;
            }
            Object listeners = cnpcplus$listenersField
                    .get(noppes.npcs.api.wrapper.WrapperNpcAPI.EVENT_BUS);
            if (listeners instanceof java.util.Map) {
                return !((java.util.Map<?, ?>) listeners).isEmpty();
            }
            // 字段类型不是 Map（Forge 版本差异）→ 放弃优化，退回原版。
            cnpcplus$reflectionFailed = true;
            return true;
        } catch (Throwable ignored) {
            cnpcplus$reflectionFailed = true;
            return true;
        }
    }
}
