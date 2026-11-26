package fr.worldarchitect.project;
import java.io.IOException;
import java.nio.file.*;
public final class OfflineAerialBuilder {
 private OfflineAerialBuilder(){}
 public record Overview(int originX,int originZ,int step,int width,int height,int[] colors,int chunks){}
 /** Call off the server tick thread, with an immutable terrain snapshot. */
 public static Overview build(Path terrain,int originX,int originZ,int length,int width)throws IOException{
  if(length<1||width<1||length>60000||width>60000)throw new IOException("Invalid overview bounds");
  int step=1;while((length+step-1)/step>1024||(width+step-1)/step>1024)step*=2;
  int w=(length+step-1)/step,h=(width+step-1)/step;
  int[] colors=new int[w*h];int chunks=0;
  Path region=terrain.resolve("region");
  if(!Files.isDirectory(region,LinkOption.NOFOLLOW_LINKS))throw new IOException("Missing region directory");
  try(var files=Files.list(region)){
   var iterator=files.iterator();
   while(iterator.hasNext()){
    if(Thread.currentThread().isInterrupted())throw new IOException("Overview cancelled");
    Path file=iterator.next();
    if(!file.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca"))continue;
    for(var chunk:RegionIndex.read(file)){
     long bx=chunk.x()*16L,bz=chunk.z()*16L;
     if(bx+16<=originX||bz+16<=originZ||bx>=originX+(long)length||bz>=originZ+(long)width)continue;
     int dx=(int)(bx-originX),dz=(int)(bz-originZ);
     int startX=Math.floorMod(-dx,step),startZ=Math.floorMod(-dz,step);
     if(startX>=16||startZ>=16)continue;
     var surface=SurfaceColors.resolve(ChunkSurface.read(RegionChunkReader.read(file,chunk)));chunks++;
     for(int z=startZ;z<16;z+=step)for(int x=startX;x<16;x+=step){
      int px=dx+x,pz=dz+z;
      if(px>=0&&pz>=0&&px<length&&pz<width)colors[pz/step*w+px/step]=surface[z*16+x];
     }
    }
   }
  }
  return new Overview(originX,originZ,step,w,h,colors,chunks);
 }
}
