package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.List;

public final class BackupScreen extends Screen {
 private final Screen parent;
 private final String collection,map;
 private String label="Sauvegarde",destination;
 private int page;
 private String selected;
 public BackupScreen(Screen parent,String collection,String map){
  super(Component.literal("Sauvegardes"));this.parent=parent;this.collection=collection;this.map=map;this.destination=map+" - Restauration";
  ProjectPackets.refreshBackups(collection,map);
 }
 private List<String> rows(){
  var r=ProjectPackets.backups();
  return r!=null&&r.success()&&collection.equals(r.collection())&&map.equals(r.map())?r.archives():List.of();
 }
 private void send(String action,String value){ProjectPackets.request(new ProjectPackets.Action(action,collection,null,0,value,false));}
 @Override protected void init(){
  int w=Math.min(340,width-16),x=(width-w)/2,y=Math.max(8,height/2-110);
  for(int i=0;i<4;i++){final int index=i;
   addRenderableWidget(Button.builder(Component.literal("Choisir"),b->{var list=rows();int n=page*4+index;if(n<list.size())selected=list.get(n);}).bounds(x+w-58,y+20+i*20,58,18).build());
  }
  addRenderableWidget(Button.builder(Component.literal("<"),b->page=Math.max(0,page-1)).bounds(x,y+102,28,20).build());
  addRenderableWidget(Button.builder(Component.literal(">"),b->{if((page+1)*4<rows().size())page++;}).bounds(x+32,y+102,28,20).build());
  addRenderableWidget(Button.builder(Component.literal("Actualiser"),b->{selected=null;page=0;ProjectPackets.refreshBackups(collection,map);}).bounds(x+64,y+102,w-64,20).build());
  var field=new EditBox(font,x,y+126,w-104,20,Component.literal("Nom de sauvegarde"));
  field.setMaxLength(64);field.setValue(label);field.setResponder(v->label=v);addRenderableWidget(field);
  addRenderableWidget(Button.builder(Component.literal("Sauvegarder"),b->{if(label.matches("[a-zA-Z0-9_-]{1,64}"))send("backup_map",map+"|"+label);}).bounds(x+w-100,y+126,100,20).build());
  var dest=new EditBox(font,x,y+150,w-104,20,Component.literal("Nom de restauration"));
  dest.setMaxLength(80);dest.setValue(destination);dest.setResponder(v->destination=v);addRenderableWidget(dest);
  addRenderableWidget(Button.builder(Component.literal("Restaurer"),b->{if(selected!=null&&rows().contains(selected)&&!destination.isBlank()&&!destination.contains("|"))send("restore_map",map+"|"+selected+"|"+destination);}).bounds(x+w-100,y+150,100,20).build());
  addRenderableWidget(Button.builder(Component.literal("Retour"),b->onClose()).bounds(x,y+174,w,20).build());
 }
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,0xff20251e);
  int w=Math.min(340,width-16),x=(width-w)/2,y=Math.max(8,height/2-110);
  g.drawCenteredString(font,"SAUVEGARDES : "+map,width/2,y,0xffb6c69c);
  var list=rows();
  for(int i=0;i<4;i++){int n=page*4+i;if(n>=list.size())break;String a=list.get(n);
   String[] parts=a.split("-",8);String title=parts.length==8?parts[7].replace(".zip",""):a;
   g.drawString(font,font.plainSubstrByWidth((a.startsWith("auto-")?"Auto : ":"Manuelle : ")+title,w-64),x,y+25+i*20,a.equals(selected)?0xffb6c69c:0xffdddddd);
  }
  var result=ProjectPackets.catalog();
  var backup=ProjectPackets.backups();
  String status=result!=null?result.message():backup!=null?backup.message():"Chargement...";
  g.drawString(font,font.plainSubstrByWidth(status,w),x,y+198,0xffb6c69c);
  super.render(g,mx,my,delta);
 }
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
