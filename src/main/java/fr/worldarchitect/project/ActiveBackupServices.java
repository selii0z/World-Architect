package fr.worldarchitect.project;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import java.util.*;
import java.util.concurrent.*;

@EventBusSubscriber(modid="worldarchitect")
public final class ActiveBackupServices {
 private record Service(ActiveBackupCoordinator coordinator,ExecutorService executor){}
 private static final Map<MinecraftServer,Service> SERVICES=new IdentityHashMap<>();
 private ActiveBackupServices(){}
 public static void register(MinecraftServer server,ProjectSnapshotBackend backend){
  if(!server.isSameThread())throw new IllegalStateException("Server thread required");
  Objects.requireNonNull(backend);
  if(SERVICES.containsKey(server))throw new IllegalStateException("Snapshot backend already registered");
  var executor=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"worldarchitect-backups");t.setDaemon(true);return t;});
  SERVICES.put(server,new Service(new ActiveBackupCoordinator(backend,executor),executor));
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){
  var server=event.getServer();
  var service=SERVICES.get(server);
  if(service==null||server.getTickCount()%20!=0)return;
  var root=server.getWorldPath(LevelResource.ROOT).resolve("worldarchitect");
  for(var level:server.getAllLevels()){
   try{ProjectService.forLevel(level).ifPresent(project->service.coordinator().tick(level,project,root));}
   catch(RuntimeException e){com.mojang.logging.LogUtils.getLogger().warn("Cannot schedule project backup",e);}
  }
 }
 @SubscribeEvent public static void stop(ServerStoppingEvent event){
  var service=SERVICES.remove(event.getServer());
  if(service!=null)service.executor().shutdown();
 }
}
