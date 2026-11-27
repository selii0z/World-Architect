package fr.worldarchitect.network;

import fr.worldarchitect.project.Project;
import fr.worldarchitect.project.ProjectService;
import com.google.gson.Gson;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="worldarchitect", bus=EventBusSubscriber.Bus.MOD)
public final class ProjectPackets {
 private static final Gson GSON = new Gson();
 public record Request(String json) implements CustomPacketPayload {
  public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","project_request"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC = StreamCodec.of((b,p)->b.writeUtf(p.json(),2048),b->new Request(b.readUtf(2048)));
  public Type<Request> type(){return TYPE;}
 }
 public record Reply(String json) implements CustomPacketPayload {
  public static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","project_reply"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Reply> CODEC = StreamCodec.of((b,p)->b.writeUtf(p.json(),262144),b->new Reply(b.readUtf(262144)));
  public Type<Reply> type(){return TYPE;}
 }
 public record GhostChunk(String structure,int index,int total,java.util.List<fr.worldarchitect.project.StructureLibrary.PreviewBlock> blocks){}
 public record GhostReply(String json) implements CustomPacketPayload {
  public static final Type<GhostReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","ghost_reply"));
  public static final StreamCodec<RegistryFriendlyByteBuf,GhostReply> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json(),262144),b->new GhostReply(b.readUtf(262144)));
  public Type<GhostReply> type(){return TYPE;}
 }
 private static final java.util.Map<Integer,java.util.List<fr.worldarchitect.project.StructureLibrary.PreviewBlock>> ghostParts=new java.util.HashMap<>();
 private static String ghostId;
 private static java.util.List<fr.worldarchitect.project.StructureLibrary.PreviewBlock> ghostBlocks=java.util.List.of();
 public static java.util.List<fr.worldarchitect.project.StructureLibrary.PreviewBlock> ghostBlocks(String structure){return activePreview!=null&&structure.equals(activePreview.name())&&structure.equals(ghostId)?ghostBlocks:java.util.List.of();}
 private static void acceptGhost(GhostChunk chunk){
  if(activePreview==null||!chunk.structure().equals(activePreview.name())||chunk.total()<1||chunk.total()>256||chunk.index()<0||chunk.index()>=chunk.total())return;
  if(!chunk.structure().equals(ghostId)){ghostId=chunk.structure();ghostParts.clear();ghostBlocks=java.util.List.of();}
  ghostParts.put(chunk.index(),chunk.blocks());
  if(ghostParts.size()==chunk.total()){
   var combined=new java.util.ArrayList<fr.worldarchitect.project.StructureLibrary.PreviewBlock>();
   for(int i=0;i<chunk.total();i++)combined.addAll(ghostParts.get(i));
   ghostBlocks=java.util.List.copyOf(combined);ghostParts.clear();
  }
 }
 public record Action(String action,String name,Project.WorldType worldType,int size,String profile,boolean automaticBackups,int width){public Action(String action,String name,Project.WorldType worldType,int size,String profile,boolean automaticBackups){this(action,name,worldType,size,profile,automaticBackups,size);}}
 public record PreviewBounds(String dimension,int minX,int minY,int minZ,int maxX,int maxY,int maxZ,boolean valid,String structure,int anchorX,int anchorY,int anchorZ,String rotation,boolean mirrored){}
 public record Result(boolean success,String message,java.util.List<Project> projects,java.util.Set<java.util.UUID> ready,java.util.List<fr.worldarchitect.project.StructureLibrary.Metadata> structures,PreviewBounds preview){public Result(boolean success,String message,java.util.List<Project> projects,java.util.Set<java.util.UUID> ready){this(success,message,projects,ready,java.util.List.of(),null);}}
 private static final fr.worldarchitect.project.StructureSelection SELECTIONS=new fr.worldarchitect.project.StructureSelection();
 private static volatile Action activePreview;
 public static boolean hasActivePreview(){return activePreview!=null;}
 public static void resetClientPreview(){activePreview=null;latest=null;markers=null;aerial=null;ghostId=null;ghostParts.clear();ghostBlocks=java.util.List.of();}
 public static void manipulatePreview(int rotationDelta,boolean flip){
  var a=activePreview;if(a==null)return;
  var rotations=net.minecraft.world.level.block.Rotation.values();
  int index=net.minecraft.world.level.block.Rotation.valueOf(a.profile()).ordinal();
  request(new Action("preview_structure",a.name(),null,0,rotations[Math.floorMod(index+rotationDelta,rotations.length)].name(),a.automaticBackups()^flip));
 }
 public static void confirmPreview(){
  var a=activePreview;var r=latest;
  if(a==null||r==null||r.preview()==null||!r.preview().valid())return;
  var p=r.preview();
  if(!a.name().equals(p.structure())||!a.profile().equals(p.rotation())||a.automaticBackups()!=p.mirrored())return;
  var target=p.anchorX()+","+p.anchorY()+","+p.anchorZ()+","+p.rotation();
  request(new Action("place_structure",p.structure(),null,0,target,p.mirrored()));
 }
 public static void cancelPreview(){resetClientPreview();request(new Action("library",null,null,0,null,false));}
 public record BackupResult(boolean success,String collection,String map,String message,java.util.List<String> archives){}
 public record BackupReply(String json) implements CustomPacketPayload {
  public static final Type<BackupReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","backup_reply"));
  public static final StreamCodec<RegistryFriendlyByteBuf,BackupReply> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json(),262144),b->new BackupReply(b.readUtf(262144)));
  public Type<BackupReply> type(){return TYPE;}
 }
 private static volatile BackupResult backups;
 public static BackupResult backups(){return backups;}
 public static void refreshBackups(String collection,String map){backups=null;request(new Action("backup_list",collection,null,0,map,false));}
 public record CatalogResult(boolean success,String message,java.util.List<fr.worldarchitect.project.MapCatalog.Collection> collections){}
 public record CatalogReply(String json) implements CustomPacketPayload {
  public static final Type<CatalogReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","catalog_reply"));
  public static final StreamCodec<RegistryFriendlyByteBuf,CatalogReply> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json(),262144),b->new CatalogReply(b.readUtf(262144)));
  public Type<CatalogReply> type(){return TYPE;}
 }
 public record OverviewImage(int index,int total,byte[] png) implements CustomPacketPayload {
  public static final Type<OverviewImage> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","overview_image"));
  public static final StreamCodec<RegistryFriendlyByteBuf,OverviewImage> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.index());b.writeVarInt(p.total());b.writeByteArray(p.png());},b->new OverviewImage(b.readVarInt(),b.readVarInt(),b.readByteArray(131072)));
  public Type<OverviewImage> type(){return TYPE;}
 }
 private static volatile byte[] overviewImage;
 public static byte[] overviewImage(){return overviewImage;}
 private static java.io.ByteArrayOutputStream overviewBuffer;
 private static int overviewNext,overviewTotal;
 public static void clearOverviewImage(){overviewImage=null;overviewBuffer=null;overviewNext=0;}
 private static void acceptOverview(OverviewImage p){
  if(p.total()<1||p.total()>40||p.index()<0||p.index()>=p.total()){clearOverviewImage();overviewImage=new byte[0];return;}
  if(p.index()==0){overviewBuffer=new java.io.ByteArrayOutputStream();overviewNext=0;overviewTotal=p.total();}
  if(overviewBuffer==null||p.index()!=overviewNext||p.total()!=overviewTotal){clearOverviewImage();overviewImage=new byte[0];return;}
  overviewBuffer.writeBytes(p.png());overviewNext++;
  if(overviewNext==overviewTotal){overviewImage=overviewBuffer.toByteArray();overviewBuffer=null;}
 }
 private static final fr.worldarchitect.project.OverviewJob OVERVIEW=new fr.worldarchitect.project.OverviewJob();
 private static final java.util.concurrent.atomic.AtomicBoolean IMPORTING=new java.util.concurrent.atomic.AtomicBoolean();
 private static volatile CatalogResult catalog;
 public static CatalogResult catalog(){return catalog;}
 public static void refreshCatalog(){catalog=null;request(new Action("catalog",null,null,0,null,false));}
 public record MarkerResult(boolean success,String message,String dimension,java.util.List<fr.worldarchitect.project.ProjectMarkers.Marker> markers){}
 public record MarkerReply(String json) implements CustomPacketPayload {
  public static final Type<MarkerReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","marker_reply"));
  public static final StreamCodec<RegistryFriendlyByteBuf,MarkerReply> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json(),262144),b->new MarkerReply(b.readUtf(262144)));
  public Type<MarkerReply> type(){return TYPE;}
 }
 private static volatile MarkerResult markers;
 public static MarkerResult markers(){return markers;}
 public static void refreshMarkers(){markers=null;request(new Action("marker_list",null,null,0,null,false));}
 public record AerialResult(String message,fr.worldarchitect.project.AerialMap.Tile tile){}
 public record AerialReply(String json) implements CustomPacketPayload {
  public static final Type<AerialReply> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("worldarchitect","aerial_reply"));
  public static final StreamCodec<RegistryFriendlyByteBuf,AerialReply> CODEC=StreamCodec.of((b,p)->b.writeUtf(p.json(),262144),b->new AerialReply(b.readUtf(262144)));
  public Type<AerialReply> type(){return TYPE;}
 }
 private static volatile AerialResult aerial;
 public static AerialResult aerial(){return aerial;}
 public static void requestAerial(int x,int z,int step){aerial=null;request(new Action("aerial",x+","+z,null,step,null,false));}
 private static final java.util.Map<ServerPlayer,Long> AERIAL_LIMIT=new java.util.WeakHashMap<>();
 private static volatile Result latest;
 public static Result latest(){return latest;}
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){
  var r=e.registrar("15");
  r.playToClient(AerialReply.TYPE,AerialReply.CODEC,(p,c)->c.enqueueWork(()->aerial=GSON.fromJson(p.json(),AerialResult.class)));
  r.playToClient(MarkerReply.TYPE,MarkerReply.CODEC,(p,c)->c.enqueueWork(()->markers=GSON.fromJson(p.json(),MarkerResult.class)));
  r.playToClient(BackupReply.TYPE,BackupReply.CODEC,(p,c)->c.enqueueWork(()->backups=GSON.fromJson(p.json(),BackupResult.class)));
  r.playToClient(OverviewImage.TYPE,OverviewImage.CODEC,(p,c)->c.enqueueWork(()->acceptOverview(p)));
  r.playToClient(CatalogReply.TYPE,CatalogReply.CODEC,(p,c)->c.enqueueWork(()->catalog=GSON.fromJson(p.json(),CatalogResult.class)));
  r.playToClient(GhostReply.TYPE,GhostReply.CODEC,(p,c)->c.enqueueWork(()->acceptGhost(GSON.fromJson(p.json(),GhostChunk.class))));
  r.playToServer(Request.TYPE,Request.CODEC,ProjectPackets::handle);
  r.playToClient(Reply.TYPE,Reply.CODEC,(p,c)->c.enqueueWork(()->latest=GSON.fromJson(p.json(),Result.class)));
 }
 private static void handle(Request packet,IPayloadContext context){
  context.enqueueWork(()->{
   if(!(context.player() instanceof ServerPlayer player)) return;
   if(!player.hasPermissions(2)) {
    PacketDistributor.sendToPlayer(player,new Reply(GSON.toJson(new Result(false,"Accès réservé aux administrateurs",java.util.List.of(),java.util.Set.of()))));
    return;
   }
   Result result;
   try{
    Action a=GSON.fromJson(packet.json(),Action.class);
    String message="OK";
    PreviewBounds preview=null;
    java.util.List<fr.worldarchitect.project.StructureLibrary.Metadata> structures=java.util.List.of();
    if("aerial".equals(a.action())){
     AerialResult response;
     try{
      long now=System.nanoTime();Long last=AERIAL_LIMIT.get(player);
      if(last!=null&&now-last<500000000L)throw new IllegalStateException("Rate limited");
      AERIAL_LIMIT.put(player,now);
      String[] center=a.name().split(",",-1);
      if(center.length!=2)throw new IllegalArgumentException("Invalid center");
      response=new AerialResult("Carte actualisée",fr.worldarchitect.project.AerialMap.sample(player,Integer.parseInt(center[0]),Integer.parseInt(center[1]),a.size()));
     }catch(Exception ex){response=new AerialResult("Carte indisponible ou requête trop rapide",null);}
     PacketDistributor.sendToPlayer(player,new AerialReply(GSON.toJson(response)));return;
    }
    if("marker_list".equals(a.action())||"marker_add".equals(a.action())||"marker_move".equals(a.action())||"marker_update".equals(a.action())||"marker_surface".equals(a.action())||"marker_remove".equals(a.action())||"marker_teleport".equals(a.action())||"surface_teleport".equals(a.action())){
     MarkerResult response;
     String dimension=player.serverLevel().dimension().location().toString();
     try{
      String status="Marqueurs actualisés";
      if("marker_move".equals(a.action())){var coords=a.profile().split(",",-1);if(coords.length!=2)throw new IllegalArgumentException("Invalid coordinates");fr.worldarchitect.project.ProjectMarkers.moveSurface(player,java.util.UUID.fromString(a.name()),Integer.parseInt(coords[0]),Integer.parseInt(coords[1]));status="Marqueur déplacé";}
      if("marker_update".equals(a.action())){var options=a.profile().split(",",-1);if(options.length!=3)throw new IllegalArgumentException("Invalid marker options");fr.worldarchitect.project.ProjectMarkers.update(player,java.util.UUID.fromString(options[0]),a.name(),fr.worldarchitect.project.ProjectMarkers.Category.valueOf(options[1]),fr.worldarchitect.project.ProjectMarkers.Color.valueOf(options[2]));status="Marqueur modifié";}
      if("marker_surface".equals(a.action())){var options=a.profile().split(",",-1);if(options.length!=4)throw new IllegalArgumentException("Invalid marker coordinates");fr.worldarchitect.project.ProjectMarkers.addSurface(player,a.name(),fr.worldarchitect.project.ProjectMarkers.Category.valueOf(options[0]),fr.worldarchitect.project.ProjectMarkers.Color.valueOf(options[1]),Integer.parseInt(options[2]),Integer.parseInt(options[3]));status="Marqueur ajouté";}
      if("marker_add".equals(a.action())){var options=a.profile()==null?new String[]{"GENERAL","WHITE"}:a.profile().split(",",-1);if(options.length!=2)throw new IllegalArgumentException("Invalid marker options");fr.worldarchitect.project.ProjectMarkers.add(player,a.name(),fr.worldarchitect.project.ProjectMarkers.Category.valueOf(options[0]),fr.worldarchitect.project.ProjectMarkers.Color.valueOf(options[1]));status="Marqueur ajouté";}
      if("marker_remove".equals(a.action()))status=fr.worldarchitect.project.ProjectMarkers.remove(player,java.util.UUID.fromString(a.name()))?"Marqueur supprimé":"Marqueur introuvable";
      if("surface_teleport".equals(a.action())){var coords=a.profile().split(",",-1);if(coords.length!=2)throw new IllegalArgumentException("Invalid coordinates");fr.worldarchitect.project.ProjectMarkers.teleportSurface(player,Integer.parseInt(coords[0]),Integer.parseInt(coords[1]));status="Téléportation effectuée";}
      if("marker_teleport".equals(a.action())){fr.worldarchitect.project.ProjectMarkers.teleport(player,java.util.UUID.fromString(a.name()));status="Téléportation effectuée";}
      response=new MarkerResult(true,status,dimension,fr.worldarchitect.project.ProjectMarkers.list(player));
     }catch(Exception ex){response=new MarkerResult(false,"Action impossible dans ce projet",dimension,java.util.List.of());}
     PacketDistributor.sendToPlayer(player,new MarkerReply(GSON.toJson(response)));return;
    }
    if("import_schematic".equals(a.action())){
     CatalogResult response;
     try{
      String[] names=a.profile().split("\\|",-1);
      if(names.length!=2)throw new IllegalArgumentException("Invalid schematic request");
      var source=fr.worldarchitect.project.MapCatalog.resolveSchematic(a.name(),names[0]);
      fr.worldarchitect.project.StructureLibrary.importSchematic(player.serverLevel(),source,names[1],a.name());
      response=new CatalogResult(true,"Structure ajoutée à la bibliothèque",fr.worldarchitect.project.MapCatalog.scan());
      if(GSON.toJson(response).length()>250000)response=new CatalogResult(true,"Structure importée ; catalogue trop volumineux",java.util.List.of());
     }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Schematic import failed",ex);response=new CatalogResult(false,"Échec de l’opération de terrain de la structure",java.util.List.of());}
     PacketDistributor.sendToPlayer(player,new CatalogReply(GSON.toJson(response)));return;
    }
    if("overview_image".equals(a.action())||"project_overview_image".equals(a.action())){
     var server=player.getServer();var id=player.getUUID();
     java.nio.file.Path activeFile=null;
     if("project_overview_image".equals(a.action())){
      var project=fr.worldarchitect.project.ProjectService.forLevel(player.serverLevel()).orElseThrow();
      var location=net.minecraft.resources.ResourceLocation.parse(project.dimensionId());
      activeFile=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("dimensions").resolve(location.getNamespace()).resolve(location.getPath()).resolve("overview.png");
     }
     var projectFile=activeFile;
     java.util.concurrent.CompletableFuture.runAsync(()->{
      byte[] data=new byte[0];
      try{
       var file=projectFile!=null?projectFile:fr.worldarchitect.project.MapCatalog.resolveTerrain(a.name(),a.profile()).resolve("overview.png");
       if(java.nio.file.Files.isRegularFile(file,java.nio.file.LinkOption.NOFOLLOW_LINKS))try(var in=java.nio.file.Files.newInputStream(file)){var bytes=in.readNBytes(5242881);if(bytes.length<=5242880)data=bytes;}
      }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Overview image read failed",ex);}
      var bytes=data;server.execute(()->{var online=server.getPlayerList().getPlayer(id);if(online!=null&&online.hasPermissions(2)){int total=Math.max(1,(bytes.length+131071)/131072);for(int i=0;i<total;i++)PacketDistributor.sendToPlayer(online,new OverviewImage(i,total,java.util.Arrays.copyOfRange(bytes,i*131072,Math.min(bytes.length,(i+1)*131072))));}});
     });return;
    }
    if("overview_start".equals(a.action())||"overview_status".equals(a.action())||"overview_cancel".equals(a.action())){
     CatalogResult response;
     try{
      if("overview_start".equals(a.action())){
       var options=a.profile().split("\\|",-1);
       if(options.length!=3)throw new IllegalArgumentException("Invalid overview request");
       var terrain=fr.worldarchitect.project.MapCatalog.resolveTerrain(a.name(),options[0]);
       OVERVIEW.start(terrain,terrain.resolve("overview.png"),Integer.parseInt(options[1]),Integer.parseInt(options[2]),a.size(),a.width());
      }else if("overview_cancel".equals(a.action()))OVERVIEW.cancel();
      var status=OVERVIEW.status();
      response=new CatalogResult(!"failed".equals(status.phase()),"Carte : "+status.phase()+" "+status.done()+"/"+status.total(),java.util.List.of());
     }catch(Exception ex){response=new CatalogResult(false,"Impossible de générer la carte",java.util.List.of());}
     PacketDistributor.sendToPlayer(player,new CatalogReply(GSON.toJson(response)));return;
    }
    if("backup_list".equals(a.action())){
     var server=player.getServer();var playerId=player.getUUID();
     java.util.concurrent.CompletableFuture.runAsync(()->{
      BackupResult response;
      try{
       response=new BackupResult(true,a.name(),a.profile(),"Sauvegardes actualisées",fr.worldarchitect.project.CatalogBackups.list(a.name(),a.profile()));
       if(GSON.toJson(response).length()>250000)throw new IllegalStateException("Backup list too large");
      }catch(Exception ex){response=new BackupResult(false,a.name(),a.profile(),"Impossible de lire les sauvegardes",java.util.List.of());}
      var reply=response;
      server.execute(()->{var online=server.getPlayerList().getPlayer(playerId);if(online!=null)PacketDistributor.sendToPlayer(online,new BackupReply(GSON.toJson(reply)));});
     });return;
    }
    if("import_map".equals(a.action())||"duplicate_map".equals(a.action())||"export_map".equals(a.action())||"backup_map".equals(a.action())||"restore_map".equals(a.action())){
     if(!IMPORTING.compareAndSet(false,true)){
      PacketDistributor.sendToPlayer(player,new CatalogReply(GSON.toJson(new CatalogResult(false,"Une opération de terrain est déjà en cours",java.util.List.of()))));return;
     }
     var server=player.getServer();var playerId=player.getUUID();
     PacketDistributor.sendToPlayer(player,new CatalogReply(GSON.toJson(new CatalogResult(true,"Opération de terrain en cours...",java.util.List.of()))));
     java.util.concurrent.CompletableFuture.runAsync(()->{
      CatalogResult response;
      try{
       String[] names=a.profile().split("\\|",-1);
       if("backup_map".equals(a.action())){
        if(names.length!=2)throw new IllegalArgumentException("Invalid backup request");
        fr.worldarchitect.project.CatalogBackups.create(a.name(),names[0],names[1]);
       }else if("restore_map".equals(a.action())){
        if(names.length!=3)throw new IllegalArgumentException("Invalid restore request");
        fr.worldarchitect.project.CatalogBackups.restore(a.name(),names[0],names[1],names[2]);
       }else if("export_map".equals(a.action())){
        if(names.length!=1)throw new IllegalArgumentException("Invalid export request");
        fr.worldarchitect.project.MapExport.export(a.name(),names[0]);
       }else if("duplicate_map".equals(a.action())){
        if(names.length!=3)throw new IllegalArgumentException("Invalid duplication request");
        fr.worldarchitect.project.MapCatalog.duplicateTerrain(a.name(),names[0],names[1],names[2]);
       }else{
        if(names.length!=2)throw new IllegalArgumentException("Invalid import request");
        fr.worldarchitect.project.MapCatalog.importSource(a.name(),names[0],names[1]);
       }
       response=new CatalogResult(true,"backup_map".equals(a.action())?"Sauvegarde terminée":"restore_map".equals(a.action())?"Restauration terminée":"export_map".equals(a.action())?"Export ZIP terminé":"duplicate_map".equals(a.action())?"Duplication terminée":"Importation terminée",fr.worldarchitect.project.MapCatalog.scan());
       if(GSON.toJson(response).length()>250000)response=new CatalogResult(false,"Import terminé ; catalogue trop volumineux",java.util.List.of());
      }catch(Exception ex){com.mojang.logging.LogUtils.getLogger().warn("Map import failed",ex);response=new CatalogResult(false,"Échec de l’opération de terrain",java.util.List.of());}
      finally{IMPORTING.set(false);}
      var reply=response;
      server.execute(()->{var online=server.getPlayerList().getPlayer(playerId);if(online!=null)PacketDistributor.sendToPlayer(online,new CatalogReply(GSON.toJson(reply)));});
     });return;
    }
    if("catalog".equals(a.action())||"create_collection".equals(a.action())){
     CatalogResult response;
     try{
      if("create_collection".equals(a.action()))fr.worldarchitect.project.MapCatalog.createCollection(a.name());
      var collections=fr.worldarchitect.project.MapCatalog.scan();
      response=new CatalogResult(true,"Catalogue actualisé",collections);
      if(GSON.toJson(response).length()>250000)throw new IllegalStateException("Catalogue trop volumineux");
     }catch(Exception ex){response=new CatalogResult(false,"Impossible de lire ou créer la collection",java.util.List.of());}
     PacketDistributor.sendToPlayer(player,new CatalogReply(GSON.toJson(response)));return;
    }
    if("preview_structure".equals(a.action())){
     var rotation=net.minecraft.world.level.block.Rotation.valueOf(a.profile());
     var mirror=a.automaticBackups()?net.minecraft.world.level.block.Mirror.FRONT_BACK:net.minecraft.world.level.block.Mirror.NONE;
     var hit=player.pick(32.0,1.0f,false);
     var anchor=hit instanceof net.minecraft.world.phys.BlockHitResult block && hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK
      ?block.getBlockPos().relative(block.getDirection()):player.blockPosition();
     var plan=fr.worldarchitect.project.StructurePlacement.previewGeometry(player.serverLevel(),java.util.UUID.fromString(a.name()),anchor,rotation,mirror);
     boolean valid=true;
     try{fr.worldarchitect.project.StructurePlacement.validateBounds(player.serverLevel(),plan);}catch(IllegalArgumentException ex){valid=false;}
     preview=new PreviewBounds(player.serverLevel().dimension().location().toString(),plan.min().getX(),plan.min().getY(),plan.min().getZ(),plan.max().getX(),plan.max().getY(),plan.max().getZ(),valid,a.name(),anchor.getX(),anchor.getY(),anchor.getZ(),rotation.name(),a.automaticBackups());
     message=(valid?"Aperçu valide : ":"Placement interdit : ")+(plan.max().getX()-plan.min().getX()+1)+" x "+(plan.max().getY()-plan.min().getY()+1)+" x "+(plan.max().getZ()-plan.min().getZ()+1);
     structures=fr.worldarchitect.project.StructureLibrary.list(player.serverLevel());
    }
    else if("place_structure".equals(a.action())){
     fr.worldarchitect.project.ConstructionSessions.requireEditor(player);
     var parts=a.profile().split(",",-1);
     if(parts.length!=4)throw new IllegalArgumentException("Invalid placement");
     var anchor=new net.minecraft.core.BlockPos(Integer.parseInt(parts[0]),Integer.parseInt(parts[1]),Integer.parseInt(parts[2]));
     if(anchor.distToCenterSqr(player.getX(),player.getY(),player.getZ())>36*36)throw new IllegalArgumentException("Placement too far");
     var rotation=net.minecraft.world.level.block.Rotation.valueOf(parts[3]);
     var mirror=a.automaticBackups()?net.minecraft.world.level.block.Mirror.FRONT_BACK:net.minecraft.world.level.block.Mirror.NONE;
     var plan=fr.worldarchitect.project.StructurePlacement.preview(player.serverLevel(),java.util.UUID.fromString(a.name()),anchor,rotation,mirror);
     fr.worldarchitect.project.ProjectEdits.place(player,plan,false);
     message="Bâtiment placé";
    }
    else if("block_edit".equals(a.action())){
     var selection=SELECTIONS.get(player.getUUID());
     if(selection==null||!selection.complete())throw new IllegalArgumentException("Selection incomplete");
     var shape=fr.worldarchitect.project.EditGeometry.Shape.valueOf(a.name());
     var registry=net.minecraft.core.registries.BuiltInRegistries.BLOCK;
     String[] parts=a.profile().split("\\|",-1);
     if(parts.length!=2)throw new IllegalArgumentException("Invalid block edit request");
     var targetId=net.minecraft.resources.ResourceLocation.parse(parts[0]);
     if(!registry.containsKey(targetId))throw new IllegalArgumentException("Unknown target block");
     var target=registry.get(targetId).defaultBlockState();
     net.minecraft.world.level.block.Block filter=null;
     if(!parts[1].isBlank()){
      var filterId=net.minecraft.resources.ResourceLocation.parse(parts[1]);
      if(!registry.containsKey(filterId))throw new IllegalArgumentException("Unknown filter block");
      filter=registry.get(filterId);
     }
     fr.worldarchitect.project.BlockEditScheduler.submit(player,selection,shape,target,filter);
     message="Opération de construction demandée";
    }
    else if("block_undo".equals(a.action())||"block_redo".equals(a.action())){
     message=fr.worldarchitect.project.BlockEditScheduler.replay(player,"block_undo".equals(a.action()))?"Opération demandée":"Aucune modification disponible";
    }
    else if("block_recover".equals(a.action())){
     message=fr.worldarchitect.project.BlockEditScheduler.recover(player)?"Restauration demandée ; journal conservé":"Aucune construction interrompue";
    }
    else if("block_cancel".equals(a.action())){
     message=fr.worldarchitect.project.BlockEditScheduler.cancel(player)?"Annulation demandée":"Aucune opération active";
    }
    else if("undo_edit".equals(a.action())){
     message=fr.worldarchitect.project.ProjectEdits.undo(player)?"Modification annulée":"Aucune modification à annuler";
    }
    else if("redo_edit".equals(a.action())){
     message=fr.worldarchitect.project.ProjectEdits.redo(player)?"Modification rétablie":"Aucune modification à rétablir";
    }
    else if("ghost_geometry".equals(a.action())){
     ProjectService.forLevel(player.serverLevel()).orElseThrow(()->new IllegalStateException("Not a project dimension"));
     var blocks=fr.worldarchitect.project.StructureLibrary.previewBlocks(player.serverLevel(),java.util.UUID.fromString(a.name()));
     int total=Math.max(1,(blocks.size()+1023)/1024);
     for(int i=0;i<total;i++)PacketDistributor.sendToPlayer(player,new GhostReply(GSON.toJson(new GhostChunk(a.name(),i,total,blocks.subList(i*1024,Math.min(blocks.size(),(i+1)*1024))))));
     return;
    }
    else if("library".equals(a.action())){
     structures=fr.worldarchitect.project.StructureLibrary.list(player.serverLevel());
     message=structures.size()+" bâtiments enregistrés";
    }
    else if("select_first".equals(a.action())||"select_second".equals(a.action())){
     var selection=SELECTIONS.set(player,player.blockPosition(),"select_first".equals(a.action()));
     message=selection.complete()?"Sélection complète : "+selection.volume()+" blocs":"Point de sélection enregistré";
    }
    else if("capture_structure".equals(a.action())){
     var selection=SELECTIONS.get(player.getUUID());
     if(selection==null||!selection.complete())throw new IllegalArgumentException("Selection incomplete");
     fr.worldarchitect.project.StructureLibrary.capture(player.serverLevel(),selection,a.name(),a.profile());
     message="Bâtiment enregistré dans la bibliothèque";
    }
    else if("select_clear".equals(a.action())){
     SELECTIONS.clear(player.getUUID());
     message="Sélection effacée";
    }
    else if("activate_map".equals(a.action())) {
     if(!IMPORTING.compareAndSet(false,true))throw new IllegalStateException("Terrain operation in progress");
     var server=player.getServer();var playerId=player.getUUID();
     try{
      ProjectService.activateImported(player,a.profile(),a.name()).whenComplete((project,error)->{
       IMPORTING.set(false);
       server.execute(()->{
        var online=server.getPlayerList().getPlayer(playerId);
        if(error!=null)com.mojang.logging.LogUtils.getLogger().warn("Map activation failed",error);
        if(online!=null&&online.hasPermissions(2)){
         var projects=ProjectService.list(online);
         var ready=new java.util.HashSet<java.util.UUID>();
         for(var p:projects)if(ProjectService.isReady(online,p.id()))ready.add(p.id());
         PacketDistributor.sendToPlayer(online,new Reply(GSON.toJson(new Result(error==null,error==null?(fr.worldarchitect.api.RuntimeDimensionBackend.enabled(server)?"Map préparée ; chargement à chaud demandé":"Map préparée ; redémarrage nécessaire"):"Échec de l’activation",projects,ready))));
        }
       });
      });
     }catch(Exception ex){IMPORTING.set(false);throw ex;}
     return;
    }
    else if("create_catalog_project".equals(a.action())) {
     var folder=fr.worldarchitect.project.MapCatalog.reserveMap(a.profile(),a.name());
     var project=ProjectService.create(player,a.name(),a.worldType(),a.size(),a.width(),"worldarchitect:generic",a.automaticBackups());
     fr.worldarchitect.project.MapCatalog.linkProject(folder,project);
     message=fr.worldarchitect.api.RuntimeDimensionBackend.enabled(player.getServer())?"Projet créé ; chargement à chaud demandé":"Projet créé dans la collection ; redémarrage nécessaire";
    }
    else if("create".equals(a.action())) {
      ProjectService.create(player,a.name(),a.worldType(),a.size(),a.width(),a.profile(),a.automaticBackups());
      message=fr.worldarchitect.api.RuntimeDimensionBackend.enabled(player.getServer())?"Projet enregistré ; chargement à chaud demandé":"Projet enregistré ; chargement après redémarrage";
     }
     else if("save_project".equals(a.action())) {
      var project=ProjectService.requireProject(player,java.util.UUID.fromString(a.name()));
      var level=fr.worldarchitect.project.ProjectWorlds.resolve(player,project).orElseThrow(()->new IllegalStateException("Dimension not loaded"));
      fr.worldarchitect.project.ProjectAutoSaves.saveNow(level,project);
      message="Terrain enregistré (sans archive ZIP)";
     }
    else if("enter_construction".equals(a.action())) fr.worldarchitect.project.ConstructionSessions.enter(player,java.util.UUID.fromString(a.name()));
    else if("visit".equals(a.action())) fr.worldarchitect.project.VisitSessions.enter(player,java.util.UUID.fromString(a.name()));
    else if("leave".equals(a.action())) {fr.worldarchitect.project.ConstructionSessions.leave(player);SELECTIONS.clear(player.getUUID());}
    else if(!"list".equals(a.action())) throw new IllegalArgumentException("Unknown action");
    var projects=ProjectService.list(player);
    var ready=new java.util.HashSet<java.util.UUID>();
    for(var project:projects) if(ProjectService.isReady(player,project.id())) ready.add(project.id());
    result=new Result(true,message,projects,ready,structures,preview);
   }catch(Exception ex){
    com.mojang.logging.LogUtils.getLogger().warn("World Architect request failed for {}",player.getUUID(),ex);
    java.util.List<Project> projects=java.util.List.of();
    var ready=new java.util.HashSet<java.util.UUID>();
    try{projects=ProjectService.list(player);for(var project:projects)if(ProjectService.isReady(player,project.id()))ready.add(project.id());}
    catch(RuntimeException ignored){}
    result=new Result(false,"Impossible de traiter le projet",projects,ready);
   }
   PacketDistributor.sendToPlayer(player,new Reply(GSON.toJson(result)));
  });
 }
 public static void request(Action action){if("preview_structure".equals(action.action())){
  boolean changed=activePreview==null||!action.name().equals(activePreview.name());
  activePreview=action;
  if(changed){ghostId=null;ghostParts.clear();ghostBlocks=java.util.List.of();PacketDistributor.sendToServer(new Request(GSON.toJson(new Action("ghost_geometry",action.name(),null,0,null,false))));}
 }else if("leave".equals(action.action()))activePreview=null;PacketDistributor.sendToServer(new Request(GSON.toJson(action)));}
}
