package fr.worldarchitect.project;

import net.minecraft.server.level.ServerLevel;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.Comparator;

public final class ActiveBackupCoordinator {
 private final BackupScheduler scheduler=new BackupScheduler();
 private final ProjectSnapshotBackend backend;
 private final Executor executor;
 public ActiveBackupCoordinator(ProjectSnapshotBackend backend,Executor executor){
  this.backend=java.util.Objects.requireNonNull(backend);
  this.executor=java.util.Objects.requireNonNull(executor);
 }
 public boolean tick(ServerLevel level,Project project,Path root){
  if(!level.getServer().isSameThread())throw new IllegalStateException("Server thread required");
  if(!level.dimension().location().toString().equals(project.dimensionId()))throw new IllegalArgumentException("Wrong dimension");
  if(!backend.supported(level)||!scheduler.claimAutomatic(project.id(),project.automaticBackups(),System.nanoTime()))return false;
  Path staging=root.resolve("snapshots").resolve(java.util.UUID.randomUUID().toString());
  try{
   backend.snapshot(level,staging).thenAcceptAsync(snapshot->{
    if(!staging.equals(snapshot))throw new IllegalStateException("Unexpected snapshot path");
    try{
     ProjectBackupArchive.archive(snapshot,root.resolve("backups"),project.id(),"automatic",true);
     ProjectBackupArchive.pruneAutomatic(root.resolve("backups"),project.id());
    }catch(java.io.IOException e){throw new CompletionException(e);}
   },executor).whenComplete((ignored,error)->{
    try{
     if(error==null&&Files.isDirectory(staging,LinkOption.NOFOLLOW_LINKS)){
      try(var paths=Files.walk(staging)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
     }
    }catch(java.io.IOException e){com.mojang.logging.LogUtils.getLogger().warn("Snapshot cleanup failed",e);}
    finally{
     scheduler.complete(project.id(),error==null,System.nanoTime());
     if(error!=null)com.mojang.logging.LogUtils.getLogger().warn("Automatic project backup failed",error);
    }
   });
   return true;
  }catch(RuntimeException e){scheduler.complete(project.id(),false,System.nanoTime());throw e;}
 }
}
