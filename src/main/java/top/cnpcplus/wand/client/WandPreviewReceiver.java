package top.cnpcplus.wand.client;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public final class WandPreviewReceiver {
    private WandPreviewReceiver() {}
    public static void accept(int id, long request, boolean copying, CompoundTag snapshot) {
        if (Minecraft.getInstance().screen instanceof GuiWandNpcList screen) {
            if (copying) screen.acceptCopy(request, snapshot);
            else screen.acceptPreview(id, request, snapshot);
        }
    }
    public static void freezeState(boolean global, boolean allowed, int[] ids) {
        if (Minecraft.getInstance().screen instanceof GuiWandNpcList screen) screen.acceptFreezeState(global, allowed, ids);
    }
}
