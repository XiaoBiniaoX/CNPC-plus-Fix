package top.cnpcplus.mixin;

import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.client.gui.roles.GuiNpcBard;
import noppes.npcs.client.gui.select.GuiSoundSelection;
import noppes.npcs.roles.JobBard;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.IGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.bard.SongListStore;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = GuiNpcBard.class, remap = false)
public class MixinGuiNpcBard {

    @Shadow(remap = false)
    private JobBard job;

    @Inject(method = "m_7856_", at = @At("TAIL"))
    private void cnpcplus$songPanel(CallbackInfo ci) {
        GuiNpcBard self = (GuiNpcBard) (Object) this;
        List<String[]> songs = SongListStore.get(this.job);
        if (songs == null) {
            songs = new ArrayList<>();
            if (!this.job.song.isEmpty()) {
                songs.add(new String[]{this.job.song, "1"});
            }
            SongListStore.set(this.job, songs);
        }
        List<String> names = new ArrayList<>();
        for (String[] e : songs) {
            names.add(e[0]);
        }
        self.addLabel(new GuiLabel(12, "歌单", self.guiLeft + 220, self.guiTop + 2));
        GuiCustomScrollNop scroll = new GuiCustomScrollNop((Screen) self, 10);
        scroll.setSize(200, 140);
        scroll.guiLeft = self.guiLeft + 220;
        scroll.guiTop = self.guiTop + 15;
        scroll.setList(names);
        self.addScroll(scroll);
        self.addButton(new GuiButtonNop((IGuiInterface) self, 110, self.guiLeft + 220, self.guiTop + 158, 60, 20, "添加"));
        self.addButton(new GuiButtonNop((IGuiInterface) self, 111, self.guiLeft + 285, self.guiTop + 158, 60, 20, "删除"));
        self.addLabel(new GuiLabel(13, "新曲权重", self.guiLeft + 220, self.guiTop + 182));
        GuiTextFieldNop tf = new GuiTextFieldNop(10, (Screen) self, self.guiLeft + 300, self.guiTop + 178, 40, 20, "1");
        self.addTextField(tf);
        tf.numbersOnly = true;
        cnpcplus$rearrange(self);
    }

    @Unique
    private void cnpcplus$rearrange(GuiNpcBard self) {
        GuiButtonNop sel = self.getButton(0);
        if (sel != null) sel.shown = false;
        GuiButtonNop x = self.getButton(1);
        if (x != null) x.shown = false;
        GuiLabel songLabel = self.getLabel(0);
        if (songLabel != null) {
            songLabel.setMessage(net.minecraft.network.chat.Component.empty());
        }
        for (int id : new int[]{3, 4, 6}) {
            GuiButtonNop b = self.getButton(id);
            if (b == null) continue;
            net.minecraft.client.gui.components.AbstractWidget w = b;
            w.setX(w.getX() - 20);
            if (id == 3) {
                ((AbstractWidgetAccess) b).cnpcplus$setWidth(130);
            }
        }
        for (int id : new int[]{0, 6, 2, 4, 3}) {
            GuiLabel l = self.getLabel(id);
            if (l == null) continue;
            net.minecraft.client.gui.components.AbstractWidget w = l;
            w.setX(w.getX() - 20);
        }
        for (int id : new int[]{2, 3}) {
            GuiTextFieldNop t = self.getTextField(id);
            if (t == null) continue;
            net.minecraft.client.gui.components.AbstractWidget w = t;
            w.setX(w.getX() - 20);
        }
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true)
    private void cnpcplus$onButton(GuiButtonNop button, CallbackInfo ci) {
        if (button.id == 110) {
            ((GuiNpcBard) (Object) this).setSubGui(new GuiSoundSelection(""));
            ci.cancel();
        } else if (button.id == 111) {
            GuiNpcBard self = (GuiNpcBard) (Object) this;
            GuiCustomScrollNop scroll = self.getScroll(10);
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
            self.m_7856_();
            ci.cancel();
        }
    }

    @Inject(method = "subGuiClosed", at = @At("TAIL"))
    private void cnpcplus$addFromSelection(Screen subgui, CallbackInfo ci) {
        GuiSoundSelection gss = (GuiSoundSelection) subgui;
        if (gss.selectedResource == null) return;
        String song = gss.selectedResource.toString();
        GuiNpcBard self = (GuiNpcBard) (Object) this;
        List<String[]> songs = SongListStore.get(this.job);
        if (songs == null) {
            songs = new ArrayList<>();
            SongListStore.set(this.job, songs);
        }
        for (String[] e : songs) {
            if (e[0].equals(song)) return;
        }
        GuiTextFieldNop tf = self.getTextField(10);
        int weight = 1;
        if (tf != null && tf.getInteger() > 0) {
            weight = tf.getInteger();
        }
        songs.add(new String[]{song, String.valueOf(weight)});
        self.m_7856_();
    }
}
