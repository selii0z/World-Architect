package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class AerialScreen extends Screen {
 private final Screen parent;
 private int cx,cz,step=4;
 private boolean initialized;
 private long lastRequest;
 public AerialScreen(Screen parent){super(Component.literal("Carte aérienne"));this.parent=parent;}
 private void refresh(){lastRequest=System.nanoTime();ProjectPackets.requestAerial(cx,cz,step);}
 private void move(int dx,int dz){cx+=dx*step*16;cz+=dz*step*16;refresh();}
 private void button(String label,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,20).build());}
 @Override protected void init(){
  if(!initialized&&minecraft.player!=null){cx=minecraft.player.getBlockX();cz=minecraft.player.getBlockZ();initialized=true;ProjectPackets.refreshMarkers();refresh();}
  int x=width/2-100,y=height-48;
  button("<",x,y,30,()->move(-1,0));button(">",x+34,y,30,()->move(1,0));
  button("N",x+68,y,30,()->move(0,-1));button("S",x+102,y,30,()->move(0,1));
  button("+",x+136,y,30,()->{step=Math.max(1,step/2);refresh();});
  button("-",x+170,y,30,()->{step=Math.min(16,step*2);refresh();});
  button("Centrer",x,y+22,64,()->{if(minecraft.player!=null){cx=minecraft.player.getBlockX();cz=minecraft.player.getBlockZ();refresh();}});
  button("Actualiser",x+68,y+22,64,this::refresh);button("Retour",x+136,y+22,64,this::onClose);
 }
 @Override public void tick(){
  if(System.nanoTime()-lastRequest>2000000000L)refresh();
 }
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,0xff1c201b);
  g.drawCenteredString(font,"CARTE AÉRIENNE — "+(64*step)+" blocs",width/2,8,0xffb6c69c);
  int pixel=Math.max(1,Math.min((width-20)/64,(height-82)/64)),size=pixel*64,x=(width-size)/2,y=24;
  g.fill(x,y,x+size,y+size,0xff333833);
  var r=ProjectPackets.aerial();
  if(r!=null&&r.tile()!=null&&minecraft.level!=null){
   var t=r.tile();
   if(t.size()==64&&t.colors().length==4096&&t.step()==step&&t.originX()==cx-32*step&&t.originZ()==cz-32*step&&t.dimension().equals(minecraft.level.dimension().location().toString())){
    for(int i=0;i<4096;i++){int c=t.colors()[i];if(c!=0){int px=x+i%64*pixel,py=y+i/64*pixel;g.fill(px,py,px+pixel,py+pixel,c);}}
    var markers=ProjectPackets.markers();
    if(markers!=null&&markers.success()&&t.dimension().equals(markers.dimension())){
     for(var m:markers.markers()){
      int px=x+(int)((m.x()-t.originX())/(double)step*pixel),py=y+(int)((m.z()-t.originZ())/(double)step*pixel);
      if(px<x||px>=x+size||py<y||py>=y+size)continue;
      int color=switch(m.color()){case GREEN->0xff52b765;case BLUE->0xff498be4;case YELLOW->0xffe5c441;case RED->0xffdb5151;case PURPLE->0xffa66bd9;default->0xffffffff;};
      g.fill(px-3,py-3,px+4,py+4,0xff111111);g.fill(px-2,py-2,px+3,py+3,color);
      if(Math.abs(mx-px)<=5&&Math.abs(my-py)<=5)g.drawString(font,m.name(),px+5,py-9,color);
     }
    }
    if(minecraft.player!=null){int px=x+(int)((minecraft.player.getX()-t.originX())/step*pixel),py=y+(int)((minecraft.player.getZ()-t.originZ())/step*pixel);if(px>=x&&px<x+size&&py>=y&&py<y+size)g.fill(px-2,py-2,px+3,py+3,0xffffffff);}
   }
  }
  g.drawCenteredString(font,"X: "+cx+"  Z: "+cz,width/2,height-60,0xffe9eadf);
  super.render(g,mx,my,delta);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
