package top.cnpcplus.station;

import java.util.UUID;
import net.minecraft.world.entity.player.Player;

/** Stored UUID, not a strong player/world reference. Offline owners fail gated recipes. */
public interface StationOperator {
    UUID cnpcplus$getOperator();
    void cnpcplus$setOperator(UUID player);
    static Player player(net.minecraft.world.level.block.entity.BlockEntity block) {
        UUID id = ((StationOperator)block).cnpcplus$getOperator();
        return id == null || block.getLevel().getServer() == null ? null : block.getLevel().getServer().getPlayerList().getPlayer(id);
    }
}
