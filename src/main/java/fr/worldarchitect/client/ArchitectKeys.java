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
 @EventBusSubscriber(modid="worldarchitect", value=Dist.CLIENT, bus=EventBusSubscriber.Bus.MOD)
 public static class Registration {
  @SubscribeEvent public static void register(RegisterKeyMappingsEvent e) { e.register(OPEN); }
 }
 @EventBusSubscriber(modid="worldarchitect", value=Dist.CLIENT)
 public static class Input {
  @SubscribeEvent public static void tick(ClientTickEvent.Post e) {
   while (OPEN.consumeClick()) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player != null && mc.screen == null) mc.setScreen(new ArchitectScreen());
   }
  }
 }
}
