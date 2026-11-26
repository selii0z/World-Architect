package fr.worldarchitect.api;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** A failed backend operation retains the terrain for recovery. */
public final class LiveInstance {
    public enum State { PREPARED, OPENING, ACTIVE, CLOSING, CLOSED, FAILED }
    private final UUID id;
    private final Path terrain;
    private State state = State.PREPARED;
    private DimensionBackend owner;
    private MinecraftServer owningServer;

    public LiveInstance(UUID id, Path terrain) {
        this.id = java.util.Objects.requireNonNull(id);
        this.terrain = java.util.Objects.requireNonNull(terrain);
    }

    public synchronized State state() { return state; }
    public synchronized boolean canDiscard() {
        return state == State.PREPARED || state == State.CLOSED;
    }

    public synchronized CompletableFuture<ServerLevel> open(MinecraftServer server, DimensionBackend backend) {
        if (state != State.PREPARED || !backend.supported(server))
            return CompletableFuture.failedFuture(new IllegalStateException("Instance cannot be opened"));
        owner = backend;
        owningServer = server;
        state = State.OPENING;
        try {
            return backend.open(server, id, terrain).thenApply(level -> java.util.Objects.requireNonNull(level, "Backend returned no level")).whenComplete((level, error) -> {
                synchronized (this) { state = error == null && level != null ? State.ACTIVE : State.FAILED; }
            });
        } catch (RuntimeException e) {
            state = State.FAILED;
            return CompletableFuture.failedFuture(e);
        }
    }

    public synchronized CompletableFuture<Void> close(MinecraftServer server, DimensionBackend backend) {
        if (state != State.ACTIVE || owner != backend || owningServer != server)
            return CompletableFuture.failedFuture(new IllegalStateException("Instance not active"));
        state = State.CLOSING;
        try {
            return backend.close(server, id).whenComplete((ignored, error) -> {
                synchronized (this) { state = error == null ? State.CLOSED : State.FAILED; }
            });
        } catch (RuntimeException e) {
            state = State.FAILED;
            return CompletableFuture.failedFuture(e);
        }
    }
}
