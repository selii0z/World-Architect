package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.zip.*;

public final class MapExport {
 private MapExport(){}
 public static Path export(String collection,String name)throws IOException{
  Path source=MapCatalog.resolveTerrain(collection,name);
  Path root=MapCatalog.root().getParent().resolve("exports");
  if(Files.isSymbolicLink(root))throw new IOException("Invalid export directory");
  Files.createDirectories(root);
  Path folder=root.resolve(collection);
  if(Files.isSymbolicLink(folder))throw new IOException("Invalid collection directory");
  Files.createDirectories(folder);
  Path target=folder.resolve(name+".zip");
  if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(target.toString());
  Path tmp=Files.createTempFile(folder,"export-",".tmp");
  try{
   try(var zip=new ZipOutputStream(Files.newOutputStream(tmp))){
    for(String part:java.util.List.of("region","entities","poi")){
     Path base=source.resolve(part);
     if(!Files.exists(base,LinkOption.NOFOLLOW_LINKS))continue;
     if(!Files.isDirectory(base,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid terrain directory");
     try(var paths=Files.walk(base)){
      for(var it=paths.iterator();it.hasNext();){
       Path p=it.next();
       String entry=source.relativize(p).toString().replace(java.io.File.separatorChar,'/');
       if(Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)){zip.putNextEntry(new ZipEntry(entry+"/"));zip.closeEntry();}
       else if(Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)){zip.putNextEntry(new ZipEntry(entry));Files.copy(p,zip);zip.closeEntry();}
       else throw new IOException("Unsupported terrain entry");
      }
     }
    }
   }
   try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
   Files.move(tmp,target);
   return target;
  }finally{Files.deleteIfExists(tmp);}
 }
}
