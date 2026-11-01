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

@Mixin(value = GuiQuestLog.class, remap = false)
public abstract class MixinGuiQuestLogStyledText {
    @Shadow public Quest selectedQuest;
    @Shadow private int maxPages;

    @Redirect(method = "m_7856_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/controllers/PlayerQuestController;getActiveQuests(Lnet/minecraft/world/entity/player/Player;)Ljava/util/Vector;"))
    private Vector<Quest> cnpcplus$previewQuests(Player player) {
        if ((Object)this instanceof PlayerTextPreview.QuestLog preview) return new Vector<>(java.util.List.of(preview.draft));
        return PlayerQuestController.getActiveQuests(player);
    }

    @Redirect(method = "drawProgress", at = @At(value = "INVOKE", target = "Lnoppes/npcs/quests/QuestInterface;getObjectives(Lnet/minecraft/world/entity/player/Player;)[Lnoppes/npcs/api/handler/data/IQuestObjective;"))
    private IQuestObjective[] cnpcplus$previewProgress(QuestInterface quest, Player player) {
        return (Object)this instanceof PlayerTextPreview.QuestLog ? new IQuestObjective[0] : quest.getObjectives(player);
    }

    @Redirect(method = "scrollClicked", at = @At(value = "NEW", target = "(Ljava/lang/String;IZ[Ljava/lang/Object;)Lnoppes/npcs/client/TextBlockClient;"), require = 1)
    private TextBlockClient cnpcplus$block(String text, int width, boolean mcFont, Object[] obs) {
        TextBlockClient block = new StyledTextBlock(TextStyles.get(selectedQuest, TextStyles.LOG, text), width, mcFont, obs);
        maxPages = Math.max(1, (block.lines.size() + 9) / 10);
        return block;
    }
}
