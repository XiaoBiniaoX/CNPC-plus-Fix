package bin.cnpcplus.mixin.perf;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import noppes.npcs.client.parts.ModelPartWrapper;
import noppes.npcs.client.parts.MpmPartAbstractClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * 优化 #10 的第二块：{@code MpmPartAbstractClient.getPart(String)} 的字符串查找缓存。
 *
 * <h2>原版做了什么</h2>
 * {@code MpmPartAbstractClient}（反编译源码 33-50 行）：
 * <pre>
 * protected Map&lt;String, ModelPartWrapper&gt; defaultPose = new HashMap&lt;&gt;();
 *
 * public final ModelPartWrapper getPart(String name) {
 *     return this.defaultPose.get(name);
 * }
 * </pre>
 *
 * <p>{@code LayerParts.rotate}（反编译源码 162-268 行）每帧对每个部件调它<b>最多 8 次</b>：
 * <ul>
 *   <li>{@code PartBehaviorType.LEGS} 分支（200-211 行）：{@code "right_leg"}、{@code "left_leg"}</li>
 *   <li>{@code PartBehaviorType.ARMS} 分支（212-223 行）：{@code "right_arm"}、{@code "left_arm"}</li>
 *   <li>{@code WINGS} 分支（238-254 行）：{@code "right_wing"}、{@code "left_wing"}</li>
 *   <li>{@code WINGS2} 分支（255-267 行）：{@code "right_wing"}、{@code "left_wing"}</li>
 * </ul>
 * 加上 {@code LayerParts.renderPart}（74-160 行）里 LEGS/ARMS 各两次，
 * 一个部件一帧下来 4-8 次 {@code HashMap.get(String)}。
 *
 * <p>每次 {@code HashMap.get(String)} 要算一次字符串 hash（{@code String} 会缓存
 * {@code hashCode}，所以这一步是廉价的）、定位桶、然后 {@code equals} 逐字符比较。
 * 不算贵，但在「每帧 × 每个 NPC × 每个部件 × 8 次」的乘积下就是可观的调用量。
 *
 * <h2>为什么可以缓存</h2>
 * {@code defaultPose} 在部件加载阶段被填充
 * （由 {@code MpmPartSimple} / {@code MpmPartBedrock} 等子类在解析模型 JSON 时写入），
 * 之后<b>整个渲染期间不再变化</b>。
 *
 * <p>为了不依赖这个假设，本类做了防御：
 * 缓存条目和 {@code defaultPose} 的 {@code size()} 一起记录。
 * size 变了就重新查一遍。这样即使有子类在运行期动态增删部件，行为也仍然正确。
 *
 * <h2>为什么只缓存那六个名字</h2>
 * {@code getPart} 是 {@code public final}，理论上可以被任何代码用任意字符串调用。
 * 建一个通用的「上次查询结果」缓存需要处理键的比较，收益会被吃掉。
 *
 * <p>而实际调用点（上面列的全部位置）只用六个<b>字符串字面量</b>：
 * {@code right_leg} / {@code left_leg} / {@code right_arm} / {@code left_arm} /
 * {@code right_wing} / {@code left_wing}。
 * 所以这里为这六个各留一个字段，用 {@code ==}（引用比较）识别 ——
 * 字面量在常量池里是唯一实例，引用比较必然命中，而且比 {@code equals} 快。
 *
 * <p>不在这六个之内的名字直接落回原版路径，零风险。
 */
@OnlyIn(Dist.CLIENT)
@Mixin(value = MpmPartAbstractClient.class, remap = false)
public abstract class MixinMpmPartAbstractClientCache {

    @Shadow(remap = false) protected Map<String, ModelPartWrapper> defaultPose;

    @Unique private ModelPartWrapper cnpcplus$rightLeg;
    @Unique private ModelPartWrapper cnpcplus$leftLeg;
    @Unique private ModelPartWrapper cnpcplus$rightArm;
    @Unique private ModelPartWrapper cnpcplus$leftArm;
    @Unique private ModelPartWrapper cnpcplus$rightWing;
    @Unique private ModelPartWrapper cnpcplus$leftWing;

    /** 建立缓存时 {@code defaultPose} 的大小。变了就重查。 */
    @Unique private int cnpcplus$poseSize = -1;

    /** 哪些槽位已经查过（区分「查过但结果是 null」和「还没查」）。 */
    @Unique private int cnpcplus$resolvedMask;

    @Inject(method = "getPart", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$cachedLookup(String name, CallbackInfoReturnable<ModelPartWrapper> cir) {
        Map<String, ModelPartWrapper> pose = this.defaultPose;
        if (pose == null) {
            return;
        }

        int size = pose.size();
        if (size != this.cnpcplus$poseSize) {
            // 部件表变了（加载完成、或运行期动态改动）。丢弃全部缓存。
            this.cnpcplus$poseSize = size;
            this.cnpcplus$resolvedMask = 0;
            this.cnpcplus$rightLeg = null;
            this.cnpcplus$leftLeg = null;
            this.cnpcplus$rightArm = null;
            this.cnpcplus$leftArm = null;
            this.cnpcplus$rightWing = null;
            this.cnpcplus$leftWing = null;
        }

        // 引用比较：调用方全部用字符串字面量，常量池保证同一实例。
        // 不匹配的名字（第三方代码用动态字符串调）直接落回原版 HashMap 查找。
        int slot;
        if (name == "right_leg") slot = 0;
        else if (name == "left_leg") slot = 1;
        else if (name == "right_arm") slot = 2;
        else if (name == "left_arm") slot = 3;
        else if (name == "right_wing") slot = 4;
        else if (name == "left_wing") slot = 5;
        else return;

        int bit = 1 << slot;
        if ((this.cnpcplus$resolvedMask & bit) != 0) {
            cir.setReturnValue(cnpcplus$slot(slot));
            return;
        }

        ModelPartWrapper resolved = pose.get(name);
        cnpcplus$store(slot, resolved);
        this.cnpcplus$resolvedMask |= bit;
        cir.setReturnValue(resolved);
    }

    @Unique
    private ModelPartWrapper cnpcplus$slot(int slot) {
        return switch (slot) {
            case 0 -> this.cnpcplus$rightLeg;
            case 1 -> this.cnpcplus$leftLeg;
            case 2 -> this.cnpcplus$rightArm;
            case 3 -> this.cnpcplus$leftArm;
            case 4 -> this.cnpcplus$rightWing;
            default -> this.cnpcplus$leftWing;
        };
    }

    @Unique
    private void cnpcplus$store(int slot, ModelPartWrapper value) {
        switch (slot) {
            case 0 -> this.cnpcplus$rightLeg = value;
            case 1 -> this.cnpcplus$leftLeg = value;
            case 2 -> this.cnpcplus$rightArm = value;
            case 3 -> this.cnpcplus$leftArm = value;
            case 4 -> this.cnpcplus$rightWing = value;
            default -> this.cnpcplus$leftWing = value;
        }
    }
}
