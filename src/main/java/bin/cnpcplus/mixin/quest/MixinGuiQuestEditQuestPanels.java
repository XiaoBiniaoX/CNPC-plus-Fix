package bin.cnpcplus.mixin.quest;

import bin.cnpcplus.client.gui.SubGuiQuestPicker;
import bin.cnpcplus.client.gui.SubGuiQuestSettings;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.translation.I18n;
import noppes.npcs.client.Client;
import noppes.npcs.client.gui.global.GuiQuestEdit;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.constants.EnumGuiType;
import noppes.npcs.constants.EnumPacketServer;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiQuestEdit.class, remap = false)
public abstract class MixinGuiQuestEditQuestPanels extends SubGuiInterface {
    @Shadow private Quest quest;

    @ModifyArg(method = "func_73866_w_", at = @At(value = "INVOKE",
            target = "Lnoppes/npcs/client/gui/util/GuiButtonBiDirectional;<init>(IIIII[Ljava/lang/String;I)V", ordinal = 0),
            index = 5, require = 1, remap = false)
    private String[] cnpcplus$allQuestTypes(String[] original) {
        return SubGuiQuestPicker.TYPES;
    }

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$questPanelButtons(CallbackInfo ci) {
        GuiNpcButton type = getButton(6);
        GuiNpcButton repeat = getButton(8);
        if (type != null) {
            buttonList.remove(type);
            addButton(new GuiNpcButton(6, type.x, type.y, type.width, type.height, SubGuiQuestPicker.TYPES[quest.type]));
        }
        if (repeat != null) {
            buttonList.remove(repeat);
            addButton(new GuiNpcButton(8, repeat.x, repeat.y, repeat.width, repeat.height, SubGuiQuestPicker.REPEATS[quest.repeat.ordinal()]));
        }
        if (quest.type == 6) {
            GuiNpcButton targets = getButton(7);
            if (targets != null) targets.setDisplayText(I18n.translateToLocal("cnpcplus.quest.targets"));
            addButton(new GuiNpcButton(100, guiLeft + 216, guiTop + 88, 82, 20, "cnpcplus.quest.sources"));
        }
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$questPanelClick(GuiButton raw, CallbackInfo ci) {
        if (raw.id == 6 || raw.id == 8) {
            setSubGui(new SubGuiQuestPicker(quest, raw.id == 8));
            ci.cancel();
        } else if (raw.id == 100) {
            setSubGui(new SubGuiQuestSettings(quest));
            ci.cancel();
        } else if (raw.id == 7 && quest.type == 6) {
            Client.sendData(EnumPacketServer.QuestOpenGui, EnumGuiType.QuestItem, quest.writeToNBT(new NBTTagCompound()));
            ci.cancel();
        } else if (raw.id == 7 && quest.type != 0 && quest.type != 1 && quest.type != 6) {
            setSubGui(new SubGuiQuestSettings(quest));
            ci.cancel();
        }
    }
}
