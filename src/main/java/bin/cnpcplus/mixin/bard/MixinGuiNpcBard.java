package bin.cnpcplus.mixin.bard;

import bin.cnpcplus.bard.SongListStore;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import noppes.npcs.client.gui.roles.GuiNpcBard;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.client.gui.util.GuiCustomScroll;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.client.gui.util.GuiNpcButton;
import noppes.npcs.client.gui.util.GuiNpcLabel;
import noppes.npcs.client.gui.util.GuiNpcTextField;
import noppes.npcs.client.gui.util.SubGuiInterface;
import noppes.npcs.roles.JobBard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Playlist panel on the right side of GuiNpcBard (xSize=420, right side free):
 * song scroll (id 10), add (110) / delete (111) buttons, weight field,
 * and hide the vanilla single-song controls (buttons 0/1, label 0).
 */
@Mixin(value = GuiNpcBard.class, remap = false)
public class MixinGuiNpcBard {

    @Shadow(remap = false)
    private JobBard job;

    @Inject(method = "func_73866_w_", at = @At("TAIL"), remap = false)
    private void cnpcplus$songPanel(CallbackInfo ci) {
        GuiNpcBard self = (GuiNpcBard) (Object) this;
        List<String[]> songs = SongListStore.get(this.job);
        if (songs == null) {
            songs = new ArrayList<String[]>();
            if (!this.job.song.isEmpty()) {
                songs.add(new String[]{this.job.song, "1"});
            }
            SongListStore.set(this.job, songs);
        }
        List<String> names = new ArrayList<String>();
        for (String[] e : songs) {
            names.add(e[0]);
        }
        self.addLabel(new GuiNpcLabel(12, "cnpcplus.bard.songlist", self.guiLeft + 220, self.guiTop + 2));
        GuiCustomScroll scroll = new GuiCustomScroll((GuiScreen) self, 10);
        scroll.setSize(200, 140);
        scroll.guiLeft = self.guiLeft + 220;
        scroll.guiTop = self.guiTop + 15;
        scroll.setList(names);
        self.addScroll(scroll);
        self.addButton(new GuiNpcButton(110, self.guiLeft + 220, self.guiTop + 158, 60, 20, "cnpcplus.bard.add"));
        self.addButton(new GuiNpcButton(111, self.guiLeft + 285, self.guiTop + 158, 60, 20, "cnpcplus.bard.delete"));
        self.addLabel(new GuiNpcLabel(13, "cnpcplus.bard.weight", self.guiLeft + 220, self.guiTop + 182));
        GuiNpcTextField tf = new GuiNpcTextField(10, (GuiScreen) self, self.guiLeft + 300, self.guiTop + 178, 40, 20, "1");
        self.addTextField(tf);
        tf.setNumbersOnly();
        GuiNpcButton b0 = self.getButton(0);
        if (b0 != null) b0.setVisible(false);
        GuiNpcButton b1 = self.getButton(1);
        if (b1 != null) b1.setVisible(false);
        GuiNpcLabel l0 = self.getLabel(0);
        if (l0 != null) l0.label = "";
        cnpcplus$rearrange(self);
    }

    /** Vanilla streamer toggle (button 3, "像唱片机一样播放") is too wide;
     *  1.20.1 blueprint: shift left 20, widen to 130; same for the rest. */
    @Unique
    private void cnpcplus$rearrange(GuiNpcBard self) {
        GuiNpcButton b3 = self.getButton(3);
        if (b3 != null) {
            b3.x -= 20;
            b3.width = 130;
        }
        GuiNpcButton b4 = self.getButton(4);
        if (b4 != null) b4.x -= 20;
        for (int id : new int[]{0, 2, 4, 3}) {
            GuiNpcLabel l = self.getLabel(id);
            if (l != null) l.x -= 20;
        }
        for (int id : new int[]{2, 3}) {
            GuiNpcTextField t = self.getTextField(id);
            if (t != null) t.x -= 20;
        }
    }

    @Inject(method = "func_146284_a", at = @At("HEAD"), cancellable = true, remap = false)
    private void cnpcplus$onButton(GuiButton button, CallbackInfo ci) {
        if (button.id == 110) {
            GuiNPCInterface base = (GuiNPCInterface) (Object) this;
            base.setSubGui(new GuiSoundSelection(""));
            ci.cancel();
        } else if (button.id == 111) {
            GuiNpcBard self = (GuiNpcBard) (Object) this;
            GuiCustomScroll scroll = self.getScroll(10);
            List<String[]> songs = SongListStore.get(this.job);
            if (scroll != null && songs != null && scroll.getSelected() != null) {
                String selected = scroll.getSelected();
                for (int i = 0; i < songs.size(); i++) {
                    if (songs.get(i)[0].equals(selected)) {
                        songs.remove(i);
                        break;
                    }
                }
            }
            self.func_73866_w_();
            ci.cancel();
        }
    }

    @Inject(method = "subGuiClosed", at = @At("TAIL"), remap = false)
    private void cnpcplus$addFromSelection(SubGuiInterface subgui, CallbackInfo ci) {
        if (!(subgui instanceof GuiSoundSelection)) return;
        GuiSoundSelection gss = (GuiSoundSelection) subgui;
        if (gss.selectedResource == null) return;
        String song = gss.selectedResource.toString();
        GuiNpcBard self = (GuiNpcBard) (Object) this;
        List<String[]> songs = SongListStore.get(this.job);
        if (songs == null) {
            songs = new ArrayList<String[]>();
            SongListStore.set(this.job, songs);
        }
        for (String[] e : songs) {
            if (e[0].equals(song)) return;
        }
        GuiNpcTextField tf = self.getTextField(10);
        int weight = 1;
        if (tf != null && tf.getInteger() > 0) {
            weight = tf.getInteger();
        }
        songs.add(new String[]{song, String.valueOf(weight)});
        self.func_73866_w_();
    }
}