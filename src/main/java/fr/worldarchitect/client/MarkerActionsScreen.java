package fr.worldarchitect.client;
import fr.worldarchitect.network.ProjectPackets;
import fr.worldarchitect.project.ProjectMarkers;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class MarkerActionsScreen extends Screen {
 private final Screen parent;
 private final ProjectMarkers.Marker marker;
 public MarkerActionsScreen(Screen parent,ProjectMarkers.Marker marker){super(Component.literal("Marqueur"));this.parent=parent;this.marker=marker;}
 private void action(String label,int y,Runnable run){addRenderableWidget(Button.builder(Component.literal(label),b->run.run()).bounds(width/2-90,y,180,20).build());}
 private void confirm(String label,String operation){minecraft.setScreen(new ConfirmScreen(ok->{if(ok)ProjectPackets.request(new ProjectPackets.Action(operation,marker.id().toString(),null,0,"",false));minecraft.setScreen(ok?parent:this);},Component.literal(label),Component.literal(marker.name()+" ?")));}
 @Override protected void init(){int y=height/2-54;action("Téléporter",y,()->confirm("Rejoindre le marqueur","marker_teleport"));action("Modifier",y+24,()->minecraft.setScreen(new MapMarkerScreen(parent,marker)));if(parent instanceof AerialScreen aerial)action("Déplacer",y+48,()->{aerial.beginMove(marker);minecraft.setScreen(parent);});else if(parent instanceof OverviewImageScreen overview)action("Déplacer",y+48,()->{overview.beginMove(marker);minecraft.setScreen(parent);});action("Supprimer",y+72,()->confirm("Supprimer le marqueur","marker_remove"));action("Retour",y+96,this::onClose);}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0xff1c201b);g.drawCenteredString(font,font.plainSubstrByWidth(marker.name(),width-20),width/2,height/2-80,0xffb6c69c);super.render(g,mx,my,delta);}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
}
