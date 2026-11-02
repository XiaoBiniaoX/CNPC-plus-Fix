package top.cnpcplus.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import noppes.npcs.client.gui.GuiNpcMobSpawner;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiCustomScrollNop;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.cnpcplus.clone.client.*;

@Mixin(value = GuiNpcMobSpawner.class, remap = false)
public abstract class MixinGuiMobSpawnerPreview extends GuiNPCInterface implements ClonePreviewHost {
    @Shadow private GuiCustomScrollNop scroll;
    @Shadow private int activeTab;
    @Shadow private static int showingClones;
    @Unique private ClonePreview cnpcplus$preview;
    @Override public ClonePreview cnpcplus$clonePreview() { return cnpcplus$preview; }
    @Inject(method = "m_7856_", at = @At("TAIL"), require = 1)
    private void cnpcplus$init(CallbackInfo ci) {
        if (cnpcplus$preview == null) cnpcplus$preview = new ClonePreview();
        else cnpcplus$preview.clear();
    }
    @Override public void m_88315_(GuiGraphics g, int mx, int my, float partial) {
        super.m_88315_(g, mx, my, partial);
        if (cnpcplus$preview == null) return;
        if (hasSubGui()) { cnpcplus$preview.clear(); return; }
        cnpcplus$preview.select(showingClones, activeTab, showingClones == 1 || scroll == null ? null : scroll.getSelected());
        cnpcplus$preview.render(this, g, guiLeft + 171, guiTop + 128, 80, 85);
    }
    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void cnpcplus$close(CallbackInfo ci) { if (cnpcplus$preview != null) cnpcplus$preview.clear(); }
    @Override public void removed() { if (cnpcplus$preview != null) cnpcplus$preview.clear(); super.removed(); }
    @Inject(method = "setGuiData", at = @At("HEAD"), cancellable = true, require = 1)
    private void cnpcplus$listTab(CompoundTag data, CallbackInfo ci) {
        if (showingClones != 2 || data == null || data.getInt("CNPCPlusCloneListTab") != activeTab) ci.cancel();
        else if (scroll != null) scroll.clearSelection();
    }
}
