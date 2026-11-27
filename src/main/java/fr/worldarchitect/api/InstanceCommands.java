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
