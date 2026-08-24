package bin.cnpcplus.mixin.ai;

import bin.cnpcplus.ai.AiSpeedAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = DataAI.class, remap = false)
public class MixinDataAISpeed implements AiSpeedAccess {
    @Shadow
    private EntityNPCInterface npc;

    @Shadow
    private int moveSpeed;

    @Unique
    private float cnpcplus$moveSpeed = 5.0F;

    /**
     * 整数入口（CNPC 公开脚本 API {@code INPCAi.setWalkingSpeed(int)}）的浮点值同步。
     *
     * <p>必须用 RETURN 而不是 HEAD：原版方法体里先写 {@code moveSpeed = speed}，
     * 再调 {@code npc.getSpeed()} 去设置属性，而 getSpeed() 已被本模组重定向到读浮点影子值。
     * 若在 HEAD 同步，等于在原版赋值前抢跑，两个值在方法执行期间不一致。
     *
     * <p>这里也不做 {@code Math.max(0.01F, ...)} 钳制：原版 setWalkingSpeed 只拦负数和 >100，
     * {@code speed=0} 是合法输入，语义就是「让 NPC 停下不动」。
     * 早期实现把 0 钳成 0.01，结果影子值 0.01 而原版 moveSpeed=0，
     * getSpeed() 返回 0.01/20=0.0005，NPC 变成几乎不动 —— 这正是「脚本改移速后 NPC 罚站」的成因。
     * 最小值限制只属于 GUI 的浮点入口，不能施加到脚本 API 上。
     */
    @Inject(method = "setWalkingSpeed", at = @At("RETURN"))
    private void cnpcplus$syncIntegerSpeed(int speed, CallbackInfo ci) {
        this.cnpcplus$moveSpeed = speed;
    }

    @Inject(method = "readToNBT", at = @At("TAIL"))
    private void cnpcplus$readFloatSpeed(CompoundTag tag, CallbackInfo ci) {
        float speed = tag.contains("CNPCPlusMoveSpeed")
                ? tag.getFloat("CNPCPlusMoveSpeed")
                : tag.getInt("MoveSpeed");
        // 下限是 0 而不是 0.01：speed=0 是原版合法值（NPC 静止），
        // 存档里存的 0 必须能原样读回，不能被当成非法值跳过或篡改。
        if (speed >= 0.0F) this.cnpcplus$setWalkingSpeed(speed);
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveFloatSpeed(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        CompoundTag result = cir.getReturnValue();
        result.putInt("MoveSpeed", Math.round(this.cnpcplus$moveSpeed));
        result.putFloat("CNPCPlusMoveSpeed", this.cnpcplus$moveSpeed);
    }

    @Override
    public float cnpcplus$getWalkingSpeed() {
        return this.cnpcplus$moveSpeed;
    }

    @Override
    public void cnpcplus$setWalkingSpeed(float speed) {
        if (!Float.isFinite(speed)) speed = 5.0F;
        // 下限 0 与原版 setWalkingSpeed(int) 一致（原版只拦负数和 >100）。
        // speed=0 表示 NPC 静止，是合法语义，不能钳成 0.01，
        // 否则脚本设 0 后 getSpeed() 返回 0.0005，表现为「罚站但又在微微挪动」。
        // GUI 输入框自己有 0.01 最小值限制，那是界面层的约束，不属于数据层。
        this.cnpcplus$moveSpeed = Math.max(0.0F, Math.min(100.0F, speed));
        this.moveSpeed = Math.round(this.cnpcplus$moveSpeed);
        float actual = this.cnpcplus$moveSpeed / 20.0F;
        this.npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(actual);
        this.npc.getAttribute(Attributes.FLYING_SPEED).setBaseValue(actual * 2.0F);
    }
}
