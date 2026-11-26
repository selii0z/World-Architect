package fr.worldarchitect.project;

import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

public final class AerialMap {
 private AerialMap(){}
 public static final int SIZE=64;
 public record Tile(String dimension,int originX,int originZ,int step,int size,int[] colors,int length,int width){}
 public static Tile sample(ServerPlayer player,int centerX,int centerZ,int step)throws IOException{
  var level=player.serverLevel();
  if(!level.getServer().isSameThread()||!player.hasPermissions(2))throw new IOException("Access denied");
  var project=ProjectService.forLevel(level).orElseThrow(()->new IOException("Not a project"));
  if(step!=1&&step!=2&&step!=4&&step!=8&&step!=16)throw new IOException("Invalid scale");
  if(Math.abs((long)centerX)>29999000||Math.abs((long)centerZ)>29999000)throw new IOException("Invalid center");
  int ox=centerX-SIZE/2*step,oz=centerZ-SIZE/2*step;
  int[] colors=new int[SIZE*SIZE];
  for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++){
   int bx=ox+x*step,bz=oz+z*step;
   if(!project.bounds().contains(bx+0.5,bz+0.5))continue;
   var chunk=level.getChunkSource().getChunkNow(bx>>4,bz>>4);
   if(chunk==null)continue;
   int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,bx&15,bz&15);
   if(y<level.getMinBuildHeight()||y>=level.getMaxBuildHeight())continue;
   var pos=new BlockPos(bx,y,bz);
   var state=chunk.getBlockState(pos);
   colors[z*SIZE+x]=0xff000000|state.getMapColor(level,pos).col;
  }
  return new Tile(level.dimension().location().toString(),ox,oz,step,SIZE,colors,project.bounds().length(),project.bounds().width());
 }
}
