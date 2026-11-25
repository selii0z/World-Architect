package fr.worldarchitect.client;

import fr.worldarchitect.network.ProjectPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid="worldarchitect",value=Dist.CLIENT)
public final class PreviewRenderer {
 private PreviewRenderer(){}
 @SubscribeEvent public static void render(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
  var mc=Minecraft.getInstance();
  var result=ProjectPackets.latest();
  if(mc.level==null||result==null||!result.success()||result.preview()==null)return;
  var p=result.preview();
  if(!mc.level.dimension().location().toString().equals(p.dimension()))return;
  var camera=event.getCamera().getPosition();
  var box=new AABB(p.minX(),p.minY(),p.minZ(),p.maxX()+1.0,p.maxY()+1.0,p.maxZ()+1.0).move(-camera.x,-camera.y,-camera.z);
  var buffers=mc.renderBuffers().bufferSource();
  LevelRenderer.renderLineBox(event.getPoseStack(),buffers.getBuffer(RenderType.lines()),box,0.35f,1f,0.35f,0.9f);
  buffers.endBatch(RenderType.lines());
 }
}
