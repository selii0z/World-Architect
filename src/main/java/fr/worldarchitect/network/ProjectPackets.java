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
 public record Result(boolean success,String message,java.util.List<Project> projects,java.util.Set<java.util.UUID> ready){}
 private static volatile Result latest;
 public static Result latest(){return latest;}
 @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){
  var r=e.registrar("4");
  r.playToServer(Request.TYPE,Request.CODEC,ProjectPackets::handle);
  r.playToClient(Reply.TYPE,Reply.CODEC,(p,c)->c.enqueueWork(()->latest=GSON.fromJson(p.json(),Result.class)));
 }
 private static void handle(Request packet,IPayloadContext context){
  context.enqueueWork(()->{
   if(!(context.player() instanceof ServerPlayer player)||!player.hasPermissions(2)) return;
   Result result;
   try{
    Action a=GSON.fromJson(packet.json(),Action.class);
    if("create".equals(a.action())) ProjectService.create(player,a.name(),a.worldType(),a.size(),a.width(),a.profile(),a.automaticBackups());
    else if("visit".equals(a.action())) fr.worldarchitect.project.VisitSessions.enter(player,java.util.UUID.fromString(a.name()));
    else if("leave".equals(a.action())) fr.worldarchitect.project.ConstructionSessions.leave(player);
    else if(!"list".equals(a.action())) throw new IllegalArgumentException("Unknown action");
    var projects=ProjectService.list(player);
    var ready=new java.util.HashSet<java.util.UUID>();
    for(var project:projects) if(ProjectService.isReady(player,project.id())) ready.add(project.id());
    result=new Result(true,"OK",projects,ready);
   }catch(Exception ex){result=new Result(false,"Impossible de traiter le projet",java.util.List.of(),java.util.Set.of());}
   PacketDistributor.sendToPlayer(player,new Reply(GSON.toJson(result)));
  });
 }
 public static void request(Action action){PacketDistributor.sendToServer(new Request(GSON.toJson(action)));}
}
