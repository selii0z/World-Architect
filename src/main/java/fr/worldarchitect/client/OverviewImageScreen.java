package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
public final class OverviewImageScreen extends Screen{
 private final Screen parent;private ResourceLocation texture;private int iw,ih;private double zoom=1,dx,dz;private boolean attempted;private String error="";
 public OverviewImageScreen(Screen parent){super(Component.literal("Carte aérienne"));this.parent=parent;}
 @Override public void tick(){
  if(attempted)return;byte[] bytes=ProjectPackets.overviewImage();if(bytes==null)return;attempted=true;
  if(bytes.length==0){error="Carte absente ou trop volumineuse";return;}
  try{
   if(bytes.length<24)throw new IllegalArgumentException();
   var header=java.nio.ByteBuffer.wrap(bytes);int w=header.getInt(16),h=header.getInt(20);
   if(w<1||h<1||w>1024||h>1024)throw new IllegalArgumentException();
   var image=NativeImage.read(new java.io.ByteArrayInputStream(bytes));iw=image.getWidth();ih=image.getHeight();
   texture=minecraft.getTextureManager().register("worldarchitect_overview",new DynamicTexture(image));
  }catch(Exception e){error="Image illisible";}
 }
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  g.fill(0,0,width,height,0xff20241f);
  if(texture!=null){
   double scale=Math.min((width-20.0)/iw,(height-44.0)/ih)*zoom;
   int w=Math.max(1,(int)(iw*scale)),h=Math.max(1,(int)(ih*scale));
   g.enableScissor(0,22,width,height-20);
   g.blit(texture,(int)((width-w)/2.0+dx),(int)((height-h)/2.0+dz),w,h,0f,0f,iw,ih,iw,ih);
   g.disableScissor();
  }else g.drawCenteredString(font,error.isEmpty()?"Chargement...":error,width/2,height/2,0xffffff);
  g.drawCenteredString(font,"Carte aérienne — molette : zoom / glisser : déplacer / Échap : retour",width/2,7,0xffffff);
 }
 @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){zoom=Math.max(0.25,Math.min(16,zoom*Math.pow(1.2,vertical)));return true;}
 @Override public boolean mouseDragged(double x,double y,int button,double deltaX,double deltaY){if(button==0){dx+=deltaX;dz+=deltaY;return true;}return false;}
 @Override public void removed(){if(texture!=null){minecraft.getTextureManager().release(texture);texture=null;}ProjectPackets.clearOverviewImage();}
 @Override public void onClose(){minecraft.setScreen(parent);}
}
