package top.cnpcplus.persist.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import top.cnpcplus.craftingview.network.PacketHandler;
import top.cnpcplus.persist.PersistedRecipeStore;

import java.util.function.Supplier;

public class PacketPersistRecipe {

    private final CompoundTag data;

    public PacketPersistRecipe(CompoundTag data) {
        this.data = data;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeNbt(this.data);
    }

    public static PacketPersistRecipe decode(FriendlyByteBuf buf) {
        return new PacketPersistRecipe(buf.readNbt());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;
            if (this.data == null) return;
            RecipeCarpentry recipe = RecipeCarpentry.load(this.data);
            if (recipe == null || recipe.getId() == null) return;
            RecipeCarpentry saved = RecipeController.instance.saveRecipe(recipe);
            PersistedRecipeStore.put(saved);
            PacketHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketPersistStatus(saved.getId(), true)
            );
        });
        ctx.get().setPacketHandled(true);
    }
}
