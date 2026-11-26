package fr.worldarchitect.api;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

@EventBusSubscriber(modid="worldarchitect")
public final class InstanceServices {
    private record Service(InstanceManager manager, ExecutorService executor) {}
    private static final Map<MinecraftServer, Service> SERVICES = new IdentityHashMap<>();
    private InstanceServices() {}

    @SubscribeEvent public static synchronized void start(ServerStartingEvent event) {
        var server = event.getServer();
        var executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "WorldArchitect-Instances");
            thread.setDaemon(true);
            return thread;
        });
        SERVICES.put(server, new Service(new InstanceManager(server.getWorldPath(LevelResource.ROOT), executor), executor));
    }

    public static synchronized InstanceManager get(MinecraftServer server) {
        var service = SERVICES.get(server);
        if (service == null) throw new IllegalStateException("Instance service unavailable");
        return service.manager();
    }

    @SubscribeEvent public static synchronized void stop(ServerStoppingEvent event) {
        var service = SERVICES.remove(event.getServer());
        if (service != null) service.executor().shutdown();
    }
}
