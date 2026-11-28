package fr.worldarchitect.project;
import java.io.IOException;
import java.nio.file.*;
import java.util.zip.ZipInputStream;

final class BackupRestore {
 private BackupRestore(){}
 // Extract only into a new isolated directory; never overwrite a running world.
 static Path stage(Path archive,Path stagingRoot,long maxBytes) throws IOException {
  if(maxBytes<=0)throw new IllegalArgumentException("Invalid extraction limit");
  if(Files.isSymbolicLink(stagingRoot)||!Files.isRegularFile(archive,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid restore source or staging directory");
  ProjectBackupArchive.verify(archive);
  Files.createDirectories(stagingRoot);
  Path root=Files.createTempDirectory(stagingRoot,"restore-").toAbsolutePath().normalize();
  long total=0;int count=0;
  byte[] buffer=new byte[65536];
  var seen=new java.util.HashSet<Path>();
  try(var zip=new ZipInputStream(Files.newInputStream(archive))){
   for(var entry=zip.getNextEntry();entry!=null;entry=zip.getNextEntry()){
    if(++count>100000)throw new IOException("Too many archive entries");
    String name=entry.getName();
    if(name.isBlank()||name.startsWith("/")||name.contains("\\")||name.contains(":"))throw new IOException("Invalid archive path");
    Path target=root.resolve(name).normalize();
    if(!target.startsWith(root)||target.equals(root))throw new IOException("Archive path escapes staging");
    if(!seen.add(target))throw new IOException("Duplicate archive entry");
    if(entry.isDirectory()){
     if(zip.read()!=-1)throw new IOException("Unexpected directory payload");
     Files.createDirectories(target);
    }
    else{
     Files.createDirectories(target.getParent());
     try(var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW)){
      int n;
      while((n=zip.read(buffer))!=-1){
       if(n>maxBytes-total)throw new IOException("Archive exceeds extraction limit");
       total+=n;out.write(buffer,0,n);
      }
     }
    }
    zip.closeEntry();
   }
   if(count==0)throw new IOException("Empty archive");
   return root;
  }catch(IOException|RuntimeException e){
   try(var paths=Files.walk(root)){for(var p:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
   catch(IOException cleanup){e.addSuppressed(cleanup);}
   throw e;
  }
 }
}
