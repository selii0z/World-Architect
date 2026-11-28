package fr.worldarchitect.project;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.zip.*;
import net.minecraft.nbt.*;

public final class RegionChunkReader {
 private RegionChunkReader(){}
 /** Only use immutable offline snapshots. External chunk streams are not supported. */
 public static CompoundTag read(Path file,RegionIndex.Chunk chunk)throws IOException{
  if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))throw new IOException("Invalid region");
  if(chunk.sector()<2||chunk.sectors()<1||chunk.sectors()>255)throw new IOException("Invalid allocation");
  try(var channel=FileChannel.open(file,StandardOpenOption.READ)){
   long offset=chunk.sector()*4096L;
   if(offset+chunk.sectors()*4096L>channel.size())throw new IOException("Truncated allocation");
   channel.position(offset);
   var header=ByteBuffer.allocate(5);
   while(header.hasRemaining())if(channel.read(header)<0)throw new EOFException();
   header.flip();int length=header.getInt(),type=header.get()&255;
   if(length<2||length>chunk.sectors()*4096-4)throw new IOException("Invalid chunk length");
   if((type&128)!=0)throw new IOException("External chunk stream unsupported");
   var payload=ByteBuffer.allocate(length-1);
   while(payload.hasRemaining())if(channel.read(payload)<0)throw new EOFException();
   var raw=new ByteArrayInputStream(payload.array());
   InputStream decoded=switch(type){case 1->new GZIPInputStream(raw);case 2->new InflaterInputStream(raw);case 3->raw;default->throw new IOException("Unsupported chunk compression");};
   try(var input=new DataInputStream(decoded)){
    var tag=NbtIo.read(input,NbtAccounter.create(32L*1024*1024));
    if(tag.getInt("xPos")!=chunk.x()||tag.getInt("zPos")!=chunk.z())throw new IOException("Chunk coordinates mismatch");
    return tag;
   }
  }
 }
}
