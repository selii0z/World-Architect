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
 public static void resetClientPreview(){activePreview=null;latest=null;ghostId=null;ghostParts.clear();ghostBlocks=java.util.List.of();}
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
 private static volatile Result latest;
 public static Result latest(){return latest;}
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){
  var r=e.registrar("9");
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
    else if("create".equals(a.action())) ProjectService.create(player,a.name(),a.worldType(),a.size(),a.width(),a.profile(),a.automaticBackups());
    else if("visit".equals(a.action())) fr.worldarchitect.project.VisitSessions.enter(player,java.util.UUID.fromString(a.name()));
    else if("leave".equals(a.action())) {fr.worldarchitect.project.ConstructionSessions.leave(player);SELECTIONS.clear(player.getUUID());}
    else if(!"list".equals(a.action())) throw new IllegalArgumentException("Unknown action");
    var projects=ProjectService.list(player);
    var ready=new java.util.HashSet<java.util.UUID>();
    for(var project:projects) if(ProjectService.isReady(player,project.id())) ready.add(project.id());
    result=new Result(true,message,projects,ready,structures,preview);
   }catch(Exception ex){
    com.mojang.logging.LogUtils.getLogger().warn("World Architect request failed for {}",player.getUUID(),ex);
    result=new Result(false,"Impossible de traiter le projet",java.util.List.of(),java.util.Set.of());
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
