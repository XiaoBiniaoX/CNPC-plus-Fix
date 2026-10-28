package bin.cnpcplus.crafting.client;

import bin.cnpcplus.crafting.network.PacketRecipeState;
import net.minecraft.client.Minecraft;

public final class RecipeClientState {
    public static PacketRecipeState current;
    private RecipeClientState() { }
    public static void accept(PacketRecipeState state) {
        current = state;
        if (Minecraft.getMinecraft().currentScreen instanceof RecipeEditor) {
            ((RecipeEditor) Minecraft.getMinecraft().currentScreen).accept(state);
        }
    }
}
