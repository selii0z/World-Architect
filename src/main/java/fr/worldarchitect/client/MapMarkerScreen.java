package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import fr.worldarchitect.project.ProjectMarkers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

public final class MapMarkerScreen extends Screen {
 private final Screen parent;
 private final int x,z;
 private int category,color;
 private EditBox name;
 private ProjectMarkers.Marker editing;
 private static final String[] CATEGORIES={"Général","Bâtiment","Accès","Repère","Zone"};
 private static final String[] COLORS={"Blanc","Vert","Bleu","Jaune","Rouge","Violet"};
 public MapMarkerScreen(Screen parent,int x,int z){super(Component.literal("Nouveau marqueur"));this.parent=parent;this.x=x;this.z=z;}
 public MapMarkerScreen(Screen parent,ProjectMarkers.Marker marker){this(parent,marker.x(),marker.z());editing=marker;category=marker.category().ordinal();color=marker.color().ordinal();}
 @Override protected void init(){
  int left=width/2-100,top=height/2-55;
  String old=name==null?(editing==null?"":editing.name()):name.getValue();
  name=new EditBox(font,left,top,200,20,Component.literal("Nom"));name.setMaxLength(64);name.setValue(old);addRenderableWidget(name);
  addRenderableWidget(Button.builder(Component.literal(CATEGORIES[category]),b->{category=(category+1)%CATEGORIES.length;b.setMessage(Component.literal(CATEGORIES[category]));}).bounds(left,top+24,98,20).build());
  addRenderableWidget(Button.builder(Component.literal(COLORS[color]),b->{color=(color+1)%COLORS.length;b.setMessage(Component.literal(COLORS[color]));}).bounds(left+102,top+24,98,20).build());
  addRenderableWidget(Button.builder(Component.literal(editing==null?"Créer":"Enregistrer"),b->{if(name.getValue().isBlank())return;String options=ProjectMarkers.Category.values()[category].name()+","+ProjectMarkers.Color.values()[color].name();ProjectPackets.request(new ProjectPackets.Action(editing==null?"marker_surface":"marker_update",name.getValue().strip(),null,0,editing==null?options+","+x+","+z:editing.id()+","+options,false));onClose();}).bounds(left,top+50,98,20).build());
  addRenderableWidget(Button.builder(Component.literal("Annuler"),b->onClose()).bounds(left+102,top+50,98,20).build());
 }
 @Override public void render(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0xff1c201b);g.drawCenteredString(font,editing==null?"NOUVEAU MARQUEUR":"MODIFIER LE MARQUEUR",width/2,height/2-88,0xffb6c69c);g.drawCenteredString(font,"X: "+x+"  Z: "+z,width/2,height/2-72,0xffffffff);super.render(g,mx,my,delta);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
