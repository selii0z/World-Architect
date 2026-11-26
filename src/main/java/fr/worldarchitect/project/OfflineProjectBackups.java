package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class OfflineProjectBackups {
 private OfflineProjectBackups(){}
 private static final BackupScheduler SCHEDULER=new BackupScheduler();
 // Source must be an offline terrain directory, never a loaded dimension.
 public static Path create(Path source,Path backupRoot,UUID project,String label,boolean automatic)throws IOException{
  if(!SCHEDULER.claimManual(project))throw new IOException("Backup already running");
  boolean success=false;
  try{
   if(Files.exists(source.resolve("session.lock")))throw new IOException("Source must be an offline snapshot");
   Path result=ProjectBackupArchive.archive(source,backupRoot,project,label,automatic);
   if(automatic)ProjectBackupArchive.pruneAutomatic(backupRoot,project);
   success=true;return result;
  }finally{SCHEDULER.complete(project,success,System.nanoTime());}
 }
 public static List<String> list(Path backupRoot,UUID project)throws IOException{
  Path dir=backupRoot.resolve(project.toString());
  if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))return List.of();
  try(var paths=Files.list(dir)){
   return paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).map(p->p.getFileName().toString()).filter(OfflineProjectBackups::valid).sorted(Comparator.reverseOrder()).limit(1000).toList();
  }
 }
 private static boolean valid(String name){return name!=null&&name.matches("(auto|manual)-[0-9]{13}-[0-9a-f-]{36}-[a-zA-Z0-9_-]{1,64}\\.zip");}
 public static Path stageRestore(Path backupRoot,UUID project,String name,Path stagingRoot)throws IOException{
  if(!valid(name))throw new IOException("Invalid backup name");
  Path dir=backupRoot.resolve(project.toString());
  Path file=dir.resolve(name);
  if(Files.isSymbolicLink(dir)||!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Backup not found");
  return BackupRestore.stage(file,stagingRoot,8L*1024*1024*1024);
 }
}
