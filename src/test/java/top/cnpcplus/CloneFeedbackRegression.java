package top.cnpcplus;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.Bootstrap;
import noppes.npcs.controllers.ServerCloneController;
import noppes.npcs.packets.PacketServerBasic;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Executes both production feedback handlers; synchronous save and chat transport are test boundaries. */
public final class CloneFeedbackRegression {
    private static final String PAGE = "top/cnpcplus/CloneFeedbackRegression$Page";
    private static final String HOOK = "top/cnpcplus/CloneFeedbackRegression";
    private static boolean succeed;
    private static int saves;
    private static final List<ServerPlayer> recipients = new ArrayList<>();
    private static final List<Component> messages = new ArrayList<>();
    public static String save(ServerCloneController controller, CompoundTag data, String name, int tab) {
        saves++;
        if (succeed) controller.lastLoaded++;
        return name;
    }
    public static void report(ServerPlayer player, Component message) { recipients.add(player); report(message); }
    public static void report(Component message) { messages.add(message); }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        try { Bootstrap.bootStrap(); }
        catch (ExceptionInInitializerError unavailable) {
            Throwable cause = unavailable;
            while (cause.getCause() != null) cause = cause.getCause();
            if (!(cause instanceof NoSuchMethodException) || !cause.getMessage().equals("net.minecraftforge.network.NetworkEvent.<init>()")) throw unavailable;
            System.out.println("HEADLESS_ONLY: no ModLauncher event transforms");
        }
        ClassLoader loader = new ClassLoader(CloneFeedbackRegression.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                String path = name.replace('.', '/');
                if (!path.equals(PAGE) && !path.equals("noppes/npcs/client/controllers/ClientCloneController")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) try (var input = getParent().getResourceAsStream(path + ".class")) {
                        ClassNode node = new ClassNode(); new ClassReader(input).accept(node, 0);
                        if (!path.equals(PAGE)) node.methods.removeIf(m -> m.name.equals("<clinit>"));
                        else for (String source : List.of("MixinCloneSaveFeedback", "MixinClientCloneSaveFeedback")) {
                            ClassNode mixin = new ClassNode();
                            try (var bytes = getParent().getResourceAsStream("top/cnpcplus/mixin/" + source + ".class")) { new ClassReader(bytes).accept(mixin, 0); }
                            MethodNode handler = mixin.methods.stream().filter(m -> m.name.equals("cnpcplus$reportFailure")).findFirst().orElseThrow();
                            handler.name = source.equals("MixinCloneSaveFeedback") ? "server" : "client";
                            for (var instruction : handler.instructions.toArray()) if (instruction instanceof MethodInsnNode call) {
                                if (call.name.equals("addClone")) handler.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "save", "(Lnoppes/npcs/controllers/ServerCloneController;Lnet/minecraft/nbt/CompoundTag;Ljava/lang/String;I)Ljava/lang/String;", false));
                                else if (call.owner.equals("net/minecraft/client/Minecraft") && call.name.equals("getInstance")) {
                                    var gui = call.getNext(); var chat = gui.getNext(); handler.instructions.remove(chat); handler.instructions.remove(gui); handler.instructions.remove(call);
                                } else if (call.name.equals("sendSystemMessage")) handler.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "report", "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/Component;)V", false));
                                else if (call.name.equals("addMessage")) handler.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "report", "(Lnet/minecraft/network/chat/Component;)V", false));
                            }
                            node.methods.add(handler);
                        }
                        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); byte[] bytes = writer.toByteArray(); loaded = defineClass(name, bytes, 0, bytes.length);
                    } catch (Exception e) { throw new ClassNotFoundException(name, e); }
                    if (resolve) resolveClass(loaded); return loaded;
                }
            }
        };
        Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); f.setAccessible(true); var unsafe = (sun.misc.Unsafe)f.get(null);
        Class<?> pageType = loader.loadClass(PAGE.replace('/', '.'));
        PacketServerBasic first = (PacketServerBasic)unsafe.allocateInstance(pageType), second = (PacketServerBasic)unsafe.allocateInstance(pageType);
        first.player = (ServerPlayer)unsafe.allocateInstance(ServerPlayer.class); second.player = (ServerPlayer)unsafe.allocateInstance(ServerPlayer.class);
        ServerCloneController server = (ServerCloneController)unsafe.allocateInstance(ServerCloneController.class);
        Class<?> clientType = loader.loadClass("noppes.npcs.client.controllers.ClientCloneController");
        ServerCloneController client = (ServerCloneController)unsafe.allocateInstance(clientType);
        var onServer = pageType.getDeclaredMethod("server", ServerCloneController.class, CompoundTag.class, String.class, int.class); onServer.setAccessible(true);
        var onClient = pageType.getDeclaredMethod("client", clientType, CompoundTag.class, String.class, int.class); onClient.setAccessible(true);
        CompoundTag data = new CompoundTag(); data.putString("Name", "骑兵"); CompoundTag before = data.copy();
        succeed = true; check(onServer.invoke(first, server, data, "骑兵", 2).equals("骑兵") && messages.isEmpty(), "successful server save warned or changed result");
        succeed = false; onServer.invoke(second, server, data, "骑兵", 2);
        check(messages.size() == 1 && recipients.equals(List.of(second.player)), "failure warned another player");
        succeed = true; onServer.invoke(first, server, data, "骑兵", 2); onClient.invoke(first, client, data, "骑兵", 2);
        check(messages.size() == 1, "successful retry retained shared failure state");
        succeed = false; check(onClient.invoke(first, client, data, "骑兵", 2).equals("骑兵") && messages.size() == 2, "local failure missing or changed native result");
        succeed = true; onClient.invoke(first, client, data, "骑兵", 2);
        check(messages.size() == 2 && saves == 6 && data.equals(before), "duplicate saves, changed data, or failed local retry");
        for (Component message : messages) check(message.getContents() instanceof TranslatableContents text && text.getKey().equals("cnpcplus.clone.saveFailed")
                && message.getStyle().getColor().getValue() == ChatFormatting.RED.getColor(), "feedback lost translation/red style");
        System.out.println("CLONE_FEEDBACK_REGRESSION=PASS (both production handlers; per-call success/failure/retry/recipient/style; save/chat test shells, not network/game/Mixin)");
    }
    public static final class Page extends PacketServerBasic { @Override protected void handle() {} }
    private static void check(boolean good, String message) { if (!good) throw new AssertionError(message); }
}
