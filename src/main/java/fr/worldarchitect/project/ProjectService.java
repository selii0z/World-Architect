package fr.worldarchitect.project;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.io.IOException;
import java.util.List;
import java.util.IdentityHashMap;
import java.util.Map;

@EventBusSubscriber(modid="worldarchitect")
public final class ProjectService {
 private static final Map<MinecraftServer, ProjectRegistry> REGISTRIES = new IdentityHashMap<>();
 @SubscribeEvent public static void start(ServerStartingEvent event) {
  MinecraftServer server = event.getServer();
  try {
   ProjectRegistry registry = new ProjectRegistry(server.getWorldPath(LevelResource.ROOT).resolve("worldarchitect"));
   REGISTRIES.put(server, registry);
   for (Project project : registry.list()) {
    if (project.worldType() == Project.WorldType.IMPORTED) continue;
    try { ProjectDatapack.stage(server, project); }
    catch (java.nio.file.FileAlreadyExistsException ignored) {}
    catch (IOException e) { com.mojang.logging.LogUtils.getLogger().error("Cannot stage project {}", project.id(), e); }
   }
  } catch(IOException e) {
   com.mojang.logging.LogUtils.getLogger().error("World Architect registry unavailable; existing data preserved", e);
  }
 }
 @SubscribeEvent public static void stop(ServerStoppedEvent event) {
  REGISTRIES.remove(event.getServer());
 }
 private static ProjectRegistry authorized(ServerPlayer player) {
  if (!player.hasPermissions(2)) throw new SecurityException("Administrator required");
  MinecraftServer server = player.getServer();
  if (server == null || !server.isSameThread()) throw new IllegalStateException("Server thread required");
  ProjectRegistry registry = REGISTRIES.get(server);
  if (registry == null) throw new IllegalStateException("Project registry unavailable");
  return registry;
 }
 public static List<Project> list(ServerPlayer player) { return authorized(player).list(); }
 public static Project create(ServerPlayer player, String name, Project.WorldType type, int size, String profile, boolean backups) throws IOException {
  if (type == Project.WorldType.IMPORTED) throw new IllegalArgumentException("Import not implemented");
  Project project = authorized(player).create(name,type,size,profile,backups);
  ProjectDatapack.stage(player.getServer(), project);
  return project;
 }
}
