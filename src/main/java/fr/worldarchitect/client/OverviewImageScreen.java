package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
public final class OverviewImageScreen extends Screen{
 private final Screen parent;private final boolean activeProject;private ResourceLocation texture;private int iw,ih;private double zoom=1,dx,dz;private boolean attempted;private int originX,originZ,step;private String error="";
 public OverviewImageScreen(Screen parent){this(parent,false);}
 public OverviewImageScreen(Screen parent,boolean activeProject){super(Component.literal("Carte aérienne"));this.parent=parent;this.activeProject=activeProject;if(activeProject)ProjectPackets.refreshMarkers();}
 @Override public void tick(){
  if(attempted)return;byte[] bytes=ProjectPackets.overviewImage();if(bytes==null)return;attempted=true;
  if(bytes.length==0){error="Carte absente ou trop volumineuse";return;}
  try{
   if(bytes.length<24)throw new IllegalArgumentException();
   var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);
   if(w<1||h<1||w>1024||h>1024)throw new IllegalArgumentException();
   readCoordinates(bytes);
   var image=NativeImage.read(new java.io.ByteArrayInputStream(bytes));iw=image.getWidth();ih=image.getHeight();
   texture=minecraft.getTextureManager().register("worldarchitect_overview",new DynamicTexture(image));
  }catch(Exception e){error="Image illisible";}
 }
 private void readCoordinates(byte[] bytes){
  var b=java.nio.ByteBuffer.wrap(bytes);b.position(8);
  Integer x=null,z=null,s=null;
  while(b.remaining()>=12){
   int n=b.getInt(),type=b.getInt();if(n<0||n>b.remaining()-4)break;
   int end=b.position()+n;
   if(type==0x74455874&&n<=128){
    byte[] value=new byte[n];b.get(value);String t=new String(value,java.nio.charset.StandardCharsets.ISO_8859_1);
    int sep=t.indexOf(0);
    if(sep>0)try{int v=Integer.parseInt(t.substring(sep+1));switch(t.substring(0,sep)){case "OriginX"->x=v;case "OriginZ"->z=v;case "Step"->s=v;}}catch(NumberFormatException ignored){}
   }
   b.position(end+4);
  }
  if(x!=null&&z!=null&&s!=null&&s>0&&s<=64){originX=x;originZ=z;step=s;}
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  g.fill(0,0,width,height,0xff20241f);
  if(texture!=null){
   double scale=Math.min((width-20.0)/iw,(height-44.0)/ih)*zoom;
   int w=Math.max(1,(int)(iw*scale)),h=Math.max(1,(int)(ih*scale));
   g.enableScissor(0,22,width,height-20);
   g.blit(texture,(int)((width-w)/2.0+dx),(int)((height-h)/2.0+dz),w,h,0f,0f,iw,ih,iw,ih);
   g.disableScissor();
   int px=(int)Math.floor((mx-(width-w)/2.0-dx)*iw/w),pz=(int)Math.floor((my-(height-h)/2.0-dz)*ih/h);
   if(step>0&&px>=0&&px<iw&&pz>=0&&pz<ih&&my>=22&&my<height-20)g.drawCenteredString(font,"X : "+(originX+(long)px*step)+"  Z : "+(originZ+(long)pz*step)+"  (± "+step+" blocs)",width/2,height-14,0xffffff);
  }else g.drawCenteredString(font,error.isEmpty()?"Chargement...":error,width/2,height/2,0xffffff);
  g.drawCenteredString(font,"Carte aérienne — molette : zoom / glisser : déplacer / Échap : retour",width/2,7,0xffffff);
 }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){zoom=Math.max(0.25,Math.min(16,zoom*Math.pow(1.2,vertical)));return true;}
 @Override public boolean mouseDragged(double x,double y,int button,double deltaX,double deltaY){if(button==0){dx+=deltaX;dz+=deltaY;return true;}return false;}
 @Override public void removed(){if(texture!=null){minecraft.getTextureManager().release(texture);texture=null;}ProjectPackets.clearOverviewImage();}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
