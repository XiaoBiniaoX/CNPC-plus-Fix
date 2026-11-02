package top.cnpcplus.bossbar.client;

import noppes.npcs.client.gui.mainmenu.GuiNpcDisplay;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import top.cnpcplus.bossbar.BossBarData;
import top.cnpcplus.bossbar.BossBarSettings;
import top.cnpcplus.client.gui.SubGuiColorWheel;

/** Same native color wheel as marks; boss mode and save authority stay on the display page. */
public final class SubGuiBossBar extends SubGuiColorWheel {
    private final GuiNpcDisplay parent;
    private int mode;

    public SubGuiBossBar(GuiNpcDisplay parent) {
        super(BossBarData.get(parent.npc.display).rgb(), null);
        this.parent = parent;
        mode = BossBarData.get(parent.npc.display).mode();
    }

    @Override public void m_7856_() {
        super.m_7856_();
        addButton(new GuiButtonNop(this, 0, guiLeft + 154, guiTop + 34, 128, 20,
                new String[]{"cnpcplus.bossbar.off", "cnpcplus.bossbar.always", "cnpcplus.bossbar.attacking"}, mode));
    }

    @Override public void buttonEvent(GuiButtonNop button) {
        if (button.id == 0) mode = button.getValue();
        else super.buttonEvent(button);
    }

    @Override public void save() {
        try {
            BossBarData.put(parent.npc, new BossBarSettings(mode, rgb));
            parent.save();
        } finally { super.save(); }
    }
}
