package bin.cnpcplus.mixin.perf;

import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataScript;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import bin.cnpcplus.perf.CollideEventGate;

/**
 * 优化 #3：给 {@code EntityNPCInterface.onCollide()} 加门控。
 *
 * <h2>原版做了什么</h2>
 * {@code EntityNPCInterface.onCollide()}（反编译源码 1353-1373 行），
 * 由 {@code aiStep()}（{@code m_8107_}）第 626 行在服务端每 tick 调用：
 * <pre>
 * public void onCollide() {
 *     if (!this.isAlive()) return;
 *     if (this.tickCount % 4 != 0) return;
 *     if (this.level().isClientSide) return;
 *     AABB aabb = this.getVehicle() != null &amp;&amp; this.getVehicle().isAlive()
 *             ? this.getBoundingBox().minmax(this.getVehicle().getBoundingBox()).inflate(1.0, 0.0, 1.0)
 *             : this.getBoundingBox().inflate(1.0, 0.5, 1.0);
 *     List list = this.level().getEntitiesOfClass(LivingEntity.class, aabb);
 *     if (list == null) return;
 *     for (int i = 0; i &lt; list.size(); i++) {
 *         Entity entity = (Entity) list.get(i);
 *         if (entity != this &amp;&amp; entity.isAlive()) EventHooks.onNPCCollide(this, entity);
 *     }
 * }
 * </pre>
 *
 * <h2>无效开销在哪</h2>
 * {@code EventHooks.onNPCCollide} 的全部作用就是「触发 COLLIDE 脚本 + 发一个 API 事件」：
 * <pre>
 * if (npc.script.isClient()) return;
 * NpcEvent.CollideEvent event = new NpcEvent.CollideEvent(npc.wrappedNPC, entity);
 * npc.script.runScript(EnumScriptType.COLLIDE, event);
 * WrapperNpcAPI.EVENT_BUS.post(event);
 * </pre>
 * 而 {@code DataScript.runScript} 第一句是 {@code if (!this.isEnabled()) return;}，
 * {@code isEnabled()} = {@code enabled &amp;&amp; ScriptController.HasStart &amp;&amp; !clientSide}。
 *
 * <p>所以对一个<b>没开脚本</b>、且 API 总线上<b>没有监听器</b>的 NPC，
 * 这整条路径的唯一效果是：
 * <ul>
 *   <li>每 4 tick 一次 {@code AABB.inflate}（新建 1-2 个 {@code AABB} 对象）</li>
 *   <li>每 4 tick 一次 {@code getEntitiesOfClass} —— 要遍历 AABB 覆盖的全部区块 section
 *       的实体列表，还要新建一个结果 {@code ArrayList}</li>
 *   <li>每个候选一个 {@code CollideEvent} 对象 + 一次 {@code wrappedNPC} 取用</li>
 * </ul>
 * 全部丢弃。一个有 200 个 NPC 的服务器，这是每秒 1000 次白跑的实体范围查询。
 *
 * <h2>本类怎么做</h2>
 * 在 HEAD 判断「这次调用是否可能产生任何可观察效果」，不可能就 {@code cancel()}。
 *
 * <p>门控条件（两个都不成立才跳过）：
 * <ol>
 *   <li>{@code npc.script.getEnabled()} —— NPC 自己开了脚本。
 *       注意用 {@code getEnabled()} 而不是 {@code isEnabled()}：
 *       后者还要求 {@code ScriptController.HasStart}，而那是个全局开关，
 *       可能在运行中变化。用更宽松的 {@code getEnabled()}
 *       意味着「配了脚本的 NPC 一律走原版路径」，宁松不紧。</li>
 *   <li>{@link CollideEventGate#apiMayListen()} —— API 总线上可能有 Java 监听器。
 *       无法判断时返回 {@code true}，也就是走原版路径。</li>
 * </ol>
 *
 * <h2>为什么这个优化是安全的</h2>
 * 被跳过的调用<b>在原版语义下也是无副作用的</b>：
 * {@code onCollide} 除了触发事件之外不修改任何状态（不推开实体、不造成伤害、
 * 不设置任何字段）。这一点我逐行确认过 —— 方法体里只有查询和 {@code EventHooks} 调用。
 * 所以「不产生事件的调用」与「不调用」完全等价。
 *
 * <h2>与其他附属的共存</h2>
 * 同目录的 CNPC 附属（{@code top.cnpcplus}）有一个 {@code MixinEntityNPCFlyingFix}，
 * 它用 {@code @Redirect} 拦下 {@code aiStep} 里对 {@code onCollide()} 的<b>调用点</b>，
 * 把飞行 NPC 的碰撞检测延后到移动之后再执行（在 {@code aiStep} 的 RETURN 上补调）。
 *
 * <p>那是对<b>调用时机</b>的改造，本类是对<b>方法入口</b>的门控 —— 两者作用在不同位置，
 * 不冲突，且叠加后语义正确：
 * <ul>
 *   <li>飞行 NPC：Redirect 把调用推迟到 RETURN，然后走进本类的 HEAD 门控。
 *       该跳过的仍然跳过，该执行的时机仍然是「移动后」。</li>
 *   <li>地面 NPC：Redirect 原样转发，然后走进本类的门控。</li>
 * </ul>
 * 这也正是本类不用 {@code @Overwrite} 的原因之一。
 *
 * <h2>为什么不顺手改 {@code % 4} 的频率</h2>
 * 想过，但没做。{@code % 4} 是脚本作者可以观察到的行为（COLLIDE 事件的触发密度），
 * 改成 {@code % 10} 会让依赖高频碰撞检测的脚本（比如「踩到就传送」的机关）表现变差。
 * 而门控已经把「没脚本时」的开销降到零 —— 真正开了脚本的 NPC 本来就该按原频率跑。
 *
 * <p><b>1.21.1 已核签名一致</b>（javap）：
 * {@code public void onCollide()} 存在，方法体仍是「isAlive → tickCount % 4 →
 * isClientSide → AABB → getEntitiesOfClass → EventHooks.onNPCCollide」；
 * {@code public final DataScript script} 字段仍是 public final；
 * {@code EventHooks.onNPCCollide} 仍是 isClient → new CollideEvent → runScript →
 * {@code WrapperNpcAPI.EVENT_BUS.post}，总线类型在 1.21.1 是
 * {@code net.neoforged.bus.api.IEventBus}（见 {@link CollideEventGate}）。
 */
@Mixin(value = EntityNPCInterface.class, remap = false)
public abstract class MixinEntityNPCCollide {

    /** {@code EntityNPCInterface.script}，字节码确认是 {@code public final}。 */
    @Shadow(remap = false) @Final public DataScript script;

    @Inject(method = "onCollide", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$skipWhenNoConsumer(CallbackInfo ci) {
        EntityNPCInterface npc = (EntityNPCInterface) (Object) this;

        // 客户端本来就会在原版第 1356 行返回，这里提前挡掉，省一次 AABB 构造。
        if (npc.level().isClientSide) {
            ci.cancel();
            return;
        }

        // 这个 NPC 配了脚本 → 走原版路径。
        // 用 getEnabled()（只看 NPC 自身开关）而不是 isEnabled()（还看全局 HasStart），
        // 判据更宽松，宁可多跑也不漏事件。
        DataScript s = this.script;
        if (s != null && s.getEnabled()) {
            return;
        }

        // 有 Java 侧 API 监听器（或无法判断） → 走原版路径。
        if (CollideEventGate.apiMayListen()) {
            return;
        }

        // 没有任何消费者。这次调用在原版下也不会产生可观察效果，直接跳过。
        ci.cancel();
    }
}
