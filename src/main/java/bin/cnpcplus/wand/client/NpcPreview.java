package bin.cnpcplus.wand.client;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.wand.PacketWandNpc;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.nbt.NBTTagCompound;
import noppes.npcs.client.gui.util.GuiNPCInterface;
import org.lwjgl.opengl.GL11;

/** One detached preview per list; selection identity and request token isolate delayed replies. */
public final class NpcPreview {
    private int mode = -1, tab, id;
    private String name;
    private long request, opened;
    private EntityLivingBase entity;
    public void clear() { mode = -1; name = null; request = 0; entity = null; }
    public void select(int mode, int id, int tab, String name, Supplier<NBTTagCompound> local) {
        if (this.mode == mode && this.id == id && this.tab == tab && Objects.equals(this.name, name)) return;
        this.mode = mode; this.id = id; this.tab = tab; this.name = name; entity = null;
        opened = request = System.nanoTime();
        if (name == null) return;
        if (mode == 2 || mode == 3)
            CraftingViewNetwork.CHANNEL.sendToServer(new PacketWandNpc(id, mode, request, tab, name));
        else load(local.get());
    }
    private void load(NBTTagCompound tag) {
        Entity value = tag == null ? null : EntityList.createEntityFromNBT(tag.copy(), Minecraft.getMinecraft().world);
        entity = value instanceof EntityLivingBase ? (EntityLivingBase) value : null;
    }
    public void receive(PacketWandNpc packet) {
        if (packet.action == mode && packet.id == id && packet.tab == tab && packet.request == request && Objects.equals(packet.name, name))
            load(packet.snapshot);
    }
    public void render(GuiNPCInterface gui, int x, int y, float zoom) {
        if (entity == null) return;
        int oldX = gui.mouseX, oldY = gui.mouseY;
        gui.mouseX = gui.guiLeft + x;
        gui.mouseY = Math.round(gui.guiTop + y - 50F * (entity.height > 2.4F ? 2F / entity.height : 1F) * zoom);
        try { gui.drawNpc(entity, x, y, zoom, (int) ((System.nanoTime() - opened) / 1_000_000_000.0 * 12) % 360); }
        finally { gui.mouseX = oldX; gui.mouseY = oldY; }
    }
    public void render(GuiNPCInterface gui, int x, int y, int width, int height) {
        GuiNPCInterface.drawRect(gui.guiLeft + x, gui.guiTop + y, gui.guiLeft + x + width, gui.guiTop + y + height, 0xFF202020);
        if (entity == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        double fx = mc.displayWidth / (double) gui.width, fy = mc.displayHeight / (double) gui.height;
        float normalized = entity.height > 2.4F ? 2F / entity.height : 1F;
        float zoom = Math.min((height - 10) / (30F * entity.height * normalized), (width - 8) / (30F * entity.width * normalized));
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor((int) ((gui.guiLeft + x) * fx), mc.displayHeight - (int) ((gui.guiTop + y + height) * fy), (int) (width * fx), (int) (height * fy));
        try { render(gui, x + width / 2, y + height - 4, zoom); }
        finally { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
    }
}
