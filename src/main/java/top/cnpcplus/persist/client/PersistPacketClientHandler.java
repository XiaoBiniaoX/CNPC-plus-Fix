package top.cnpcplus.persist.client;

import net.minecraft.resources.ResourceLocation;
import top.cnpcplus.persist.PersistedRecipeStore;

import java.util.List;

public final class PersistPacketClientHandler {
    private PersistPacketClientHandler() {
    }

    public static void setStatus(ResourceLocation id, boolean persisted) {
        PersistedRecipeStore.clientSet(id, persisted);
        PersistRecipeClient.refreshButtons();
    }

    public static void setAll(List<ResourceLocation> ids) {
        PersistedRecipeStore.clientSetAll(ids);
        PersistRecipeClient.refreshButtons();
    }
}
