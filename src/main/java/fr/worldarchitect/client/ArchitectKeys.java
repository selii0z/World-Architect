package fr.worldarchitect.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;
public final class ArchitectKeys {
 static final KeyMapping OPEN = new KeyMapping("key.worldarchitect.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.worldarchitect");
 static final KeyMapping POINT_A = new KeyMapping("World Architect : point A",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.worldarchitect");
 static final KeyMapping POINT_B = new KeyMapping("World Architect : point B",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.worldarchitect");
 static final KeyMapping CLEAR = new KeyMapping("World Architect : effacer la sélection",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_UNKNOWN,"key.categories.worldarchitect");
 @EventBusSubscriber(modid="worldarchitect", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
 public static class Registration {
  @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(OPEN); e.register(POINT_A); e.register(POINT_B); e.register(CLEAR); }
 }
 @EventBusSubscriber(modid="worldarchitect", value=Dist.CLIENT)
 public static class Input {
  private static void send(String action){fr.worldarchitect.network.ProjectPackets.request(new fr.worldarchitect.network.ProjectPackets.Action(action,null,null,0,null,false));}
  @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
   var client=Minecraft.getInstance();
   if(client.player!=null&&client.screen==null&&client.level!=null&&client.level.dimension().location().getNamespace().equals("worldarchitect")){
    while(POINT_A.consumeClick())send("select_first");
    while(POINT_B.consumeClick())send("select_second");
    while(CLEAR.consumeClick())send("select_clear");
   }else{
    while(POINT_A.consumeClick()){}
    while(POINT_B.consumeClick()){}
    while(CLEAR.consumeClick()){}
   }
   while (OPEN.consumeClick()) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null && mc.screen == null) mc.setScreen(new ArchitectScreen());
   }
  }
 }
}
