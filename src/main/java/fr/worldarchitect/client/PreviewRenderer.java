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
  LevelRenderer.renderLineBox(event.getPoseStack(),buffers.getBuffer(RenderType.lines()),box,p.valid()?0.35f:1f,p.valid()?1f:0.2f,0.35f,0.9f);
  buffers.endBatch(RenderType.lines());
  var blocks=ProjectPackets.ghostBlocks(p.structure());
  // Limit immediate model rendering; large prefabs retain their complete boundary.
  if(blocks.size()>4096)return;
  var rotation=net.minecraft.world.level.block.Rotation.valueOf(p.rotation());
  var mirror=p.mirrored()?net.minecraft.world.level.block.Mirror.FRONT_BACK:net.minecraft.world.level.block.Mirror.NONE;
  var anchor=new net.minecraft.core.BlockPos(p.anchorX(),p.anchorY(),p.anchorZ());
  var pose=event.getPoseStack();
  for(var block:blocks){
   var pos=net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.transform(block.offset(),mirror,rotation,net.minecraft.core.BlockPos.ZERO).offset(anchor);
   if(pos.distToCenterSqr(camera.x,camera.y,camera.z)>96*96)continue;
   var state=net.minecraft.world.level.block.Block.stateById(block.stateId()).mirror(mirror).rotate(rotation);
   pose.pushPose();
   try{
    pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z);
    mc.getBlockRenderer().renderSingleBlock(state,pose,buffers,0xF000F0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
   }finally{pose.popPose();}
  }
  buffers.endBatch();
 }
}
