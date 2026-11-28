package fr.worldarchitect.project;
import java.io.IOException;
import java.nio.file.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
public final class OverviewStorage {
 private OverviewStorage(){}
 public static Path save(Path destination,OfflineAerialBuilder.Overview overview)throws IOException{
  int w=overview.width(),h=overview.height();
  if(w<1||h<1||w>1024||h>1024||overview.colors().length!=w*h)throw new IOException("Invalid overview");
  Path target=destination.toAbsolutePath().normalize();
  Path parent=target.getParent();
  if(Files.isSymbolicLink(parent)||Files.isSymbolicLink(target))throw new IOException("Invalid overview destination");
  Files.createDirectories(parent);
  var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
  image.setRGB(0,0,w,h,overview.colors(),0,w);
  Path tmp=Files.createTempFile(parent,"overview-",".png");
  try{
   var writers=ImageIO.getImageWritersByFormatName("png");
   if(!writers.hasNext())throw new IOException("PNG encoder unavailable");
   var writer=writers.next();
   try(var stream=ImageIO.createImageOutputStream(tmp.toFile())){
    writer.setOutput(stream);
    var metadata=writer.getDefaultImageMetadata(javax.imageio.ImageTypeSpecifier.createFromRenderedImage(image),writer.getDefaultWriteParam());
    var root=new javax.imageio.metadata.IIOMetadataNode("javax_imageio_png_1.0");
    var text=new javax.imageio.metadata.IIOMetadataNode("tEXt");
    for(var pair:new String[][]{{"OriginX",Integer.toString(overview.originX())},{"OriginZ",Integer.toString(overview.originZ())},{"Step",Integer.toString(overview.step())}}){
     var entry=new javax.imageio.metadata.IIOMetadataNode("tEXtEntry");entry.setAttribute("keyword",pair[0]);entry.setAttribute("value",pair[1]);text.appendChild(entry);
    }
    root.appendChild(text);metadata.mergeTree("javax_imageio_png_1.0",root);
    writer.write(null,new javax.imageio.IIOImage(image,null,metadata),writer.getDefaultWriteParam());
   }finally{writer.dispose();}
   try(var channel=java.nio.channels.FileChannel.open(tmp,StandardOpenOption.WRITE)){channel.force(true);}
   Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   return target;
  }finally{Files.deleteIfExists(tmp);}
 }
}
