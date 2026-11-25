package fr.worldarchitect.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public final class ArchitectScreen extends Screen {
 private static final int BG=0xff1c201b, PANEL=0xff2c3427, ACCENT=0xffb6c69c, TEXT=0xffe9eadf;
 public ArchitectScreen(){ super(Component.literal("World Architect")); }
 @Override protected void init(){
  int w=Math.min(340,width-24), x=(width-w)/2, y=height/2;
  addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(x+12,y+48,w-24,20).build());
 }
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,BG);
  int w=Math.min(340,width-24), x=(width-w)/2,y=height/2;
  g.fill(x,y-76,x+w,y+80,PANEL);
  g.drawCenteredString(font,"WORLD ARCHITECT",width/2,y-62,ACCENT);
  g.drawCenteredString(font,"Gestionnaire de projets",width/2,y-40,TEXT);
  g.drawCenteredString(font,"Connexion au registre en développement",width/2,y-8,TEXT);
  super.render(g,mx,my,delta);
 }
 @Override public boolean isPauseScreen(){return false;}
}
