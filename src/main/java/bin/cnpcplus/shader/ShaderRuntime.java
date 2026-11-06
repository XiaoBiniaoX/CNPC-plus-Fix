package bin.cnpcplus.shader;

import net.minecraftforge.fml.common.FMLCommonHandler;

/** Effective-side gate keeps integrated-server scripts out of the render thread. */
public final class ShaderRuntime {
    private ShaderRuntime() {}
    public static boolean client() { return FMLCommonHandler.instance().getEffectiveSide().isClient(); }
    public static ShaderManager manager() { return client() ? bin.cnpcplus.shader.client.ClientShaders.MANAGER : ShaderManager.SERVER; }
    public static boolean isLoaded(String id) { return client() && bin.cnpcplus.shader.client.ClientShaders.isLoaded(id); }
    public static boolean load(String id) { return client() && bin.cnpcplus.shader.client.ClientShaders.load(id); }
    public static boolean unload(String id) { return client() && bin.cnpcplus.shader.client.ClientShaders.unload(id); }
    public static boolean reload(String id) { return client() && bin.cnpcplus.shader.client.ClientShaders.reload(id); }
    public static void reloadAll() { if (client()) bin.cnpcplus.shader.client.ClientShaders.reloadAll(); }
    public static String[] loaded() { return client() ? bin.cnpcplus.shader.client.ClientShaders.loaded() : new String[0]; }
}
