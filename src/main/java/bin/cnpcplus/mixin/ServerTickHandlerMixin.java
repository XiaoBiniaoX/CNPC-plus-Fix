package bin.cnpcplus.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import noppes.npcs.ServerTickHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;

@Mixin(ServerTickHandler.class)
public class ServerTickHandlerMixin {

    @Redirect(method = "playerLogin", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;getAllLevels()Ljava/lang/Iterable;"), remap = false)
    private Iterable<ServerLevel> cnpcplus$skipPlayerLoginScoreboardSync(MinecraftServer server) {
        return Collections.emptyList();
    }
}