package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
public final class OverviewScreen extends Screen{
 private final Screen parent;private final String collection,map;
 private EditBox x,z,length,widthBox;private int ticks;private String local="";
 public OverviewScreen(Screen parent,String collection,String map){super(Component.literal("Carte aérienne"));this.parent=parent;this.collection=collection;this.map=map;}
 private EditBox field(int left,int top,String value){var box=new EditBox(font,left,top,100,20,Component.literal(value));box.setMaxLength(12);box.setValue(value);addRenderableWidget(box);return box;}
 private void send(String action){ProjectPackets.request(new ProjectPackets.Action(action,collection,null,0,map,false));}
 @Override protected void init(){
  int left=width/2-110,top=height/2-70;
  x=field(left,top,"-3000");z=field(left+120,top,"-3000");
  length=field(left,top+42,"6000");widthBox=field(left+120,top+42,"6000");
  addRenderableWidget(Button.builder(Component.literal("Générer"),b->{try{
   int ox=Integer.parseInt(x.getValue()),oz=Integer.parseInt(z.getValue()),l=Integer.parseInt(length.getValue()),w=Integer.parseInt(widthBox.getValue());
   if(l<1||w<1||l>60000||w>60000)throw new IllegalArgumentException();
   ProjectPackets.request(new ProjectPackets.Action("overview_start",collection,null,l,map+"|"+ox+"|"+oz,false,w));local="";
  }catch(Exception e){local="Dimensions ou coordonnées invalides";}}).bounds(left,top+72,100,20).build());
  addRenderableWidget(Button.builder(Component.literal("Annuler"),b->send("overview_cancel")).bounds(left+120,top+72,100,20).build());
  addRenderableWidget(Button.builder(Component.literal("Afficher"),b->{ProjectPackets.clearOverviewImage();send("overview_image");minecraft.setScreen(new OverviewImageScreen(this));}).bounds(left,top+122,100,20).build());
  addRenderableWidget(Button.builder(Component.literal("Retour"),b->onClose()).bounds(left+120,top+122,100,20).build());
 }
 @Override public void tick(){super.tick();if(++ticks%20==0)send("overview_status");}
 @Override public void render(GuiGraphics g,int mx,int my,float partial){
  super.render(g,mx,my,partial);int left=width/2-110,top=height/2-70;
  g.drawCenteredString(font,map,width/2,top-30,0xffffff);
  g.drawString(font,"Origine X",left,top-11,0xffffff);g.drawString(font,"Origine Z",left+120,top-11,0xffffff);
  g.drawString(font,"Longueur",left,top+31,0xffffff);g.drawString(font,"Largeur",left+120,top+31,0xffffff);
  var status=ProjectPackets.catalog();String msg=local.isEmpty()?(status==null?"":status.message()):local;
  g.drawCenteredString(font,msg,width/2,top+100,0xffffff);
 }
 @Override public void onClose(){ProjectPackets.refreshCatalog();minecraft.setScreen(parent);}
}
