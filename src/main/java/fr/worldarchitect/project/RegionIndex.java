package fr.worldarchitect.project;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

public final class RegionIndex {
 private RegionIndex(){}
 public record Chunk(int x,int z,int sector,int sectors){}
 /** Read an offline region header without generating or loading Minecraft chunks. */
 public static List<Chunk> read(Path file)throws IOException{
  if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid region file");
  var matcher=java.util.regex.Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca").matcher(file.getFileName().toString());
  if(!matcher.matches())throw new IOException("Invalid region name");
  int rx=Integer.parseInt(matcher.group(1)),rz=Integer.parseInt(matcher.group(2));
  if(Math.abs((long)rx)>58600||Math.abs((long)rz)>58600)throw new IOException("Region outside world");
  try(var channel=FileChannel.open(file,StandardOpenOption.READ)){
   long size=channel.size();
   if(size<8192||size%4096!=0)throw new IOException("Invalid region length");
   var header=ByteBuffer.allocate(4096);
   while(header.hasRemaining()){if(channel.read(header)<0)throw new IOException("Truncated header");}
   header.flip();var result=new ArrayList<Chunk>();var used=new BitSet();
   for(int i=0;i<1024;i++){
    int location=header.getInt(),offset=location>>>8,count=location&255;
    if(location==0)continue;
    if(offset<2||count==0||((long)offset+count)*4096>size)throw new IOException("Invalid chunk allocation");
    int overlap=used.nextSetBit(offset);
    if(overlap>=0&&overlap<offset+count)throw new IOException("Overlapping chunk allocation");
    used.set(offset,offset+count);
    result.add(new Chunk(rx*32+i%32,rz*32+i/32,offset,count));
   }
   return List.copyOf(result);
  }
 }
}
