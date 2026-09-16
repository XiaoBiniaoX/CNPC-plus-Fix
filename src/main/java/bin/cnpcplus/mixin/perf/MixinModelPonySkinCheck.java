package bin.cnpcplus.mixin.perf;

import noppes.npcs.client.model.ModelPony;
import noppes.npcs.entity.EntityNpcPony;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 性能问题 16：Pony 模型每帧同步 {@code ImageIO.read} 解码整张 PNG，只为读一个像素。
 *
 * <h3>根因（javap 字节码实证，比哈基彬描述更严重 —— 有两份重复实现）</h3>
 * <b>位置 A</b> {@code ModelPony.func_78088_a(Entity,FFFFFF)}（render）：
 * <pre>
 *  8: getfield EntityNpcPony.textureLocation
 * 13: getfield EntityNpcPony.checked
 * 16: if_acmpeq 248                  ← checked 命中则跳过整块
 * 52: invokestatic javax/imageio/ImageIO.read(InputStream)   ← 同步解码整张 PNG
 * 77: invokevirtual BufferedImage.getRGB(0, 0)               ← 只取单个像素
 * 240: putfield EntityNpcPony.checked ← ★ 只有成功路径才写标记
 * 243: goto 248
 * --- catch IOException: offset 246-247 ---
 * 246: astore 9                      ← 块内 putfield 计数 = 0
 * </pre>
 *
 * <b>位置 B</b> {@code RenderNPCPony.getEntityTexture(T)}：同一逻辑的副本，
 * {@code ImageIO.read} 在 offset 55、{@code getRGB(0,0)} 在 offset 78、
 * {@code checked} 写入在 offset 232、catch 块 offset 238-239（同样零 putfield）。
 *
 * 两者都在**主渲染线程**，均无异步、无 synchronized。
 * 调用链：{@code RenderLivingBase.doRender} 同时触发
 * {@code RenderNPCPony.func_110775_a}（→ 位置 B）与
 * {@code RenderLivingBase.func_77036_a → ModelBase.func_78088_a}（→ 位置 A）。
 *
 * <h3>后果</h3>
 * 正常情况 {@code checked} 命中后短路，只读一次。
 * 但 {@code ImageIO.read} 抛 {@code IOException} 时（资源包缺文件、PNG 损坏、
 * {@code textureLocation} 指向不存在的 URL 皮肤路径），控制流跳到 catch，
 * 而 {@code checked} 的 {@code putfield} 在 {@code goto} 之前的成功路径上**被跳过**
 * → 下一帧 {@code if_acmpeq} 再次失败 → **每帧两次完整 PNG 解码**。
 * 60 FPS 下 = 120 次/秒/NPC。且 {@code checked} 全 jar 无任何重置路径。
 *
 * <h3>修法（方案 A 的核心）</h3>
 * 在两处的 RETURN 无条件补写 {@code checked = textureLocation}。
 * 这样即使解码失败也标记为「已检查」，不再每帧重试 ——
 * 代价是该 NPC 的 pegasus/unicorn 判定保持默认值（false/false），
 * 但那本来就是解码失败时拿不到的信息，重试一万次也拿不到。
 *
 * 用 RETURN 而不是在 catch 里注入：Mixin 对 catch 块的注入需要
 * {@code @At("HANDLER")} 之类的非标准注入点，而 RETURN 更稳且同样覆盖
 * 「成功」与「异常被吞掉后继续执行」两条路径。
 *
 * <h3>没做的部分（哈基彬要求「留口子可随时改换为 C」）</h3>
 * 方案 A 不包括：
 * <ul>
 *   <li>合并两份重复实现（要改 RenderNPCPony 的调用链，风险大于收益）；</li>
 *   <li>改用 {@code ImageIO.getImageReaders} 只读首像素不解码全图
 *       （收益仅在首次，而首次本来就只有一次）；</li>
 *   <li><b>修那个死分支</b> —— {@code ModelPony} offset 174/176 与
 *       {@code RenderNPCPony} offset 174/177 的 {@code Color.equals} + {@code ifeq}
 *       跳到**紧邻的下一条指令**，比较结果完全丢弃，
 *       常量 {@code (249,177,49)} 那一路是无效判断（源码 bug）。
 *       修它会改变 pegasus/unicorn 的判定行为，而我无法确认原作者意图。</li>
 * </ul>
 * 死分支的口子留在 cfg 的 {@code ponySkinFixDeadBranch}（默认 false）。
 * 哈基彬要切 C 时把它打开即可 —— 届时需要另加一个混入实现实际赋值，
 * 本类只负责不再每帧重试这一件事。
 *
 * <h3>纯客户端</h3>
 * {@code ModelPony} 是客户端模型。本混入注册 client 侧。
 */
@Mixin(value = ModelPony.class, remap = false)
public class MixinModelPonySkinCheck {

    /**
     * 渲染方法返回时无条件标记已检查，消除异常路径的每帧重试。
     *
     * 目标 {@code func_78088_a(Entity,FFFFFF)} 返回 void，
     * 故 handler 用 {@code CallbackInfo}（阶段 25 的教训）。
     * 写精确描述符避免与 {@code ModelBase} 的其他重载混淆。
     */
    @Inject(method = "func_78088_a(Lnet/minecraft/entity/Entity;FFFFFF)V",
            at = @At("RETURN"), remap = false, require = 1)
    private void cnpcplus$markChecked(Entity entity, float limbSwing, float limbSwingAmount,
                                      float ageInTicks, float netHeadYaw, float headPitch,
                                      float scale, CallbackInfo ci) {
        if (!(entity instanceof EntityNpcPony)) return;
        EntityNpcPony pony = (EntityNpcPony) entity;
        // textureLocation 为 null 时原版也不会进入检查块，无需标记。
        if (pony.textureLocation != null) {
            pony.checked = pony.textureLocation;
        }
    }
}
