package top.cnpcplus.mixin;

import noppes.npcs.client.gui.player.moderngui.GuiDialogModern;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import top.cnpcplus.config.CnpcPlusConfigData;

@Mixin(value = GuiDialogModern.class, remap = false)
public class MixinGuiDialogModern {

    // === 1. NPC 在对话框中的显示位置与大小 ===
    @ModifyArg(method = "m_88315_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/moderngui/GuiDialogModern;drawNpc(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/LivingEntity;IIFIZ)V"), index = 2)
    private int cnpcplus$npcX(int x) { return CnpcPlusConfigData.DialogNpcPosX.get(); }

    @ModifyArg(method = "m_88315_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/moderngui/GuiDialogModern;drawNpc(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/LivingEntity;IIFIZ)V"), index = 3)
    private int cnpcplus$npcY(int y) { return CnpcPlusConfigData.DialogNpcPosY.get(); }

    @ModifyArg(method = "m_88315_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/client/gui/player/moderngui/GuiDialogModern;drawNpc(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/world/entity/LivingEntity;IIFIZ)V"), index = 4)
    private float cnpcplus$npcScale(float scale) { return CnpcPlusConfigData.DialogNpcScale.get().floatValue(); }

    // === 2. 选项整体缩放（改 wcoeff 让框+字一起放大） ===
    @ModifyVariable(method = "m_88315_", at = @At("STORE"), ordinal = 0)
    private double cnpcplus$optionScale(double wcoeff) {
        return wcoeff * CnpcPlusConfigData.DialogOptionScale.get().floatValue();
    }

    // ——— X/Y 补偿 + 位置配置 ———
    private float cnpcplus$optS() {
        return CnpcPlusConfigData.DialogOptionScale.get().floatValue();
    }

    @ModifyConstant(method = "m_88315_", constant = @Constant(intValue = 723))
    private int cnpcplus$boxX(int x) {
        float s = cnpcplus$optS();
        int posX = CnpcPlusConfigData.DialogOptionPosX.get();
        return s == 1.0f ? posX : (int)(posX / s + 0.5f);
    }

    @ModifyConstant(method = "m_88315_", constant = @Constant(intValue = 727))
    private int cnpcplus$symbolX(int x) {
        float s = cnpcplus$optS();
        int posX = CnpcPlusConfigData.DialogOptionPosX.get();
        int v = posX + 4;
        return s == 1.0f ? v : (int)(v / s + 0.5f);
    }

    @ModifyConstant(method = "m_88315_", constant = @Constant(intValue = 735))
    private int cnpcplus$textX(int x) {
        float s = cnpcplus$optS();
        int posX = CnpcPlusConfigData.DialogOptionPosX.get();
        int v = posX + 12;
        return s == 1.0f ? v : (int)(v / s + 0.5f);
    }

    @ModifyConstant(method = "m_88315_", constant = @Constant(intValue = 220))
    private int cnpcplus$baseY(int y) {
        float s = cnpcplus$optS();
        int posY = CnpcPlusConfigData.DialogOptionPosY.get();
        return s == 1.0f ? posY : (int)(posY / s + 0.5f);
    }

    // ——— Hit-test 跟位置走 ———
    @ModifyConstant(method = "m_88315_", constant = @Constant(doubleValue = 723.0))
    private double cnpcplus$hitLeft(double d) {
        float s = cnpcplus$optS();
        int posX = CnpcPlusConfigData.DialogOptionPosX.get();
        return s == 1.0f ? posX : posX / s;
    }

    @ModifyConstant(method = "m_88315_", constant = @Constant(doubleValue = 946.0))
    private double cnpcplus$hitRight(double d) {
        float s = cnpcplus$optS();
        int posX = CnpcPlusConfigData.DialogOptionPosX.get();
        double v = posX + 223.0;
        return s == 1.0f ? v : v / s;
    }

    // === 3. 选项间距 ===
    @ModifyConstant(method = "m_88315_", constant = @Constant(intValue = 19))
    private int cnpcplus$optionSpacing(int val) {
        return val + CnpcPlusConfigData.DialogOptionSpacing.get();
    }
}
