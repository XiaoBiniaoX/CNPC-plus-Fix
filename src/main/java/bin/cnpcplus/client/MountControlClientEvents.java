package bin.cnpcplus.client;

import bin.cnpcplus.craftingview.network.CraftingViewNetwork;
import bin.cnpcplus.craftingview.network.PacketMountControlInput;
import bin.cnpcplus.common.IMountControlData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.Mod;
import noppes.npcs.entity.EntityNPCInterface;

@Mod.EventBusSubscriber(value = Side.CLIENT)
public final class MountControlClientEvents {
    private static float lastStrafe;
    private static float lastForward;
    private static boolean lastJump;
    private static boolean lastSneak;
    private static boolean wasRiding;

    private MountControlClientEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null) {
            wasRiding = false;
            return;
        }
        if (!(mc.player.getRidingEntity() instanceof EntityNPCInterface)) {
            wasRiding = false;
            return;
        }
        EntityNPCInterface npc = (EntityNPCInterface) mc.player.getRidingEntity();
        if (npc.ais == null
                || !((IMountControlData) (Object) npc.ais).cnpcplus$getMountControl()) {
            wasRiding = false;
            return;
        }
        KeyBinding forward = mc.gameSettings.keyBindForward;
        KeyBinding back = mc.gameSettings.keyBindBack;
        KeyBinding left = mc.gameSettings.keyBindLeft;
        KeyBinding right = mc.gameSettings.keyBindRight;
        float f = (forward.isKeyDown() ? 1.0f : 0.0f) - (back.isKeyDown() ? 1.0f : 0.0f);
        float s = (left.isKeyDown() ? 1.0f : 0.0f) - (right.isKeyDown() ? 1.0f : 0.0f);
        boolean jump = mc.gameSettings.keyBindJump.isKeyDown();
        boolean sneak = mc.gameSettings.keyBindSneak.isKeyDown();
        // Only send when the input actually changes, so idle riding costs no traffic.
        if (wasRiding && f == lastForward && s == lastStrafe
                && jump == lastJump && sneak == lastSneak) {
            return;
        }
        lastForward = f;
        lastStrafe = s;
        lastJump = jump;
        lastSneak = sneak;
        wasRiding = true;
        CraftingViewNetwork.CHANNEL.sendToServer(new PacketMountControlInput(s, f, jump, sneak));
    }
}
