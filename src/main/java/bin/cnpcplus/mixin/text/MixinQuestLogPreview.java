package bin.cnpcplus.mixin.text;

import bin.cnpcplus.text.client.PlayerTextPreview;
import java.util.List;
import java.util.Vector;
import net.minecraft.entity.player.EntityPlayer;
import noppes.npcs.client.gui.player.GuiQuestLog;
import noppes.npcs.controllers.PlayerQuestController;
import noppes.npcs.controllers.data.Quest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = GuiQuestLog.class, remap = false)
public abstract class MixinQuestLogPreview {
    @Redirect(method = "func_73866_w_", at = @At(value = "INVOKE", target = "Lnoppes/npcs/controllers/PlayerQuestController;getActiveQuests(Lnet/minecraft/entity/player/EntityPlayer;)Ljava/util/Vector;"), require = 1)
    private Vector<Quest> cnpcplus$draftOnly(EntityPlayer player) {
        if (!((Object) this instanceof PlayerTextPreview.LogPage)) return PlayerQuestController.getActiveQuests(player);
        Vector<Quest> quests = new Vector<>();
        quests.add(((PlayerTextPreview.LogPage) (Object) this).draft); return quests;
    }
    @Redirect(method = "func_73866_w_", at = @At(value = "INVOKE", target = "Lmicdoodle8/mods/galacticraft/api/client/tabs/TabRegistry;addTabsToList(Ljava/util/List;)V"), require = 1)
    private void cnpcplus$noNavigation(List buttons) {
        if (!((Object) this instanceof PlayerTextPreview.LogPage)) micdoodle8.mods.galacticraft.api.client.tabs.TabRegistry.addTabsToList(buttons);
    }
    @Redirect(method = "func_73866_w_", at = @At(value = "INVOKE", target = "Lmicdoodle8/mods/galacticraft/api/client/tabs/TabRegistry;updateTabValues(IILjava/lang/Class;)V"), require = 1)
    private void cnpcplus$noSharedTabChanges(int x, int y, Class<?> selected) {
        if (!((Object) this instanceof PlayerTextPreview.LogPage)) micdoodle8.mods.galacticraft.api.client.tabs.TabRegistry.updateTabValues(x, y, selected);
    }
}
