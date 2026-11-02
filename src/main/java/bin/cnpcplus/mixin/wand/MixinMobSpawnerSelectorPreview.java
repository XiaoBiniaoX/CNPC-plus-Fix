package bin.cnpcplus.mixin.wand;

import bin.cnpcplus.wand.PacketWandNpc;
import bin.cnpcplus.wand.client.NpcPreview;
import bin.cnpcplus.wand.client.PreviewReceiver;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.GuiNpcMobSpawnerSelector;
import noppes.npcs.client.gui.util.SubGuiInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GuiNpcMobSpawnerSelector.class, remap = false)
public abstract class MixinMobSpawnerSelectorPreview extends SubGuiInterface implements PreviewReceiver {
    @Shadow public int activeTab;
    @Shadow public boolean isServer;
    @Shadow public abstract String getSelected();
    @Shadow public abstract NBTTagCompound getCompound();
    @Unique private final NpcPreview cnpcplus$preview = new NpcPreview();
    @Inject(method = "func_73866_w_", at = @At("RETURN"), require = 1)
    private void cnpcplus$init(CallbackInfo ci) { cnpcplus$preview.clear(); }
    @Override public void drawScreen(int x, int y, float partial) {
        super.drawScreen(x, y, partial);
        cnpcplus$preview.select(isServer ? 2 : 0, 0, activeTab, getSelected(), this::getCompound);
        cnpcplus$preview.render(this, 211, 208, 1.1F);
    }
    @Override public void cnpcplus$receive(PacketWandNpc packet) { cnpcplus$preview.receive(packet); }
    @Inject(method = "save", at = @At("HEAD"), require = 1)
    private void cnpcplus$close(CallbackInfo ci) { cnpcplus$preview.clear(); }
}
