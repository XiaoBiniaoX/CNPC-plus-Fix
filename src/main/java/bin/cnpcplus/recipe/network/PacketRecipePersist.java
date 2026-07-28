package bin.cnpcplus.recipe.network;

import bin.cnpcplus.CnpcPlus;
import bin.cnpcplus.recipe.id.RecipeIds;
import bin.cnpcplus.recipe.services.RecipeServices;
import bin.cnpcplus.recipe.storage.RecipePersistent;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import noppes.npcs.controllers.RecipeController;
import noppes.npcs.controllers.data.RecipeCarpentry;
import noppes.npcs.packets.server.SPacketRecipeGet;
import noppes.npcs.packets.server.SPacketRecipesGet;

/**
 * C2S: persist=true add to recipes_persistent.dat; false remove from it only.
 */
public record PacketRecipePersist(int syncId, boolean persist) implements CustomPacketPayload {
    public static final Type<PacketRecipePersist> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CnpcPlus.MODID, "recipe_persist"));

    public static final StreamCodec<ByteBuf, PacketRecipePersist> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PacketRecipePersist::syncId,
            ByteBufCodecs.BOOL, PacketRecipePersist::persist,
            PacketRecipePersist::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(PacketRecipePersist packet, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            if (!sp.hasPermissions(2)) return;
            RecipeController controller = RecipeController.instance;
            if (controller == null) return;
            RecipeCarpentry recipe = RecipeIds.INSTANCE.bySyncId(packet.syncId);
            if (recipe == null) {
                CnpcPlus.LOGGER.warn("[RecipePersist] unknown syncId={}", packet.syncId);
                return;
            }
            if (packet.persist) {
                RecipePersistent.INSTANCE.persist(recipe, sp.registryAccess(), controller);
                RecipeServices.reloadGlobalIntoRecipeManager(controller);
            } else {
                RecipePersistent.INSTANCE.unpersist(recipe);
            }
            SPacketRecipesGet.sendRecipeData(sp, recipe.isGlobal ? 3 : 4);
            SPacketRecipeGet.setRecipeGui(sp, recipe);
            CnpcPlus.LOGGER.info("[RecipePersist] {} name={} syncId={}",
                    packet.persist ? "persist" : "unpersist", recipe.name, packet.syncId);
        });
    }
}
