package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class DuplicateMapScreen extends Screen {
 private final Screen parent;
 private final String collection,source;
 private String target,name;
 public DuplicateMapScreen(Screen parent,String collection,String source,String name){
  super(Component.literal("Dupliquer une map"));this.parent=parent;this.collection=collection;this.source=source;this.target=collection;this.name=name.equals(source)?source+" - Copie":name;
 }
 @Override protected void init(){
  int x=width/2-110,y=height/2-70;
  var folder=new EditBox(font,x,y+20,220,20,Component.literal("Collection de destination"));
  folder.setMaxLength(80);folder.setValue(target);folder.setResponder(v->target=v);addRenderableWidget(folder);
  var dest=new EditBox(font,x,y+60,220,20,Component.literal("Nom de la copie"));
  dest.setMaxLength(80);dest.setValue(name);dest.setResponder(v->name=v);addRenderableWidget(dest);
  addRenderableWidget(Button.builder(Component.literal("Dupliquer"),b->{
   if(target.isBlank()||name.isBlank()||target.contains("|")||name.contains("|")||source.contains("|"))return;
   ProjectPackets.request(new ProjectPackets.Action("duplicate_map",collection,null,0,source+"|"+target+"|"+name,false));onClose();
  }).bounds(x,y+90,108,20).build());
  addRenderableWidget(Button.builder(Component.literal("Annuler"),b->onClose()).bounds(x+112,y+90,108,20).build());
 }
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,0xff20251e);
  int x=width/2-110,y=height/2-70;
  g.drawCenteredString(font,"DUPLIQUER : "+source,width/2,y-18,0xffb6c69c);
  g.drawString(font,"Collection de destination",x,y+8,0xffdddddd);
  g.drawString(font,"Nom de la copie",x,y+48,0xffdddddd);
  super.render(g,mx,my,delta);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
