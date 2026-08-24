package top.cnpcplus.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;
import noppes.npcs.api.CustomNPCsException;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.entity.data.DataAI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.cnpcplus.ai.WalkingSpeedAccess;

@Mixin(value = DataAI.class, remap = false)
public class MixinDataAIWalkingSpeed implements WalkingSpeedAccess {
    @Shadow(remap = false) public EntityNPCInterface npc;

    /**
     * 小数速度覆盖值。-1 = 未设置，此时一律回落到原版 int 字段 moveSpeed。
     *
     * <p>初始值绝不能是某个「看起来合理」的速度（曾经是 5.0f）：本 mixin 会让
     * EntityNPCInterface.getSpeed() 改读这个字段，而任何没走过我们同步路径的 DataAI 实例
     * （脚本新建、克隆、旧存档读取）字段都还是初始值，于是速度被硬按成 5/20=0.25，NPC 罚站。
     * 用哨兵值表达「没有自定义值」，语义上才成立。
     */
    @Unique private float cnpcplus$walkingSpeed = -1.0f;

    /** 有小数覆盖值就用它，否则回落原版 moveSpeed（getWalkingSpeed 就是读那个字段）。 */
    @Override
    public float cnpcplus$getWalkingSpeed() {
        if (this.cnpcplus$walkingSpeed >= 0.01f) return this.cnpcplus$walkingSpeed;
        return ((DataAI) (Object) this).getWalkingSpeed();
    }

    @Override
    public void cnpcplus$setWalkingSpeed(float speed) {
        if (speed < 0.01f || speed > 100.0f) {
            throw new CustomNPCsException("Wrong speed: " + speed);
        }
        this.cnpcplus$walkingSpeed = speed;
        cnpcplus$applyAttributes(speed);
    }

    /** 把速度写进三条属性。抽出来是因为 setWalkingSpeed(int) 的同步路径也要用。 */
    @Unique
    private void cnpcplus$applyAttributes(float speed) {
        if (this.npc == null) return;
        float base = speed / 20.0f;
        this.npc.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(base);
        this.npc.getAttribute(ForgeMod.SWIM_SPEED.get()).setBaseValue(base * 32.0f);
        this.npc.getAttribute(Attributes.FLYING_SPEED).setBaseValue(base * 2.0f);
    }

    /**
     * 脚本 / GUI 走的是原版 INPCAi.setWalkingSpeed(int)，这里必须在 HEAD 同步。
     *
     * <p>不能用 RETURN：原版方法体内的顺序是「putfield moveSpeed → getAttribute → 回调
     * getSpeed() → setBaseValue」（javap 实证）。RETURN 注入发生在整个方法结束之后，
     * 那次中途的 getSpeed() 读到的还是旧值，属性就被设成错的。
     *
     * <p>整数入口意味着用户放弃了小数精度，所以清掉覆盖值让 getSpeed 回落原版字段，
     * 避免上一次设的小数值继续生效。
     */
    @Inject(method = "setWalkingSpeed", at = @At("HEAD"))
    private void cnpcplus$syncIntegerSpeed(int speed, CallbackInfo ci) {
        this.cnpcplus$walkingSpeed = -1.0f;
    }

    /**
     * 读档：只在存在小数键时才覆盖，且不抛异常。
     *
     * <p>原来直接调 cnpcplus$setWalkingSpeed，而它会 throw CustomNPCsException。
     * 读档路径上抛异常会中断整个 NBT 加载，旧存档里 MoveSpeed 为 0 或异常值时直接炸。
     * 这里改成静默忽略非法值，回落原版字段。
     */
    @Inject(method = "readToNBT", at = @At("RETURN"))
    private void cnpcplus$readFloatSpeed(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains("CNPCPlusMoveSpeed")) {
            this.cnpcplus$walkingSpeed = -1.0f;
            return;
        }
        float speed = tag.getFloat("CNPCPlusMoveSpeed");
        if (speed >= 0.01f && speed <= 100.0f) {
            this.cnpcplus$walkingSpeed = speed;
            cnpcplus$applyAttributes(speed);
        } else {
            this.cnpcplus$walkingSpeed = -1.0f;
        }
    }

    /**
     * 存档：只有存在小数覆盖值时才多写一个键，且不动原版已写好的 MoveSpeed。
     *
     * <p>原来无条件 `putInt("MoveSpeed", Math.round(自己的字段))`，在字段还是初始值时
     * 会把原版正确的整数速度覆盖成错的（旧世界因此被写坏）。没有小数值时什么都不写，
     * 存档与原版完全一致，向后兼容。
     */
    @Inject(method = "save", at = @At("RETURN"))
    private void cnpcplus$saveFloatSpeed(CompoundTag tag, CallbackInfoReturnable<CompoundTag> cir) {
        if (this.cnpcplus$walkingSpeed < 0.01f) return;
        tag.putInt("MoveSpeed", Math.round(this.cnpcplus$walkingSpeed));
        tag.putFloat("CNPCPlusMoveSpeed", this.cnpcplus$walkingSpeed);
    }
}
