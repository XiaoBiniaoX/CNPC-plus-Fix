package bin.cnpcplus.bossbar.client;

import bin.cnpcplus.bossbar.BossBarData;
import bin.cnpcplus.bossbar.BossBarSettings;
import bin.cnpcplus.client.gui.SubGuiColorWheel;
import net.minecraft.client.gui.GuiButton;
import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.client.gui.util.GuiNpcButton;

public final class SubGuiBossBar extends SubGuiColorWheel {
    private final GuiNpcDisplay display;
    private int mode;
    public SubGuiBossBar(GuiNpcDisplay display) {
        super(BossBarData.get(display.npc.display).rgb, null); this.display = display; mode = BossBarData.get(display.npc.display).mode;
    }
    @Override public void initGui() {
        super.initGui();
        addButton(new GuiNpcButton(0, guiLeft + 154, guiTop + 34, 128, 20,
                new String[]{"cnpcplus.bossbar.off", "cnpcplus.bossbar.always", "cnpcplus.bossbar.attacking"}, mode));
    }
    @Override public void buttonEvent(GuiButton button) { if (button.id == 0) mode = ((GuiNpcButton) button).getValue(); else super.buttonEvent(button); }
    @Override public void save() { super.save(); BossBarData.put(display.npc, new BossBarSettings(mode, rgb)); display.save(); }
}
