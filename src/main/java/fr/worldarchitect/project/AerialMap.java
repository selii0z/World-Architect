package fr.worldarchitect.project;

import java.io.IOException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;

public final class AerialMap {
 private AerialMap(){}
 private record Key(java.nio.file.Path root,java.util.UUID project,int x,int z,int step){}
 private static final java.util.concurrent.ThreadPoolExecutor IO=new java.util.concurrent.ThreadPoolExecutor(1,1,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),task->{var thread=new Thread(task,"worldarchitect-aerial-cache");thread.setDaemon(true);return thread;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
 private static final java.util.Map<Key,int[]> CACHE=new java.util.LinkedHashMap<>(128,0.75f,true){
  protected boolean removeEldestEntry(java.util.Map.Entry<Key,int[]> e){return size()>128;}
 };
 private static final java.util.Map<Key,int[]> DIRTY=new java.util.LinkedHashMap<>();
 private static final java.util.Set<Key> LOADING=new java.util.HashSet<>();
 private static synchronized int[] cached(Key key){
  int[] data=CACHE.get(key);
  if(data!=null)return data;
  if(IO.getQueue().remainingCapacity()>0&&LOADING.add(key))try{IO.execute(()->{
   try{int[] read=AerialCache.read(key.root(),key.project(),key.x(),key.z(),key.step());synchronized(AerialMap.class){CACHE.putIfAbsent(key,read);}}
   catch(IOException ex){synchronized(AerialMap.class){CACHE.putIfAbsent(key,new int[4096]);}}
   finally{synchronized(AerialMap.class){LOADING.remove(key);}}
  });}catch(java.util.concurrent.RejectedExecutionException ex){LOADING.remove(key);}
  return null;
 }
 private static synchronized void persist(Key key,int[] data){
  CACHE.put(key,data);
  DIRTY.put(key,data);
  flushDirty();
 }
 private static synchronized void flushDirty(){
  var iterator=DIRTY.entrySet().iterator();
  while(iterator.hasNext()&&IO.getQueue().remainingCapacity()>0){
   var entry=iterator.next();var key=entry.getKey();var data=entry.getValue();
   try{IO.execute(()->{try{AerialCache.write(key.root(),key.project(),key.x(),key.z(),key.step(),data);}catch(IOException ex){org.slf4j.LoggerFactory.getLogger(AerialMap.class).warn("Cannot save aerial cache",ex);}});iterator.remove();}catch(java.util.concurrent.RejectedExecutionException ex){break;}
  }
  while(DIRTY.size()>128){DIRTY.remove(DIRTY.keySet().iterator().next());org.slf4j.LoggerFactory.getLogger(AerialMap.class).warn("Aerial cache retry limit reached");}
 }
 public static final int SIZE=64;
 public record Tile(String dimension,int originX,int originZ,int step,int size,int[] colors,int length,int width){}
 public static Tile sample(ServerPlayer player,int centerX,int centerZ,int step)throws IOException{
  var level=player.serverLevel();
  if(!level.getServer().isSameThread()||!player.hasPermissions(2))throw new IOException("Access denied");
  var project=ProjectService.forLevel(level).orElseThrow(()->new IOException("Not a project"));
  if(step!=1&&step!=2&&step!=4&&step!=8&&step!=16&&step!=32&&step!=64&&step!=128&&step!=256&&step!=512&&step!=1024)throw new IOException("Invalid scale");
  if(Math.abs((long)centerX)>29999000||Math.abs((long)centerZ)>29999000)throw new IOException("Invalid center");
  int ox=centerX-SIZE/2*step,oz=centerZ-SIZE/2*step;
  int[] colors=new int[SIZE*SIZE];
  int span=SIZE*step;
  var root=level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
  var tiles=new java.util.HashMap<Key,int[]>();
  var changed=new java.util.HashSet<Key>();
  for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++){
   int bx=ox+x*step,bz=oz+z*step;
   if(!project.bounds().contains(bx+0.5,bz+0.5))continue;
   int tx=Math.floorDiv(bx,span)*span,tz=Math.floorDiv(bz,span)*span;
   var key=new Key(root,project.id(),tx,tz,step);
   if(!tiles.containsKey(key)){var old=cached(key);tiles.put(key,old==null?null:old.clone());}
   var data=tiles.get(key);
   int index=Math.floorDiv(bz-tz,step)*SIZE+Math.floorDiv(bx-tx,step);
   if(data!=null)colors[z*SIZE+x]=data[index];
   bx=Math.floorDiv(bx,step)*step;bz=Math.floorDiv(bz,step)*step;
   var chunk=level.getChunkSource().getChunkNow(bx>>4,bz>>4);
   if(chunk==null)continue;
   int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,bx&15,bz&15);
   if(y<level.getMinBuildHeight()||y>=level.getMaxBuildHeight())continue;
   var pos=new BlockPos(bx,y,bz);
   var state=chunk.getBlockState(pos);
   colors[z*SIZE+x]=0xff000000|state.getMapColor(level,pos).col;
   if(data!=null&&data[index]!=colors[z*SIZE+x]){data[index]=colors[z*SIZE+x];changed.add(key);}
  }
  for(var key:changed)persist(key,tiles.get(key));
  flushDirty();
  return new Tile(level.dimension().location().toString(),ox,oz,step,SIZE,colors,project.bounds().length(),project.bounds().width());
 }
}
