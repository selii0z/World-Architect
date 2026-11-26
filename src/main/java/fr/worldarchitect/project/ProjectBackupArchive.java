package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.UUID;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ProjectBackupArchive {
 private ProjectBackupArchive(){}
 // Caller must ensure the source is a consistent, quiescent project snapshot.
 static Path archive(Path snapshot,Path backupRoot,UUID project,String label,boolean automatic) throws IOException {
  if(label==null||!label.matches("[a-zA-Z0-9_-]{1,64}"))throw new IllegalArgumentException("Invalid backup label");
  if(Files.isSymbolicLink(snapshot))throw new IOException("Symbolic link snapshot");
  Path source=snapshot.toRealPath();
  if(!Files.isDirectory(source))throw new IOException("Missing snapshot");
  Path dir=backupRoot.resolve(project.toString());
  if(Files.isSymbolicLink(backupRoot)||Files.isSymbolicLink(dir))throw new IOException("Invalid backup directory");
  Files.createDirectories(dir);
  dir=dir.toRealPath();
  if(dir.startsWith(source))throw new IOException("Backup directory inside snapshot");
  Path target=dir.resolve((automatic?"auto-":"manual-")+System.currentTimeMillis()+"-"+UUID.randomUUID()+"-"+label+".zip");
  Path tmp=Files.createTempFile(dir,"backup-",".tmp");
  try{
   try(var zip=new ZipOutputStream(Files.newOutputStream(tmp));var paths=Files.walk(source)){
    for(var it=paths.iterator();it.hasNext();){
     Path p=it.next();
     if(Files.isSymbolicLink(p))throw new IOException("Symbolic link in snapshot");
     if(p.equals(source))continue;
     String name=source.relativize(p).toString().replace(java.io.File.separatorChar,'/');
     if(Files.isDirectory(p)){zip.putNextEntry(new ZipEntry(name+"/"));zip.closeEntry();}
     else if(Files.isRegularFile(p)){zip.putNextEntry(new ZipEntry(name));Files.copy(p,zip);zip.closeEntry();}
     else throw new IOException("Unsupported snapshot entry");
    }
   }
   try(var channel=FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
   Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE);
  }finally{Files.deleteIfExists(tmp);}
  return target;
 }
 static void pruneAutomatic(Path backupRoot,UUID project) throws IOException {
  Path dir=backupRoot.resolve(project.toString());
  if(Files.isSymbolicLink(backupRoot)||Files.isSymbolicLink(dir))throw new IOException("Invalid backup directory");
  if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))return;
  try(var paths=Files.list(dir)){
   var files=paths.filter(p->Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&p.getFileName().toString().matches("auto-[0-9]{13}-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-[a-zA-Z0-9_-]{1,64}\\.zip")).sorted(Comparator.comparing((Path p)->p.getFileName().toString()).reversed()).toList();
   for(int i=10;i<files.size();i++)Files.delete(files.get(i));
  }
 }
}
