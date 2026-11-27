package top.cnpcplus.mixin;

import noppes.npcs.client.TextBlockClient;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import top.cnpcplus.text.TextStyles;
import top.cnpcplus.text.client.StyledTextBlock;
import top.cnpcplus.text.client.PlayerTextPreview;
import java.util.Vector;
import net.minecraft.world.entity.player.Player;
import noppes.npcs.controllers.PlayerQuestController;
import noppes.npcs.quests.QuestInterface;
import noppes.npcs.api.handler.data.IQuestObjective;
import noppes.npcs.client.CustomNpcResourceListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.client.gui.QuestObjectiveScrollAccess;
import top.cnpcplus.quest.QuestCraft;

@Mixin(value = GuiQuestLog.class, remap = false)
public abstract class MixinGuiQuestLogStyledText implements QuestObjectiveScrollAccess {
    @Shadow public Quest selectedQuest;
    @Shadow private int maxPages;
    @Shadow private Player player;
    @Unique private int cnpcplus$craftObjectiveScroll;
    @Unique private Quest cnpcplus$objectiveQuest;

    @Override public int cnpcplus$getCraftObjectiveScroll() { return cnpcplus$craftObjectiveScroll; }
    @Override public void cnpcplus$setCraftObjectiveScroll(int offset) { cnpcplus$craftObjectiveScroll = offset; }

    @Redirect(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/controllers/PlayerQuestController;getActiveQuests(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Vector;"))
    private Vector<Quest> cnpcplus$previewQuests(Player player) {
        if ((Object)this instanceof PlayerTextPreview.QuestLog preview) return new Vector<>(java.util.List.of(preview.draft));
        return PlayerQuestController.getActiveQuests(player);
    }

    @Redirect(method = "drawProgress", at = @At(value = "INVOKE", target = "Lnoppes/npcs/quests/QuestInterface;getObjectives(Lnet/minecraft/world/entity/player/Player;)[Lnoppes/npcs/api/handler/data/IQuestObjective;"))
    private IQuestObjective[] cnpcplus$previewProgress(QuestInterface quest, Player player) {
        return (Object)this instanceof PlayerTextPreview.QuestLog ? new IQuestObjective[0] : quest.getObjectives(player);
    }

    @Inject(method = "drawProgress", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void cnpcplus$drawCraftObjectives(GuiGraphics graphics, CallbackInfo ci) {
        if (selectedQuest == null || !(selectedQuest.questInterface instanceof QuestCraft craft)
                || (Object)this instanceof PlayerTextPreview.QuestLog) return;
        if (cnpcplus$objectiveQuest != selectedQuest) {
            cnpcplus$objectiveQuest = selectedQuest;
            cnpcplus$craftObjectiveScroll = 0;
        }
        IQuestObjective[] objectives = craft.getObjectives(player);
        cnpcplus$craftObjectiveScroll = Math.max(0,
                Math.min(cnpcplus$craftObjectiveScroll, Math.max(0, objectives.length - 3)));
        GuiQuestLog gui = (GuiQuestLog)(Object)this;
        int left = gui.guiLeft + 142, top = gui.guiTop;
        int color = CustomNpcResourceListener.getDefaultTextColor();
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, net.minecraft.network.chat.Component.translatable("quest.objectives").append(":"),
                left, top + 132, color, false);
        graphics.fill(left, top + 142, left + 170, top + 143, 0xff606060);
        int rowTop = top + 146;
        graphics.enableScissor(left, rowTop, left + (objectives.length > 3 ? 166 : 170), top + 178);
        for (int row = 0; row < 3; row++) {
            int index = cnpcplus$craftObjectiveScroll + row;
            if (index >= objectives.length) break;
            graphics.drawString(font, net.minecraft.network.chat.Component.literal("- ")
                            .append(objectives[index].getMCText()), left, rowTop + row * 10, color, false);
        }
        graphics.disableScissor();
        if (objectives.length > 3) {
            int trackHeight = 32;
            int thumbHeight = Math.max(6, trackHeight * 3 / objectives.length);
            int thumbTop = rowTop + (trackHeight - thumbHeight) * cnpcplus$craftObjectiveScroll
                    / (objectives.length - 3);
            graphics.fill(left + 167, rowTop, left + 170, rowTop + trackHeight, 0xff777777);
            graphics.fill(left + 167, thumbTop, left + 170, thumbTop + thumbHeight, 0xff303030);
        }
        graphics.fill(left, top + 182, left + 170, top + 183, 0xff606060);
        String complete = selectedQuest.getNpcName();
        if (complete != null && !complete.isEmpty()) {
            graphics.drawString(font, net.minecraft.network.chat.Component.translatable("quest.completewith",
                    net.minecraft.network.chat.Component.translatable(complete)), left, top + 186, color, false);
        }
        ci.cancel();
    }

    @Redirect(method = "scrollClicked", at = @At(value = "NEW", target = "(Ljava/lang/String;IZ[Ljava/lang/Object;)Lnoppes/npcs/client/TextBlockClient;"), require = 1)
    private TextBlockClient cnpcplus$block(String text, int width, boolean mcFont, Object[] obs) {
        TextBlockClient block = new StyledTextBlock(TextStyles.get(selectedQuest, TextStyles.LOG, text), width, mcFont, obs);
        maxPages = Math.max(1, (block.lines.size() + 9) / 10);
        return block;
    }
}
