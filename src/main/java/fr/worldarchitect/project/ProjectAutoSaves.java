package fr.worldarchitect.project;

import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Native world persistence, distinct from independent snapshot archives. */
@EventBusSubscriber(modid="worldarchitect")
public final class ProjectAutoSaves {
 private static final Map<MinecraftServer,BackupScheduler> SCHEDULERS=new IdentityHashMap<>();
 private ProjectAutoSaves(){}
 private static BackupScheduler scheduler(MinecraftServer server){
  if(!server.isSameThread())throw new IllegalStateException("Server thread required");
  return SCHEDULERS.computeIfAbsent(server,ignored->new BackupScheduler());
 }
 private static void write(ServerLevel level,Project project,BackupScheduler scheduler){
  boolean success=false;
  try{
   if(!level.dimension().location().toString().equals(project.dimensionId()))throw new IllegalArgumentException("Wrong dimension");
   level.save(null,true,false);
   success=true;
   com.mojang.logging.LogUtils.getLogger().info("World Architect: project {} terrain saved",project.id());
  }finally{scheduler.complete(project.id(),success,System.nanoTime());}
 }
 public static void saveNow(ServerLevel level,Project project){
  var scheduler=scheduler(level.getServer());
  if(!scheduler.claimManual(project.id()))throw new IllegalStateException("Save already running");
  write(level,project,scheduler);
 }
 @SubscribeEvent public static void tick(ServerTickEvent.Post event){
  var server=event.getServer();
  if(server.getTickCount()%20!=0)return;
  var scheduler=scheduler(server);
  for(var level:server.getAllLevels()){
   try{
    var project=ProjectService.forLevel(level).orElse(null);
    if(project!=null&&scheduler.claimAutomatic(project.id(),project.automaticBackups(),System.nanoTime()))
     write(level,project,scheduler);
   }catch(RuntimeException e){com.mojang.logging.LogUtils.getLogger().warn("World Architect project save failed",e);}
  }
 }
 @SubscribeEvent public static void stop(ServerStoppedEvent event){SCHEDULERS.remove(event.getServer());}
}
