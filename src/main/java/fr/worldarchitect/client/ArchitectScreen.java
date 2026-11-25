package fr.worldarchitect.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import fr.worldarchitect.network.ProjectPackets;
import fr.worldarchitect.project.Project;
public final class ArchitectScreen extends Screen {
 private static final int BG=0xff1c201b,PANEL=0xff2c3427,ACCENT=0xffb6c69c,TEXT=0xffe9eadf;
 private boolean creating=false, backups=true;
 private java.util.UUID selected;
 private void action(String action){ProjectPackets.request(new ProjectPackets.Action(action,selected==null?null:selected.toString(),null,0,null,false));}
 private int type=0,size=2,profile=0,page=0;
 private String name="";
 private EditBox nameBox;
 private final int[] sizes={500,1000,2000,6000,0,-1};
 private String manualSize="3000",manualWidth="1500";
 private int chosenWidth(){if(sizes[size]!=-1)return sizes[size];try{return Integer.parseInt(manualWidth);}catch(NumberFormatException e){return -1;}}
 private int chosenSize(){if(sizes[size]!=-1)return sizes[size];try{return Integer.parseInt(manualSize);}catch(NumberFormatException e){return -1;}}
 private final String[] profiles={"worldarchitect:generic","worldarchitect:escape","worldarchitect:pokeleague","worldarchitect:fieldorders","worldarchitect:pokeworld"};
 private final String[] labels={"Construction libre","Escape From Minecraft","PokéLeague","Field Orders","PokéWorld"};
 private final String[] types={"Naturel","Plat","Vide","Importé"};
 public ArchitectScreen(){super(Component.literal("World Architect"));}
 private void refresh(){ProjectPackets.request(new ProjectPackets.Action("list",null,null,0,null,false));}
 private void rebuild(){clearWidgets();init();}
 private void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
 @Override protected void init(){
  int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
  if(!creating){
   button("+ Créer un nouveau projet",x+12,y+144,w-24,()->{creating=true;rebuild();});
   button("<",x+12,y+118,30,()->page=Math.max(0,page-1));
   button(">",x+w-42,y+118,30,()->{var r=ProjectPackets.latest();if(r!=null&&(page+1)*4<r.projects().size())page++;});
   button("Visiter",x+12,y+168,(w-28)/2,()->{var r=ProjectPackets.latest();if(selected!=null&&r!=null&&r.ready()!=null&&r.ready().contains(selected))action("visit");});
   button("Quitter le projet",x+48,y+118,w-96,()->action("leave"));
   button("Fermer",x+w/2+2,y+168,(w-28)/2,this::onClose);
   refresh();
  }else{
   nameBox=new EditBox(font,x+12,y+34,w-24,20,Component.literal("Nom du projet"));
   nameBox.setMaxLength(80);nameBox.setValue(name);nameBox.setResponder(v->name=v);addRenderableWidget(nameBox);
   button("Terrain : "+types[type],x+12,y+58,w-24,()->{type=(type+1)%4;rebuild();});
   button("Taille : "+(sizes[size]==-1?"Manuelle":sizes[size]==0?"Illimitée":sizes[size]+" x "+sizes[size]),x+12,y+82,sizes[size]==-1?(w-32)/3:w-24,()->{size=(size+1)%sizes.length;rebuild();});
   if(sizes[size]==-1){
    var field=new EditBox(font,x+16+(w-32)/3,y+82,(w-32)/3,20,Component.literal("Côté en blocs"));
    field.setMaxLength(5);field.setFilter(v->v.matches("[0-9]*"));field.setValue(manualSize);field.setResponder(v->manualSize=v);addRenderableWidget(field);
    var z=new EditBox(font,x+20+2*((w-32)/3),y+82,(w-32)/3,20,Component.literal("Largeur Z"));z.setMaxLength(5);z.setFilter(v->v.matches("[0-9]*"));z.setValue(manualWidth);z.setResponder(v->manualWidth=v);addRenderableWidget(z);
   }
   button("Profil : "+labels[profile],x+12,y+106,w-24,()->{profile=(profile+1)%profiles.length;rebuild();});
   button("Sauvegardes : "+(backups?"Oui":"Non"),x+12,y+130,w-24,()->{backups=!backups;rebuild();});
   button("Créer",x+12,y+158,(w-28)/2,()->{if(!name.isBlank()&&type!=3&&(sizes[size]!=-1||(chosenSize()>=16&&chosenSize()<=60000&&chosenWidth()>=16&&chosenWidth()<=60000))){ProjectPackets.request(new ProjectPackets.Action("create",name,Project.WorldType.values()[type],chosenSize(),profiles[profile],backups,chosenWidth()));creating=false;name="";rebuild();}});
   button("Retour",x+w/2+2,y+158,(w-28)/2,()->{creating=false;rebuild();});
  }
 }
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,BG);
  int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
  g.fill(x,y,x+w,y+204,PANEL);
  g.drawCenteredString(font,creating?"NOUVEAU PROJET":"WORLD ARCHITECT",width/2,y+10,ACCENT);
  if(!creating){
   var r=ProjectPackets.latest();
   if(r==null)g.drawCenteredString(font,"Chargement...",width/2,y+48,TEXT);
   else if(!r.success())g.drawCenteredString(font,r.message(),width/2,y+48,TEXT);
   else if(r.projects().isEmpty())g.drawCenteredString(font,"Aucun projet",width/2,y+48,TEXT);
   else for(int i=page*4;i<Math.min(page*4+4,r.projects().size());i++){
    var p=r.projects().get(i);int yy=y+30+(i-page*4)*21;
    g.fill(x+12,yy,x+w-12,yy+19,p.id().equals(selected)?0xff4c6039:BG);
    boolean ready=r.ready()!=null && r.ready().contains(p.id());
    String status=ready?"Disponible":"Non chargée";
    int sw=font.width(status);
    g.drawString(font,font.plainSubstrByWidth(p.name(),Math.max(0,w-48-sw)),x+18,yy+5,TEXT);
    g.drawString(font,status,x+w-18-sw,yy+5,ready?0xffa6c878:0xffdfc17b);
   }
  }else if(type==3)g.drawCenteredString(font,"Importation indisponible pour le moment",width/2,y+184,TEXT);
  else if(sizes[size]==-1)g.drawCenteredString(font,"Longueur X / Largeur Z : 16 à 60 000 blocs",width/2,y+184,TEXT);
  else g.drawCenteredString(font,"Création des paramètres uniquement",width/2,y+184,TEXT);
  super.render(g,mx,my,delta);
 }
 @Override public boolean mouseClicked(double mx,double my,int button){
  if(!creating&&button==0){
   int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
   var r=ProjectPackets.latest();
   if(r!=null&&r.success()&&mx>=x+12&&mx<x+w-12&&my>=y+30&&my<y+114){
    int i=page*4+(int)((my-y-30)/21);
    if(i<r.projects().size()){selected=r.projects().get(i).id();return true;}
   }
  }
  return super.mouseClicked(mx,my,button);
 }
 @Override public boolean isPauseScreen(){return false;}
}
