package top.cnpcplus.mixin;

import noppes.npcs.controllers.QuestController;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * P1-C（中）：/noppes quest reload 的 new QuestController() 构造器立即
 * instance=this，load() 先 clear 再重建——reload 期间玩家复活/进服读到
 * 空 quests，进行中任务被静默跳过。
 * 修法：构造器不再抢占 instance；load() 完成后才提交 instance。
 * 启动路径 QuestController.instance.load() 不受影响（静态字段初始化本身
 * 会在构造器返回后把新对象赋给 instance，与构造器内赋值等效）。
 */
@Mixin(value = QuestController.class, remap = false)
public abstract class MixinQuestControllerAtomicLoad {

    @Redirect(method = "<init>", at = @At(value = "FIELD",
            target = "Lnoppes/npcs/controllers/QuestController;instance:Lnoppes/npcs/controllers/QuestController;",
            opcode = Opcodes.PUTSTATIC), remap = false)
    private void cnpcplus$deferInstanceAssign(QuestController value) {
        // 构造期不替换 instance，避免 load 中途读到空表
        // 注意：静态字段初始化 instance = new QuestController() 在构造器返回后仍会 putstatic
    }

    @Inject(method = "load", at = @At("RETURN"), remap = false)
    private void cnpcplus$commitInstance(CallbackInfo ci) {
        QuestController.instance = (QuestController) (Object) this;
    }
}
