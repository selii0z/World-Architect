package fr.worldarchitect.api;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import java.util.UUID;

@EventBusSubscriber(modid="worldarchitect")
public final class InstanceCommands {
 @SubscribeEvent public static void register(RegisterCommandsEvent event) {
  event.getDispatcher().register(Commands.literal("wainstance").requires(s->s.hasPermission(2))
   .then(Commands.literal("list").executes(c->{
    var s=c.getSource();
    s.sendSuccess(()->Component.literal(InstanceServices.get(s.getServer()).states().toString()),false);
    return 1;
   }))
   .then(Commands.literal("create").then(Commands.argument("collection",StringArgumentType.string())
    .then(Commands.argument("map",StringArgumentType.string()).executes(c->{
     var s=c.getSource();var server=s.getServer();
     WorldArchitectApi.createInstance(server,StringArgumentType.getString(c,"collection"),StringArgumentType.getString(c,"map"))
      .whenComplete((handle,error)->server.execute(()->{
       if(error!=null)s.sendFailure(Component.literal("Instance creation failed: "+error.getMessage()));
       else s.sendSuccess(()->Component.literal("Instance "+handle.id()+" ready: "+handle.level().dimension().location()),false);
      }));
     return 1;
    }))))
   .then(Commands.literal("enter").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
    var s=c.getSource();var server=s.getServer();var player=s.getPlayerOrException();UUID id;
    try{id=UUID.fromString(StringArgumentType.getString(c,"id"));}
    catch(IllegalArgumentException ex){s.sendFailure(Component.literal("Invalid instance UUID"));return 0;}
    if(InstanceServices.get(server).state(id).orElse(null)!=LiveInstance.State.ACTIVE){
     s.sendFailure(Component.literal("Instance is not active"));return 0;
    }
    var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
     net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("worldarchitect","instance_"+id.toString().replace("-","")));
    var level=server.getLevel(key);
    if(level==null){s.sendFailure(Component.literal("Instance level missing"));return 0;}
    var pos=level.getSharedSpawnPos();
    int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
    player.teleportTo(level,pos.getX()+0.5,y,pos.getZ()+0.5,player.getYRot(),player.getXRot());
    return 1;
   })))
   .then(Commands.literal("leave").executes(c->{
    var s=c.getSource();var player=s.getPlayerOrException();
    var key=player.serverLevel().dimension().location();
    if(!key.getNamespace().equals("worldarchitect")||!key.getPath().startsWith("instance_")){
     s.sendFailure(Component.literal("Not in an instance"));return 0;
    }
    var level=s.getServer().overworld();var pos=level.getSharedSpawnPos();
    int y=level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
    player.teleportTo(level,pos.getX()+0.5,y,pos.getZ()+0.5,player.getYRot(),player.getXRot());
    return 1;
   }))
   .then(Commands.literal("destroy").then(Commands.argument("id",StringArgumentType.word()).executes(c->{
    var s=c.getSource();var server=s.getServer();UUID id;
    try{id=UUID.fromString(StringArgumentType.getString(c,"id"));}
    catch(IllegalArgumentException ex){s.sendFailure(Component.literal("Invalid instance UUID"));return 0;}
    WorldArchitectApi.destroyInstance(server,id).whenComplete((deleted,error)->server.execute(()->{
     if(error!=null)s.sendFailure(Component.literal("Instance cleanup failed: "+error.getMessage()));
     else s.sendSuccess(()->Component.literal("Instance deleted: "+deleted),false);
    }));
    return 1;
   }))));
 }
}
