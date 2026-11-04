package top.cnpcplus.shader;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.util.thread.SidedThreadGroups;

/** Dist/thread gate; a server script never classloads the renderer or creates a GPU object. */
public final class ShaderRuntime {
    private ShaderRuntime() {}
    public static boolean client() { return Thread.currentThread().getThreadGroup() != SidedThreadGroups.SERVER
            && net.minecraftforge.fml.loading.FMLEnvironment.dist == Dist.CLIENT; }
    public static ShaderManager manager() { return client() ? DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.MANAGER) : ShaderManager.SERVER; }
    public static boolean isLoaded(String id) { return client() && DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.isLoaded(id)); }
    public static boolean load(String id) { return client() && DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.load(id)); }
    public static boolean unload(String id) { return client() && DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.unload(id)); }
    public static boolean reload(String id) { return client() && DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.reload(id)); }
    public static void reloadAll() { if (client()) DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.reloadAll()); }
    public static String[] loaded() { return client() ? DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> top.cnpcplus.shader.client.ClientShaders.loaded()) : new String[0]; }
}
