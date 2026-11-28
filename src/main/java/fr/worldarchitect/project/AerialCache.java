package fr.worldarchitect.project;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;

final class AerialCache {
 private AerialCache(){}
 private static Path file(Path root,UUID project,int x,int z,int step){
  return root.resolve("worldarchitect/aerial-cache").resolve(project.toString()).resolve(step+"_"+x+"_"+z+".bin");
 }
 static int[] read(Path root,UUID project,int x,int z,int step)throws IOException{
  Path path=file(root,project,x,z,step);
  int[] colors=new int[4096];
  if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))return colors;
  if(Files.size(path)!=16388)return colors;
  try(var in=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))){
   if(in.readInt()!=1)return colors;
   for(int i=0;i<colors.length;i++)colors[i]=in.readInt();
  }
  return colors;
 }
 static void write(Path root,UUID project,int x,int z,int step,int[] colors)throws IOException{
  if(colors.length!=4096)throw new IOException("Invalid tile");
  Path path=file(root,project,x,z,step);
  Files.createDirectories(path.getParent());
  if(!Files.exists(path)){
   try(var files=Files.list(path.getParent())){if(files.limit(4096).count()>=4096)return;}
  }
  Path tmp=Files.createTempFile(path.getParent(),"tile-",".tmp");
  try{
   try(var out=new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp)))){
    out.writeInt(1);for(int color:colors)out.writeInt(color);
   }
   Files.move(tmp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
  }finally{Files.deleteIfExists(tmp);}
 }
}
