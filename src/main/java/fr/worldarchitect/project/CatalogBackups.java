package fr.worldarchitect.project;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class CatalogBackups {
 private CatalogBackups(){}
 private static UUID identity(String collection,String name){
  return UUID.nameUUIDFromBytes(("worldarchitect:catalog:"+collection+"/"+name).getBytes(StandardCharsets.UTF_8));
 }
 private static Path root()throws IOException{
  Path root=MapCatalog.root().getParent().resolve("backups");
  if(Files.isSymbolicLink(root))throw new IOException("Invalid backup root");
  Files.createDirectories(root);
  return root;
 }
 public static List<String> list(String collection,String name)throws IOException{
  MapCatalog.resolveTerrain(collection,name);
  return OfflineProjectBackups.list(root(),identity(collection,name));
 }
 public static Path create(String collection,String name,String label)throws IOException{
  Path source=MapCatalog.resolveTerrain(collection,name);
  Path root=root();
  UUID id=identity(collection,name);
  if(Files.isSymbolicLink(root.resolve(id.toString())))throw new IOException("Invalid backup folder");
  return OfflineProjectBackups.create(source,root,id,label,false);
 }
 public static Path restore(String collection,String name,String archive,String destination)throws IOException{
  MapCatalog.resolveTerrain(collection,name);
  if(destination==null||!destination.matches("[\\p{L}\\p{N} _.-]{1,80}")||destination.equals(".")||destination.equals("..")||destination.endsWith(".")||destination.endsWith(" "))throw new IOException("Invalid destination");
  Path parent=MapCatalog.createCollection(collection);
  Path target=parent.resolve(destination);
  if(Files.exists(target,LinkOption.NOFOLLOW_LINKS))throw new FileAlreadyExistsException(destination);
  Path staging=root().resolve("staging");
  if(Files.isSymbolicLink(staging))throw new IOException("Invalid staging folder");
  return OfflineProjectBackups.restoreCopy(root(),identity(collection,name),archive,staging,target);
 }
}
