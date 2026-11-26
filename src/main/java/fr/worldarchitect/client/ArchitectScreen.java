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
 private boolean creating=false, backups=true, selecting=false, library=false;
 private boolean toolsOpen=false;
 private int shapeIndex=0;
 private String targetBlock="minecraft:stone",filterBlock="";
 private final String[] shapeLabels={"Plein","Murs","Boîte creuse","Ellipsoïde","Ellipsoïde creux","Cylindre","Cylindre creux"};
 private java.util.UUID selected;
 private boolean catalogOpen=false;
 private String collection=null, newCollection="", targetCollection=null;
 private int catalogPage=0;
 private String importSource=null, importName="";
 private java.util.List<fr.worldarchitect.project.MapCatalog.Entry> mapEntries(){
  var r=ProjectPackets.catalog();if(r==null||!r.success()||collection==null)return java.util.List.of();
  return r.collections().stream().filter(v->v.name().equals(collection)).findFirst().map(v->v.maps()).orElse(java.util.List.of());
 }
 private java.util.List<String> catalogRows(){
  var r=ProjectPackets.catalog();if(r==null||!r.success())return java.util.List.of();
  if(collection==null)return r.collections().stream().map(v->v.name()).toList();
  return r.collections().stream().filter(v->v.name().equals(collection)).findFirst().map(v->v.maps().stream().map(m->m.name()+" | "+m.format()).toList()).orElse(java.util.List.of());
 }
 private String notice="";
 private String librarySearch="";
 private int libraryPage=0, rotation=0;
 private boolean mirrored=false;
 private java.util.UUID prefabSelected;
 private void preview(){
  if(prefabSelected!=null)ProjectPackets.request(new ProjectPackets.Action("preview_structure",prefabSelected.toString(),null,0,net.minecraft.world.level.block.Rotation.values()[rotation].name(),mirrored));
 }
 private java.util.List<fr.worldarchitect.project.StructureLibrary.Metadata> filtered(){
  var r=ProjectPackets.latest();
  if(r==null||r.structures()==null)return java.util.List.of();
  String q=librarySearch.toLowerCase(java.util.Locale.ROOT);
  return r.structures().stream().filter(s->(s.name()+" "+s.category()).toLowerCase(java.util.Locale.ROOT).contains(q)).toList();
 }
 private void action(String action){ProjectPackets.request(new ProjectPackets.Action(action,selected==null?null:selected.toString(),null,0,null,false));}
 private int type=0,size=2,profile=0,page=0;
 private String name="", prefabName="", prefabCategory="Bâtiments";
 private EditBox nameBox;
 private final int[] sizes={500,1000,2000,6000,0,-1};
 private String manualSize="3000",manualWidth="1500";
 private int chosenWidth(){if(sizes[size]!=-1)return sizes[size];try{return Integer.parseInt(manualWidth);}catch(NumberFormatException e){return -1;}}
 private int chosenSize(){if(sizes[size]!=-1)return sizes[size];try{return Integer.parseInt(manualSize);}catch(NumberFormatException e){return -1;}}
 private final String[] profiles={"worldarchitect:generic"};
 private final String[] labels={"Construction libre"};
 private final String[] types={"Naturel","Plat","Vide","Importé"};
 public ArchitectScreen(){super(Component.literal("World Architect"));}
 private void refresh(){ProjectPackets.request(new ProjectPackets.Action("list",null,null,0,null,false));}
 private void rebuild(){clearWidgets();init();}
 private void button(String label,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(label),b->action.run()).bounds(x,y,w,20).build());}
 @Override protected void init(){
  int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
  if(catalogOpen){
   button("<",x+12,y+144,30,()->catalogPage=Math.max(0,catalogPage-1));
   button(">",x+w-42,y+144,30,()->{if((catalogPage+1)*5<catalogRows().size())catalogPage++;});
   button("Actualiser",x+48,y+144,w-96,ProjectPackets::refreshCatalog);
   if(collection==null){
    var field=new EditBox(font,x+12,y+168,(w-28)/2,20,Component.literal("Nouvelle collection"));
    field.setMaxLength(80);field.setHint(Component.literal("Nom du dossier"));field.setValue(newCollection);field.setResponder(v->newCollection=v);addRenderableWidget(field);
    button("Créer dossier",x+w/2+2,y+168,(w-28)/2,()->{if(!newCollection.isBlank())ProjectPackets.request(new ProjectPackets.Action("create_collection",newCollection,null,0,null,false));});
   }else if(importSource!=null){
    var field=new EditBox(font,x+12,y+168,(w-28)/2,20,Component.literal("Nom de destination"));
    field.setMaxLength(80);field.setValue(importName);field.setResponder(v->importName=v);addRenderableWidget(field);
    button("Importer",x+w/2+2,y+168,(w-28)/2,()->{if(!importName.isBlank()){ProjectPackets.request(new ProjectPackets.Action((importSource.toLowerCase(java.util.Locale.ROOT).endsWith(".schem")||importSource.toLowerCase(java.util.Locale.ROOT).endsWith(".litematic")||importSource.toLowerCase(java.util.Locale.ROOT).endsWith(".nbt"))?"import_schematic":"import_map",collection,null,0,importSource+"|"+importName,false));importSource=null;catalogPage=0;rebuild();}});
   }else{
    button("+ Créer une map",x+12,y+168,w-24,()->{targetCollection=collection;catalogOpen=false;creating=true;rebuild();});
   }
   if(importSource!=null&&mapEntries().stream().anyMatch(e->e.name().equals(importSource)&&(e.format()==fr.worldarchitect.project.MapCatalog.Format.REGION||e.format()==fr.worldarchitect.project.MapCatalog.Format.WORLD))){
    button("Activer la map",x+12,y+192,(w-28)/2,()->{ProjectPackets.request(new ProjectPackets.Action("activate_map",importSource,null,0,collection,false));notice="Activation demandée";catalogOpen=false;importSource=null;rebuild();});
   }
   button("Retour",importSource==null?x+12:x+w/2+2,y+192,importSource==null?w-24:(w-28)/2,()->{if(importSource!=null){importSource=null;}else if(collection!=null){collection=null;catalogPage=0;}else catalogOpen=false;rebuild();});
  }else if(library){
   var search=new EditBox(font,x+12,y+30,w-24,20,Component.literal("Recherche"));
   search.setHint(Component.literal("Nom ou catégorie"));search.setValue(librarySearch);search.setResponder(v->{librarySearch=v;libraryPage=0;});addRenderableWidget(search);
   button("<",x+12,y+158,30,()->libraryPage=Math.max(0,libraryPage-1));
   button(">",x+w-42,y+158,30,()->{if((libraryPage+1)*4<filtered().size())libraryPage++;});
   button("Actualiser",x+48,y+158,w-96,()->action("library"));
   button("Rotation : "+(rotation*90)+"°",x+12,y+192,(w-32)/3,()->{rotation=(rotation+1)%4;rebuild();});
   button("Miroir : "+(mirrored?"Oui":"Non"),x+16+(w-32)/3,y+192,(w-32)/3,()->{mirrored=!mirrored;rebuild();});
   button("Aperçu",x+20+2*((w-32)/3),y+192,(w-32)/3,this::preview);
   button("Retour",x+12,y+216,w-24,()->{library=false;rebuild();});
  }else if(toolsOpen){
   int half=(w-28)/2;
   button("Point A",x+12,y+30,half,()->action("select_first"));
   button("Point B",x+w/2+2,y+30,half,()->action("select_second"));
   button("Forme : "+shapeLabels[shapeIndex],x+12,y+54,w-24,()->{shapeIndex=(shapeIndex+1)%shapeLabels.length;rebuild();});
   var target=new EditBox(font,x+12,y+88,w-24,20,Component.literal("Bloc cible"));
   target.setMaxLength(128);target.setValue(targetBlock);target.setResponder(v->targetBlock=v);addRenderableWidget(target);
   var filter=new EditBox(font,x+12,y+122,w-24,20,Component.literal("Bloc à remplacer"));
   filter.setMaxLength(128);filter.setValue(filterBlock);filter.setHint(Component.literal("Vide = tous les blocs"));filter.setResponder(v->filterBlock=v);addRenderableWidget(filter);
   button("Appliquer",x+12,y+146,half,()->{if(!targetBlock.isBlank())ProjectPackets.request(new ProjectPackets.Action("block_edit",fr.worldarchitect.project.EditGeometry.Shape.values()[shapeIndex].name(),null,0,targetBlock.strip()+"|"+filterBlock.strip(),false));});
   button("Interrompre",x+w/2+2,y+146,half,()->action("block_cancel"));
   button("Annuler",x+12,y+170,half,()->action("block_undo"));
   button("Rétablir",x+w/2+2,y+170,half,()->action("block_redo"));
   button("Retour",x+12,y+194,half,()->{toolsOpen=false;rebuild();});
   button("Revenir au jeu",x+w/2+2,y+194,half,this::onClose);
  }else if(selecting){
   button("Définir le point A",x+12,y+34,w-24,()->action("select_first"));
   button("Définir le point B",x+12,y+58,w-24,()->action("select_second"));
   button("Effacer la sélection",x+12,y+82,w-24,()->action("select_clear"));
   int half=(w-28)/2;
   var prefab=new EditBox(font,x+12,y+106,half,20,Component.literal("Nom du bâtiment"));
   prefab.setMaxLength(80);prefab.setHint(Component.literal("Nom du bâtiment"));prefab.setValue(prefabName);prefab.setResponder(v->prefabName=v);addRenderableWidget(prefab);
   var category=new EditBox(font,x+w/2+2,y+106,half,20,Component.literal("Catégorie"));
   category.setMaxLength(60);category.setValue(prefabCategory);category.setResponder(v->prefabCategory=v);addRenderableWidget(category);
   button("Enregistrer dans la bibliothèque",x+12,y+130,w-24,()->{if(!prefabName.isBlank()&&!prefabCategory.isBlank())ProjectPackets.request(new ProjectPackets.Action("capture_structure",prefabName,null,0,prefabCategory,false));});
   button("Revenir au jeu",x+12,y+158,half,this::onClose);
   button("Retour aux projets",x+w/2+2,y+158,half,()->{selecting=false;rebuild();});
  }else if(!creating){
   button("+ Créer un nouveau projet",x+12,y+144,w-24,()->{targetCollection=null;creating=true;rebuild();});
   button("<",x+12,y+118,30,()->page=Math.max(0,page-1));
   button(">",x+w-42,y+118,30,()->{var r=ProjectPackets.latest();if(r!=null&&(page+1)*4<r.projects().size())page++;});
   button("Visiter",x+12,y+168,(w-28)/2,()->{var r=ProjectPackets.latest();if(selected==null){notice="Sélectionnez un projet dans la liste";}else if(r==null||r.ready()==null||!r.ready().contains(selected)){notice="Redémarrez le monde ou le serveur pour charger le projet";}else{notice="Connexion au projet...";action("visit");}});
   button("Quitter le projet",x+48,y+118,w-96,()->action("leave"));
   button("Fermer",x+w/2+2,y+168,(w-28)/2,this::onClose);
   button("Sélection",x+12,y+192,(w-28)/2,()->{selecting=true;rebuild();});
   button("Bibliothèque",x+w/2+2,y+192,(w-28)/2,()->{library=true;libraryPage=0;rebuild();action("library");});
   button("Collections de maps",x+12,y+216,(w-28)/2,()->{catalogOpen=true;collection=null;importSource=null;catalogPage=0;rebuild();ProjectPackets.refreshCatalog();});
   button("Construction",x+w/2+2,y+216,(w-28)/2,()->{toolsOpen=true;rebuild();});
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
   button("Créer",x+12,y+158,(w-28)/2,()->{if(!name.isBlank()&&type!=3&&(sizes[size]!=-1||(chosenSize()>=16&&chosenSize()<=60000&&chosenWidth()>=16&&chosenWidth()<=60000))){ProjectPackets.request(new ProjectPackets.Action(targetCollection==null?"create":"create_catalog_project",name,Project.WorldType.values()[type],chosenSize(),targetCollection==null?profiles[profile]:targetCollection,backups,chosenWidth()));creating=false;name="";targetCollection=null;rebuild();}});
   button("Retour",x+w/2+2,y+158,(w-28)/2,()->{creating=false;if(targetCollection!=null){catalogOpen=true;targetCollection=null;}rebuild();});
  }
 }
 @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
 @Override public void render(GuiGraphics g,int mx,int my,float delta){
  g.fill(0,0,width,height,BG);
  int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
  g.fill(x,y,x+w,y+240,PANEL);
  g.drawCenteredString(font,catalogOpen?(collection==null?"COLLECTIONS DE MAPS":font.plainSubstrByWidth(collection,w-24)):library?"BIBLIOTHÈQUE":toolsOpen?"OUTILS DE CONSTRUCTION":selecting?"SÉLECTION DE BÂTIMENT":creating?(targetCollection==null?"NOUVEAU PROJET":font.plainSubstrByWidth("NOUVELLE MAP : "+targetCollection,w-24)):"WORLD ARCHITECT",width/2,y+10,ACCENT);
  if(catalogOpen){
   var rows=catalogRows();var response=ProjectPackets.catalog();
   for(int i=catalogPage*5;i<Math.min(rows.size(),catalogPage*5+5);i++){
    int yy=y+30+(i-catalogPage*5)*22;g.fill(x+12,yy,x+w-12,yy+20,collection!=null&&i<mapEntries().size()&&mapEntries().get(i).name().equals(importSource)?0xff4c6039:BG);
    g.drawString(font,font.plainSubstrByWidth(rows.get(i),w-36),x+18,yy+6,TEXT);
   }
   if(rows.isEmpty())g.drawCenteredString(font,response==null?"Chargement...":!response.success()?response.message():"Dossier vide",width/2,y+70,TEXT);
   if(response!=null)g.drawCenteredString(font,font.plainSubstrByWidth(response.message(),w-24),width/2,y+220,response.success()?ACCENT:0xffdf8b80);
  }else if(library){
   var entries=filtered();
   if(entries.isEmpty())g.drawCenteredString(font,"Aucun bâtiment correspondant",width/2,y+90,TEXT);
   for(int i=libraryPage*4;i<Math.min(entries.size(),libraryPage*4+4);i++){
    var s=entries.get(i);int yy=y+54+(i-libraryPage*4)*25;
    g.fill(x+12,yy,x+w-12,yy+23,s.id().equals(prefabSelected)?0xff4c6039:BG);
    g.drawString(font,font.plainSubstrByWidth(s.name(),w-36),x+18,yy+2,ACCENT);
    g.drawString(font,font.plainSubstrByWidth(s.category()+" | "+s.width()+" x "+s.height()+" x "+s.depth(),w-36),x+18,yy+12,TEXT);
   }
   var response=ProjectPackets.latest();
   g.drawCenteredString(font,response==null?"Chargement...":font.plainSubstrByWidth(response.message(),w-24),width/2,y+182,response!=null&&!response.success()?0xffdf8b80:TEXT);
  }else if(toolsOpen){
   g.drawString(font,"Bloc cible (air = effacer)",x+12,y+78,TEXT);
   g.drawString(font,"Filtre de remplacement",x+12,y+112,TEXT);
   var r=ProjectPackets.latest();
   if(r!=null)g.drawCenteredString(font,font.plainSubstrByWidth(r.message(),w-24),width/2,y+222,r.success()?ACCENT:0xffdf8b80);
  }else if(selecting){
   var r=ProjectPackets.latest();
   if(r!=null)g.drawCenteredString(font,font.plainSubstrByWidth(r.message(),w-24),width/2,y+186,r.success()?ACCENT:0xffdf8b80);
   g.drawCenteredString(font,"Les points utilisent votre position actuelle",width/2,y+200,TEXT);
  }else if(!creating){
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
  if(!catalogOpen&&!creating&&!selecting&&!library&&!toolsOpen&&!notice.isEmpty())g.drawCenteredString(font,font.plainSubstrByWidth(notice,w-24),width/2,y+240,0xffdfc17b);
  super.render(g,mx,my,delta);
 }
 @Override public boolean mouseClicked(double mx,double my,int button){
  if(catalogOpen){
   int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
   if(button==0&&mx>=x+12&&mx<x+w-12&&my>=y+30&&my<y+140){
    int i=catalogPage*5+(int)((my-y-30)/22);var rows=catalogRows();
    if(i<rows.size()){
     if(collection==null){collection=rows.get(i);catalogPage=0;importSource=null;rebuild();return true;}
     var entry=mapEntries().get(i);var format=entry.format();
     if(format==fr.worldarchitect.project.MapCatalog.Format.ZIP||format==fr.worldarchitect.project.MapCatalog.Format.WORLD||format==fr.worldarchitect.project.MapCatalog.Format.REGION||format==fr.worldarchitect.project.MapCatalog.Format.LITEMATIC||format==fr.worldarchitect.project.MapCatalog.Format.NBT||(format==fr.worldarchitect.project.MapCatalog.Format.SCHEM&&entry.name().toLowerCase(java.util.Locale.ROOT).endsWith(".schem"))){importSource=entry.name();importName=entry.name().replaceFirst("(?i)\\.(zip|schem|litematic|nbt)$","");if(importName.equals(entry.name()))importName+="-copie";rebuild();return true;}
    }
   }
   return super.mouseClicked(mx,my,button);
  }
  if(library&&button==0){
   int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
   if(mx>=x+12&&mx<x+w-12&&my>=y+54&&my<y+154){
    int i=libraryPage*4+(int)((my-y-54)/25);
    var entries=filtered();
    if(i<entries.size()){prefabSelected=entries.get(i).id();return true;}
   }
  }
  if(!creating&&!selecting&&!library&&!toolsOpen&&button==0){
   int w=Math.min(340,width-24),x=(width-w)/2,y=height/2-105;
   var r=ProjectPackets.latest();
   if(r!=null&&r.success()&&mx>=x+12&&mx<x+w-12&&my>=y+30&&my<y+114){
    int i=page*4+(int)((my-y-30)/21);
    if(i<r.projects().size()){selected=r.projects().get(i).id();notice="";return true;}
   }
  }
  return super.mouseClicked(mx,my,button);
 }
 @Override public boolean isPauseScreen(){return false;}
}
