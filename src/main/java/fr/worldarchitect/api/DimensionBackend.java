package fr.worldarchitect.api;

import java.util.UUID;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Backend contract only; implementations must manage Minecraft level registration and shutdown. */
public interface DimensionBackend {
    /** A close refusal with no storage or registration changes; may be retried. */
    final class BusyException extends IllegalStateException {
        public BusyException(String message){super(message);}
    }
    boolean supported(MinecraftServer server);

    /** Complete only after the level is registered and ready for players. */
    CompletableFuture<ServerLevel> open(MinecraftServer server, UUID instanceId, Path preparedTerrain);

    /** Complete only after players have left and all level storage handles are closed. */
    CompletableFuture<Void> close(MinecraftServer server, UUID instanceId);
}
