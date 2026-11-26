package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
public final class MarkerScreen extends Screen{
 private final Screen parent;
 private int page;
 private String name="";
 public MarkerScreen(Screen parent){super(Component.literal("Marqueurs"));this.parent=parent;}
 private java.util.List<fr.worldarchitect.project.ProjectMarkers.Marker> rows(){
  var r=ProjectPackets.markers();
  if(r==null||!r.success()||minecraft.level==null||!r.dimension().equals(minecraft.level.dimension().location().toString()))return java.util.List.of();
  return r.markers();
 }
 private void send(String action,String name){ProjectPackets.request(new ProjectPackets.Action(action,name,null,0,null,false));}
 private void button(String label,int x,int y,int w,Runnable run){addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(x,y,w,20).build());}
 @Override protected void init(){
  int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
  for(int i=0;i<5;i++){final int index=i;button("X",x+w-34,y+30+i*22,22,()->{var list=rows();int n=page*5+index;if(n<list.size())send("marker_remove",list.get(n).id().toString());});}
  button("<",x+12,y+144,30,()->page=Math.max(0,page-1));
  button(">",x+w-42,y+144,30,()->{if((page+1)*5<rows().size())page++;});
  button("Actualiser",x+48,y+144,w-96,ProjectPackets::refreshMarkers);
  var field=new EditBox(font,x+12,y+168,w-100,20,Component.literal("Nom"));field.setMaxLength(64);field.setValue(name);field.setHint(Component.literal("Nom du marqueur"));field.setResponder(v->name=v);addRenderableWidget(field);
  button("Ajouter",x+w-82,y+168,70,()->{if(!name.isBlank())send("marker_add",name);});
  button("Retour",x+12,y+192,w-24,this::onClose);
 }
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
  g.fill(0,0,width,height,0xff1c201b);g.fill(x,y,x+w,y+240,0xff2c3427);
  g.drawCenteredString(font,"MARQUEURS DU PROJET",width/2,y+10,0xffb6c69c);
  var list=rows();page=Math.min(page,Math.max(0,(list.size()-1)/5));
  for(int i=0;i<5&&page*5+i<list.size();i++){var m=list.get(page*5+i);g.drawString(font,font.plainSubstrByWidth(m.name()+"  ["+m.x()+", "+m.y()+", "+m.z()+"]",w-60),x+12,y+36+i*22,0xffe9eadf);}
  var r=ProjectPackets.markers();g.drawCenteredString(font,r==null?"Chargement...":font.plainSubstrByWidth(r.message(),w-24),width/2,y+218,0xffb6c69c);
  super.render(g,mx,my,delta);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
