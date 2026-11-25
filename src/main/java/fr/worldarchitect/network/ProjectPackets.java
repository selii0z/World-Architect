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
 public record Action(String action,String name,Project.WorldType worldType,int size,String profile,boolean automaticBackups,int width){public Action(String action,String name,Project.WorldType worldType,int size,String profile,boolean automaticBackups){this(action,name,worldType,size,profile,automaticBackups,size);}}
 public record PreviewBounds(String dimension,int minX,int minY,int minZ,int maxX,int maxY,int maxZ,boolean valid){}
 public record Result(boolean success,String message,java.util.List<Project> projects,java.util.Set<java.util.UUID> ready,java.util.List<fr.worldarchitect.project.StructureLibrary.Metadata> structures,PreviewBounds preview){public Result(boolean success,String message,java.util.List<Project> projects,java.util.Set<java.util.UUID> ready){this(success,message,projects,ready,java.util.List.of(),null);}}
 private static final fr.worldarchitect.project.StructureSelection SELECTIONS=new fr.worldarchitect.project.StructureSelection();
 private static volatile Action activePreview;
 public static void manipulatePreview(int rotationDelta,boolean flip){
  var a=activePreview;if(a==null)return;
  var rotations=net.minecraft.world.level.block.Rotation.values();
  int index=net.minecraft.world.level.block.Rotation.valueOf(a.profile()).ordinal();
  request(new Action("preview_structure",a.name(),null,0,rotations[Math.floorMod(index+rotationDelta,rotations.length)].name(),a.automaticBackups()^flip));
 }
 public static void cancelPreview(){activePreview=null;latest=null;request(new Action("library",null,null,0,null,false));}
 private static volatile Result latest;
 public static Result latest(){return latest;}
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){
  var r=e.registrar("7");
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
     preview=new PreviewBounds(player.serverLevel().dimension().location().toString(),plan.min().getX(),plan.min().getY(),plan.min().getZ(),plan.max().getX(),plan.max().getY(),plan.max().getZ(),valid);
     message=(valid?"Aperçu valide : ":"Placement interdit : ")+(plan.max().getX()-plan.min().getX()+1)+" x "+(plan.max().getY()-plan.min().getY()+1)+" x "+(plan.max().getZ()-plan.min().getZ()+1);
     structures=fr.worldarchitect.project.StructureLibrary.list(player.serverLevel());
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
 public static void request(Action action){if("preview_structure".equals(action.action()))activePreview=action;else if("leave".equals(action.action()))activePreview=null;PacketDistributor.sendToServer(new Request(GSON.toJson(action)));}
}
